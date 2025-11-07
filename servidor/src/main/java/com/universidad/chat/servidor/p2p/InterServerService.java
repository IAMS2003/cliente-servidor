package com.universidad.chat.servidor.p2p;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.universidad.chat.servidor.model.ServidorDAO;

/**
 * Servicio de comunicación entre servidores (P2P ligero por TCP con JSON por línea).
 * Mantiene estado de pares y permite difundir eventos básicos (presencia de usuarios locales).
 */
public class InterServerService {
    private static final Logger logger = LoggerFactory.getLogger(InterServerService.class);

    private final String localHost;
    private final int localServerPort; // puerto del servidor principal (clientes)
    private final int localP2pPort;    // puerto P2P
    private final List<String> configPeers;  // host:puerto p2p (semilla de config)
    private final Gson gson = new Gson();

    private ServerSocket listener;
    private volatile boolean running = false;
    private ExecutorService pool = Executors.newCachedThreadPool();
    private ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    // Estado de pares
    private final Map<String, PeerStatus> peerStatus = new ConcurrentHashMap<>();
    // Presencia de usuarios remotos por servidor key(host:p2pPort) -> (idUsuario -> nombre)
    private final Map<String, Map<Integer, String>> remoteUsers = new ConcurrentHashMap<>();
    // Metadatos del cliente remoto (IP/puerto del socket del cliente) por servidor
    private static class ClientInfo { String ip; Integer port; ClientInfo(String ip, Integer port){ this.ip = ip; this.port = port; } }
    private final Map<String, Map<Integer, ClientInfo>> remoteUserClients = new ConcurrentHashMap<>();
    // Puerto del servidor de clientes de cada peer key(host:p2pPort) -> serverPort
    private final Map<String, Integer> peerServerPorts = new ConcurrentHashMap<>();
    // Cache temporal para respuestas de historial
    private final Map<String, Map<String, Object>> historyCache = new ConcurrentHashMap<>();

    // Persistencia de pares
    private final ServidorDAO servidorDAO = new ServidorDAO();

    // Handler hacia el servidor principal para entregar mensajes reenviados
    public interface Handler {
        void onDirectMessageFromPeer(String peerHost, int peerPort, int idEmisor, String nombreEmisor, int idReceptor, String contenido, Long idMensajeServidorOrigen);
        void onDirectAudioFromPeer(String peerHost, int peerPort, int idEmisor, String nombreEmisor, int idReceptor, String audioBase64, String transcripcion, Long idMensajeServidorOrigen);
        List<Map<String, Object>> onRequestUserHistory(int idUsuario);
        List<Map<String, Object>> onRequestUserChannels(int idUsuario);
    }
    private Handler handler;

    // Proveedor de usuarios locales conectados
    public interface LocalConnectedUsersProvider {
        java.util.List<LocalUserInfo> getLocalConnectedUsers();
    }
    public static class LocalUserInfo {
        public final int idUsuario;
        public final String nombreUsuario;
        public final String clientIp;
        public final Integer clientPort;
        public LocalUserInfo(int id, String nombre, String clientIp, Integer clientPort) {
            this.idUsuario = id; this.nombreUsuario = nombre; this.clientIp = clientIp; this.clientPort = clientPort;
        }
    }
    private LocalConnectedUsersProvider localConnectedUsersProvider;

    // Handler para eventos de estado de servidores P2P
    public interface ServerStatusHandler {
        void onServerConnected(String host, int p2pPort, int serverPort);
        void onServerOnline(String host, int p2pPort, int serverPort);
        void onServerOffline(String host, int p2pPort);
        void onServerRegistered(String host, int p2pPort);
    }
    private ServerStatusHandler serverStatusHandler;

    // Handler para cambios de presencia de usuarios remotos
    public interface RemotePresenceHandler {
        void onRemotePresenceChanged(String host, int p2pPort, int idUsuario, String nombreUsuario, String estado);
    }
    private RemotePresenceHandler remotePresenceHandler;

    public InterServerService(String localHost, int localServerPort, int localP2pPort, List<String> peers) {
        this.localHost = localHost;
        this.localServerPort = localServerPort;
        this.localP2pPort = localP2pPort;
        this.configPeers = peers != null ? peers : List.of();
        for (String p : this.configPeers) {
            String[] hp = p.split(":");
            if (hp.length == 2) {
                String h = hp[0]; int prt = parseIntSafe(hp[1], -1);
                if (prt > 0) peerStatus.put(key(h, prt), new PeerStatus(h, prt, null, "UNKNOWN"));
            }
        }
    }

    private String key(String host, int port) { return host + ":" + port; }
    private int parseIntSafe(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

    private List<String> dbPeers = new ArrayList<>();

    public void start() throws IOException {
        if (running) return;
        running = true;
        listener = new ServerSocket(localP2pPort);
        logger.info("P2P escuchando en {}:{}", localHost, localP2pPort);
        pool.execute(() -> {
            while (running) {
                try {
                    Socket s = listener.accept();
                    pool.execute(() -> handleIncoming(s));
                } catch (IOException e) {
                    if (running) logger.error("Error aceptando conexión P2P", e);
                }
            }
        });

        // Cargar pares persistidos en BD (bootstrap) y lanzar HELLO
        try {
            this.dbPeers = servidorDAO.listarPeersHostPort();
            logger.info("P2P bootstrap desde BD: {} pares", dbPeers.size());
        } catch (Exception e) {
            logger.warn("No se pudo cargar pares desde BD: {}", e.getMessage());
        }
        pool.execute(this::sendHelloAll);
        // Enviar heartbeats periódicos para mantener estados ONLINE frescos
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try { sendHeartbeatAll(); } catch (Exception ignored) {}
        }, 15, 20, TimeUnit.SECONDS);
        // Marcar OFFLINE a pares sin actividad reciente
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try { sweepPeersAging(); } catch (Exception ignored) {}
        }, 45, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        try { if (listener != null) listener.close(); } catch (Exception ignored) {}
    try { pool.shutdownNow(); } catch (Exception ignored) {}
    try { heartbeatScheduler.shutdownNow(); } catch (Exception ignored) {}
    }

    private void handleIncoming(Socket socket) {
        String rip = socket.getInetAddress().getHostAddress();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    JsonObject obj = gson.fromJson(line, JsonObject.class);
                    String tipo = obj.has("tipo") ? obj.get("tipo").getAsString() : "";
                    if (Objects.equals(tipo, "HELLO") || Objects.equals(tipo, "HEARTBEAT")) {
                        String host = obj.get("serverHost").getAsString();
                        int p2pPort = obj.get("p2pPort").getAsInt();
                        int serverPort = obj.has("serverPort") ? obj.get("serverPort").getAsInt() : -1;
                        String k = key(host, p2pPort);
                        
                        // Verificar estado previo
                        PeerStatus prevStatus = peerStatus.get(k);
                        String prevEstado = prevStatus != null ? prevStatus.getEstado() : null;
                        boolean estabaNOK = "OFFLINE".equals(prevEstado) || "UNKNOWN".equals(prevEstado);
                        
                        peerStatus.put(k, new PeerStatus(host, p2pPort, LocalDateTime.now(), "ONLINE"));
                        if (serverPort > 0) peerServerPorts.put(k, serverPort);
                        try { servidorDAO.upsert(host, p2pPort, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
                        logger.info("P2P RX {} desde {}:{} (socket {}) - prevEstado: {}", tipo, host, p2pPort, rip, prevEstado);
                        
                        // Notificar cambio de estado: siempre 'conectado' al recibir HELLO
                        if (serverStatusHandler != null) {
                            if (Objects.equals(tipo, "HELLO")) {
                                serverStatusHandler.onServerConnected(host, p2pPort, serverPort);
                            } else if (estabaNOK) {
                                // HEARTBEAT tras OFFLINE/UNKNOWN -> ONLINE
                                serverStatusHandler.onServerOnline(host, p2pPort, serverPort);
                            }
                        }
                        
                        // Responder ACK con datos del estado completo
                        JsonObject ack = new JsonObject();
                        ack.addProperty("tipo", "ACK");
                        ack.addProperty("ts", System.currentTimeMillis());
                        // Exponer también el puerto de servidor de clientes de este nodo para que el peer lo aprenda
                        ack.addProperty("serverPort", localServerPort);
                        
                        // Enviar listado de usuarios conectados (locales + remotos)
                        if (handler != null) {
                            com.google.gson.JsonArray usuarios = new com.google.gson.JsonArray();
                            // Usuarios locales
                            if (localConnectedUsersProvider != null) {
                                for (var u : localConnectedUsersProvider.getLocalConnectedUsers()) {
                                    com.google.gson.JsonObject ju = new com.google.gson.JsonObject();
                                    ju.addProperty("id", u.idUsuario);
                                    ju.addProperty("nombre", u.nombreUsuario);
                                    ju.addProperty("servidorHost", localHost);
                                    ju.addProperty("servidorP2pPort", localP2pPort);
                                    ju.addProperty("servidorPort", localServerPort);
                                    if (u.clientIp != null) ju.addProperty("clientIp", u.clientIp);
                                    if (u.clientPort != null) ju.addProperty("clientPort", u.clientPort);
                                    ju.addProperty("local", true);
                                    usuarios.add(ju);
                                }
                            }
                            // Usuarios remotos conocidos
                            for (Map.Entry<String, Map<Integer, String>> e : remoteUsers.entrySet()) {
                                String[] hp = e.getKey().split(":");
                                if (hp.length!=2) continue;
                                String rh=hp[0]; int rp=parseIntSafe(hp[1],-1);
                                for (Map.Entry<Integer, String> ue : e.getValue().entrySet()) {
                                    com.google.gson.JsonObject ju = new com.google.gson.JsonObject();
                                    ju.addProperty("id", ue.getKey());
                                    ju.addProperty("nombre", ue.getValue());
                                    ju.addProperty("servidorHost", rh);
                                    ju.addProperty("servidorP2pPort", rp);
                                    // incluir si conocemos el puerto del servidor de clientes del peer remoto
                                    Integer sp = peerServerPorts.getOrDefault(key(rh, rp), null);
                                    if (sp != null && sp > 0) ju.addProperty("servidorPort", sp);
                                    ju.addProperty("local", false);
                                    usuarios.add(ju);
                                }
                            }
                            ack.add("usuarios", usuarios);
                        }
                        
                        // Enviar listado de servidores conocidos
                        com.google.gson.JsonArray servidores = new com.google.gson.JsonArray();
                        for (Map.Entry<String, PeerStatus> e : peerStatus.entrySet()) {
                            PeerStatus ps = e.getValue();
                            com.google.gson.JsonObject js = new com.google.gson.JsonObject();
                            js.addProperty("host", ps.getHost());
                            js.addProperty("port", ps.getPort());
                            js.addProperty("estado", ps.getEstado());
                            if (ps.getLastSeen() != null) js.addProperty("lastSeen", ps.getLastSeen().toString());
                            servidores.add(js);
                        }
                        ack.add("servidores", servidores);
                        
                        pw.println(ack.toString());
                    } else if (Objects.equals(tipo, "ACK")) {
                        // Procesar ACK enriquecido con datos de usuarios y servidores
                        // El ACK puede incluir serverPort, pero en esta rama (conexión entrante) ya lo
                        // registramos cuando procesamos el HELLO/HEARTBEAT inicial.
                        if (obj.has("usuarios")) {
                            try {
                                com.google.gson.JsonArray usuarios = obj.getAsJsonArray("usuarios");
                                // Determinar de qué servidor viene este ACK (debería estar en contexto)
                                // Por simplicidad, asumimos que viene del peer que acabamos de contactar
                                // Necesitamos trackear esto mejor, pero por ahora procesamos usuarios remotos
                                for (com.google.gson.JsonElement e : usuarios) {
                                    com.google.gson.JsonObject ju = e.getAsJsonObject();
                                    boolean esLocal = ju.has("local") && ju.get("local").getAsBoolean();
                                    if (!esLocal && ju.has("servidorHost")) {
                                        String rh = ju.get("servidorHost").getAsString();
                                        int rp = ju.get("servidorP2pPort").getAsInt();
                                        if (ju.has("servidorPort") && !ju.get("servidorPort").isJsonNull()) {
                                            int sp = ju.get("servidorPort").getAsInt();
                                            peerServerPorts.put(key(rh, rp), sp);
                                        }
                                        int id = ju.get("id").getAsInt();
                                        String nombre = ju.get("nombre").getAsString();
                                        String k = key(rh, rp);
                                        remoteUsers.computeIfAbsent(k, kk -> new ConcurrentHashMap<>()).put(id, nombre);
                                    }
                                }
                                logger.info("P2P ACK: procesados {} usuarios", usuarios.size());
                            } catch (Exception ex) {
                                logger.warn("Error procesando usuarios en ACK: {}", ex.getMessage());
                            }
                        }
                        if (obj.has("servidores")) {
                            try {
                                com.google.gson.JsonArray servidores = obj.getAsJsonArray("servidores");
                                for (com.google.gson.JsonElement e : servidores) {
                                    com.google.gson.JsonObject js = e.getAsJsonObject();
                                    String h = js.get("host").getAsString();
                                    int p = js.get("port").getAsInt();
                                    String estado = js.get("estado").getAsString();
                                    // Evitar agregarnos a nosotros mismos si un peer nos devuelve nuestra propia entrada
                                    if (p == localP2pPort && (h.equals(localHost) || "localhost".equalsIgnoreCase(h) || "127.0.0.1".equals(h))) {
                                        continue;
                                    }
                                    String k = key(h, p);
                                    // No sobrescribir si ya lo conocemos, pero registrar si es nuevo
                                    if (!peerStatus.containsKey(k)) {
                                        peerStatus.put(k, new PeerStatus(h, p, null, estado));
                                        try { servidorDAO.upsert(h, p, estado, null); } catch (Exception ignored) {}
                                        logger.info("P2P ACK: servidor descubierto {}:{} ({})", h, p, estado);
                                        // Notificar registro de nuevo servidor
                                        if (serverStatusHandler != null) {
                                            serverStatusHandler.onServerRegistered(h, p);
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                logger.warn("Error procesando servidores en ACK: {}", ex.getMessage());
                            }
                        }
                    } else if (Objects.equals(tipo, "USER_PRESENCE")) {
                        String host = obj.get("serverHost").getAsString();
                        int p2pPort = obj.get("p2pPort").getAsInt();
                        int serverPort = obj.has("serverPort") ? obj.get("serverPort").getAsInt() : -1;
                        String estado = obj.get("estado").getAsString();
                        String usuario = obj.get("usuario").getAsString();
                        int idUsuario = obj.get("idUsuario").getAsInt();
                        String cip = obj.has("clientIp") && !obj.get("clientIp").isJsonNull() ? obj.get("clientIp").getAsString() : null;
                        Integer cport = obj.has("clientPort") && !obj.get("clientPort").isJsonNull() ? obj.get("clientPort").getAsInt() : null;
                        String k = key(host, p2pPort);
                        peerStatus.compute(k, (kk, ps) -> {
                            PeerStatus n = ps != null ? ps : new PeerStatus(host, p2pPort, null, "UNKNOWN");
                            n.setLastSeen(LocalDateTime.now());
                            n.setEstado("ONLINE");
                            return n;
                        });
                        if (serverPort > 0) peerServerPorts.put(k, serverPort);
                        try { servidorDAO.upsert(host, p2pPort, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
                        remoteUsers.computeIfAbsent(k, kk -> new ConcurrentHashMap<>());
                        if (Objects.equals(estado, "CONNECTED")) {
                            remoteUsers.get(k).put(idUsuario, usuario);
                            if (cip != null || cport != null) {
                                remoteUserClients.computeIfAbsent(k, kk -> new ConcurrentHashMap<>()).put(idUsuario, new ClientInfo(cip, cport));
                            }
                            // Notificar cambio de presencia a servidor principal
                            if (remotePresenceHandler != null) {
                                remotePresenceHandler.onRemotePresenceChanged(host, p2pPort, idUsuario, usuario, estado);
                            }
                        } else if (Objects.equals(estado, "DISCONNECTED")) {
                            remoteUsers.get(k).remove(idUsuario);
                            Map<Integer, ClientInfo> m = remoteUserClients.get(k);
                            if (m != null) m.remove(idUsuario);
                            if (remotePresenceHandler != null) {
                                remotePresenceHandler.onRemotePresenceChanged(host, p2pPort, idUsuario, usuario, estado);
                            }
                        }
                        logger.info("P2P RX USER_PRESENCE [{}] user={} id={} de {}:{}", estado, usuario, idUsuario, host, p2pPort);
                    } else if (Objects.equals(tipo, "FORWARD_DM")) {
                        String host = obj.get("serverHost").getAsString();
                        int p2pPort = obj.get("p2pPort").getAsInt();
                        int idEmisor = obj.get("idEmisor").getAsInt();
                        String nombreEmisor = obj.get("nombreEmisor").getAsString();
                        int idReceptor = obj.get("idReceptor").getAsInt();
                        String contenido = obj.get("contenido").getAsString();
                        Long idMensajeOrigen = obj.has("idMensaje") && !obj.get("idMensaje").isJsonNull() ? obj.get("idMensaje").getAsLong() : null;
                        logger.info("P2P RX FORWARD_DM {}:{} emisor={}({}) receptor={} contenido='{}'", host, p2pPort, idEmisor, nombreEmisor, idReceptor, contenido);
                        if (handler != null) handler.onDirectMessageFromPeer(host, p2pPort, idEmisor, nombreEmisor, idReceptor, contenido, idMensajeOrigen);
                    } else if (Objects.equals(tipo, "FORWARD_AUDIO")) {
                        String host = obj.get("serverHost").getAsString();
                        int p2pPort = obj.get("p2pPort").getAsInt();
                        int idEmisor = obj.get("idEmisor").getAsInt();
                        String nombreEmisor = obj.get("nombreEmisor").getAsString();
                        int idReceptor = obj.get("idReceptor").getAsInt();
                        String audioBase64 = obj.get("audioData").getAsString();
                        String transcripcion = obj.has("transcripcion") && !obj.get("transcripcion").isJsonNull() ? obj.get("transcripcion").getAsString() : "";
                        Long idMensajeOrigen = obj.has("idMensaje") && !obj.get("idMensaje").isJsonNull() ? obj.get("idMensaje").getAsLong() : null;
                        logger.info("P2P RX FORWARD_AUDIO {}:{} emisor={}({}) receptor={} bytes={} trans='{}'", host, p2pPort, idEmisor, nombreEmisor, idReceptor, audioBase64!=null?audioBase64.length():0, transcripcion);
                        if (handler != null) handler.onDirectAudioFromPeer(host, p2pPort, idEmisor, nombreEmisor, idReceptor, audioBase64, transcripcion, idMensajeOrigen);
                    } else if (Objects.equals(tipo, "SERVER_OFFLINE")) {
                        String host = obj.get("serverHost").getAsString();
                        int p2pPort = obj.get("p2pPort").getAsInt();
                        String k = key(host, p2pPort);
                        peerStatus.put(k, new PeerStatus(host, p2pPort, LocalDateTime.now(), "OFFLINE"));
                        remoteUsers.remove(k);
                        try { servidorDAO.upsert(host, p2pPort, "OFFLINE", LocalDateTime.now()); } catch (Exception ignored) {}
                        logger.info("P2P RX SERVER_OFFLINE de {}:{}", host, p2pPort);
                        // Notificar que servidor se desconectó
                        if (serverStatusHandler != null) {
                            serverStatusHandler.onServerOffline(host, p2pPort);
                        }
                    } else if (Objects.equals(tipo, "REQUEST_HISTORY")) {
                        // Solicitud de historial de usuario
                        String reqHost = obj.get("serverHost").getAsString();
                        int reqP2pPort = obj.get("p2pPort").getAsInt();
                        int idUsuario = obj.get("idUsuario").getAsInt();
                        logger.info("P2P RX REQUEST_HISTORY de {}:{} para usuario {}", reqHost, reqP2pPort, idUsuario);
                        
                        // Consultar historial y responder
                        List<Map<String, Object>> mensajes = handler != null ? handler.onRequestUserHistory(idUsuario) : List.of();
                        List<Map<String, Object>> canales = handler != null ? handler.onRequestUserChannels(idUsuario) : List.of();
                        
                        // Responder con el historial
                        JsonObject response = new JsonObject();
                        response.addProperty("tipo", "RESPONSE_HISTORY");
                        response.addProperty("serverHost", localHost);
                        response.addProperty("p2pPort", localP2pPort);
                        response.addProperty("idUsuario", idUsuario);
                        response.add("mensajes", gson.toJsonTree(mensajes));
                        response.add("canales", gson.toJsonTree(canales));
                        pw.println(response.toString());
                        logger.info("P2P TX RESPONSE_HISTORY -> {}:{} mensajes={} canales={}", reqHost, reqP2pPort, mensajes.size(), canales.size());
                    } else if (Objects.equals(tipo, "RESPONSE_HISTORY")) {
                        // Respuesta de historial recibida
                        String respHost = obj.get("serverHost").getAsString();
                        int respP2pPort = obj.get("p2pPort").getAsInt();
                        int idUsuario = obj.get("idUsuario").getAsInt();
                        com.google.gson.JsonArray mensajesJson = obj.has("mensajes") ? obj.getAsJsonArray("mensajes") : new com.google.gson.JsonArray();
                        com.google.gson.JsonArray canalesJson = obj.has("canales") ? obj.getAsJsonArray("canales") : new com.google.gson.JsonArray();
                        
                        logger.info("P2P RX RESPONSE_HISTORY de {}:{} para usuario {} con {} mensajes y {} canales", 
                            respHost, respP2pPort, idUsuario, mensajesJson.size(), canalesJson.size());
                        
                        // Almacenar respuesta en cache temporal (será procesado por quien hizo la solicitud)
                        String cacheKey = "history_" + idUsuario + "_" + respHost + ":" + respP2pPort;
                        Map<String, Object> historyData = new ConcurrentHashMap<>();
                        historyData.put("mensajes", mensajesJson);
                        historyData.put("canales", canalesJson);
                        historyData.put("timestamp", System.currentTimeMillis());
                        historyCache.put(cacheKey, historyData);
                    } else {
                        logger.info("P2P RX desconocido: {}", line);
                    }
                } catch (Exception ex) {
                    logger.warn("P2P mensaje inválido desde {}: {}", rip, ex.getMessage());
                }
            }
        } catch (IOException e) {
            logger.debug("Conexión P2P cerrada desde {}", rip);
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private List<String> allPeersOnce() {
        java.util.Set<String> set = new java.util.HashSet<>();
        set.addAll(configPeers);
        set.addAll(dbPeers);
        // incluir pares aprendidos (pudieron llegar por conexiones entrantes)
        for (String k : peerStatus.keySet()) set.add(k);
        return new java.util.ArrayList<>(set);
    }

    private void sendHelloAll() {
        for (String peer : allPeersOnce()) {
            String[] hp = peer.split(":");
            if (hp.length != 2) continue;
            String host = hp[0]; int port = parseIntSafe(hp[1], -1);
            if (port <= 0) continue;
            try (Socket s = new Socket(host, port);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                 BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                JsonObject hello = new JsonObject();
                hello.addProperty("tipo", "HELLO");
                hello.addProperty("serverHost", localHost);
                hello.addProperty("serverPort", localServerPort);
                hello.addProperty("p2pPort", localP2pPort);
                hello.addProperty("ts", System.currentTimeMillis());
                pw.println(hello.toString());
                String ack = br.readLine();
                logger.info("P2P TX HELLO -> {}:{} | ACK={} ", host, port, ack != null ? ack.substring(0, Math.min(100, ack.length())) : "null");
                
                // Procesar ACK enriquecido
                if (ack != null) {
                    try {
                        JsonObject ackObj = gson.fromJson(ack, JsonObject.class);
                        if (ackObj.has("serverPort")) {
                            try { int sp = ackObj.get("serverPort").getAsInt(); peerServerPorts.put(key(host, port), sp); } catch (Exception ignored) {}
                        }
                        if (ackObj.has("usuarios")) {
                            com.google.gson.JsonArray usuarios = ackObj.getAsJsonArray("usuarios");
                            for (com.google.gson.JsonElement e : usuarios) {
                                com.google.gson.JsonObject ju = e.getAsJsonObject();
                                boolean esLocal = ju.has("local") && ju.get("local").getAsBoolean();
                                if (esLocal) {
                                    // Usuario del servidor remoto
                                    int id = ju.get("id").getAsInt();
                                    String nombre = ju.get("nombre").getAsString();
                                    String k = key(host, port);
                                    remoteUsers.computeIfAbsent(k, kk -> new ConcurrentHashMap<>()).put(id, nombre);
                                } else if (ju.has("servidorHost")) {
                                    // Usuario de otro servidor (transitivo)
                                    String rh = ju.get("servidorHost").getAsString();
                                    int rp = ju.get("servidorP2pPort").getAsInt();
                                    if (ju.has("servidorPort") && !ju.get("servidorPort").isJsonNull()) {
                                        int sp = ju.get("servidorPort").getAsInt();
                                        peerServerPorts.put(key(rh, rp), sp);
                                    }
                                    int id = ju.get("id").getAsInt();
                                    String nombre = ju.get("nombre").getAsString();
                                    String k = key(rh, rp);
                                    remoteUsers.computeIfAbsent(k, kk -> new ConcurrentHashMap<>()).put(id, nombre);
                                }
                            }
                        }
                        if (ackObj.has("servidores")) {
                            com.google.gson.JsonArray servidores = ackObj.getAsJsonArray("servidores");
                            for (com.google.gson.JsonElement e : servidores) {
                                com.google.gson.JsonObject js = e.getAsJsonObject();
                                String h = js.get("host").getAsString();
                                int p = js.get("port").getAsInt();
                                String estado = js.get("estado").getAsString();
                                // Evitar auto-registro de nosotros mismos
                                if (p == localP2pPort && (h.equals(localHost) || "localhost".equalsIgnoreCase(h) || "127.0.0.1".equals(h))) {
                                    continue;
                                }
                                String k = key(h, p);
                                if (!peerStatus.containsKey(k)) {
                                    peerStatus.put(k, new PeerStatus(h, p, null, estado));
                                    try { servidorDAO.upsert(h, p, estado, null); } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Error procesando ACK de HELLO: {}", ex.getMessage());
                    }
                }
                
                // Actualizar estado como ONLINE tras HELLO exitoso
                
                peerStatus.put(key(host, port), new PeerStatus(host, port, LocalDateTime.now(), "ONLINE"));
                try { servidorDAO.upsert(host, port, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
                
                // Notificar conexión exitosa: siempre 'conectado' en HELLO emitido
                if (serverStatusHandler != null) {
                    int sp = peerServerPorts.getOrDefault(key(host, port), -1);
                    serverStatusHandler.onServerConnected(host, port, sp);
                }
            } catch (Exception e) {
                logger.warn("P2P HELLO fallo -> {}:{} ({})", host, port, e.getMessage());
                
                // Marcar como OFFLINE si falla
                PeerStatus prevStatus = peerStatus.get(key(host, port));
                boolean estabaOnline = prevStatus != null && "ONLINE".equals(prevStatus.getEstado());
                
                peerStatus.put(key(host, port), new PeerStatus(host, port, null, "OFFLINE"));
                try { servidorDAO.upsert(host, port, "OFFLINE", null); } catch (Exception ignored) {}
                
                // Notificar desconexión si estaba online
                if (serverStatusHandler != null && estabaOnline) {
                    serverStatusHandler.onServerOffline(host, port);
                }
            }
        }
    }

    private void sendHeartbeatAll() {
        for (String peer : allPeersOnce()) {
            String[] hp = peer.split(":"); if (hp.length!=2) continue;
            String host = hp[0]; int port = parseIntSafe(hp[1], -1); if (port<=0) continue;
            try (Socket s = new Socket(host, port); PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {
                JsonObject hb = new JsonObject();
                hb.addProperty("tipo", "HEARTBEAT");
                hb.addProperty("serverHost", localHost);
                hb.addProperty("serverPort", localServerPort);
                hb.addProperty("p2pPort", localP2pPort);
                hb.addProperty("ts", System.currentTimeMillis());
                pw.println(hb.toString());
                peerStatus.compute(key(host, port), (k, ps) -> {
                    PeerStatus n = ps != null ? ps : new PeerStatus(host, port, null, "UNKNOWN");
                    n.setLastSeen(LocalDateTime.now()); n.setEstado("ONLINE"); return n;
                });
                try { servidorDAO.upsert(host, port, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
            } catch (Exception e) {
                logger.debug("Heartbeat fallo -> {}:{} ({})", host, port, e.getMessage());
            }
        }
    }

    /**
     * Solicitar manualmente el envío de HELLO a todos los pares conocidos.
     */
    public void announceOnline() {
        pool.execute(this::sendHelloAll);
    }

    /** Registrar/actualizar un peer manualmente y reflejarlo en el estado interno y BD. */
    public void registerPeer(String host, int port) {
        if (host == null || host.isBlank() || port <= 0) return;
        String k = key(host, port);
        peerStatus.put(k, new PeerStatus(host, port, null, "UNKNOWN"));
        try { servidorDAO.upsert(host, port, "UNKNOWN", null); } catch (Exception ignored) {}
    }

    /** Enviar HELLO a un peer específico. */
    public void announceTo(String host, int port) {
        if (host == null || host.isBlank() || port <= 0) return;
        pool.execute(() -> {
            try (Socket s = new Socket(host, port);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                 BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                JsonObject hello = new JsonObject();
                hello.addProperty("tipo", "HELLO");
                hello.addProperty("serverHost", localHost);
                hello.addProperty("serverPort", localServerPort);
                hello.addProperty("p2pPort", localP2pPort);
                hello.addProperty("ts", System.currentTimeMillis());
                pw.println(hello.toString());
                String ack = br.readLine();
                logger.info("P2P TX HELLO -> {}:{} | ACK={}", host, port, ack != null ? ack.substring(0, Math.min(100, ack.length())) : "null");
                
                // Procesar ACK enriquecido
                if (ack != null) {
                    try {
                        JsonObject ackObj = gson.fromJson(ack, JsonObject.class);
                        if (ackObj.has("serverPort")) {
                            try { int sp = ackObj.get("serverPort").getAsInt(); peerServerPorts.put(key(host, port), sp); } catch (Exception ignored) {}
                        }
                        if (ackObj.has("usuarios")) {
                            com.google.gson.JsonArray usuarios = ackObj.getAsJsonArray("usuarios");
                            for (com.google.gson.JsonElement e : usuarios) {
                                com.google.gson.JsonObject ju = e.getAsJsonObject();
                                boolean esLocal = ju.has("local") && ju.get("local").getAsBoolean();
                                if (esLocal) {
                                    // Usuario del servidor remoto
                                    int id = ju.get("id").getAsInt();
                                    String nombre = ju.get("nombre").getAsString();
                                    String k = key(host, port);
                                    remoteUsers.computeIfAbsent(k, kk -> new ConcurrentHashMap<>()).put(id, nombre);
                                } else if (ju.has("servidorHost")) {
                                    // Usuario de otro servidor (transitivo)
                                    String rh = ju.get("servidorHost").getAsString();
                                    int rp = ju.get("servidorP2pPort").getAsInt();
                                    if (ju.has("servidorPort") && !ju.get("servidorPort").isJsonNull()) {
                                        int sp = ju.get("servidorPort").getAsInt();
                                        peerServerPorts.put(key(rh, rp), sp);
                                    }
                                    int id = ju.get("id").getAsInt();
                                    String nombre = ju.get("nombre").getAsString();
                                    String k = key(rh, rp);
                                    remoteUsers.computeIfAbsent(k, kk -> new ConcurrentHashMap<>()).put(id, nombre);
                                }
                            }
                        }
                        if (ackObj.has("servidores")) {
                            com.google.gson.JsonArray servidores = ackObj.getAsJsonArray("servidores");
                            for (com.google.gson.JsonElement e : servidores) {
                                com.google.gson.JsonObject js = e.getAsJsonObject();
                                String h = js.get("host").getAsString();
                                int p = js.get("port").getAsInt();
                                String estado = js.get("estado").getAsString();
                                // Evitar auto-registro de nosotros mismos
                                if (p == localP2pPort && (h.equals(localHost) || "localhost".equalsIgnoreCase(h) || "127.0.0.1".equals(h))) {
                                    continue;
                                }
                                String k = key(h, p);
                                if (!peerStatus.containsKey(k)) {
                                    peerStatus.put(k, new PeerStatus(h, p, null, estado));
                                    try { servidorDAO.upsert(h, p, estado, null); } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Error procesando ACK de announceTo: {}", ex.getMessage());
                    }
                }
                
                // Actualizar estado como ONLINE tras HELLO exitoso
                
                peerStatus.put(key(host, port), new PeerStatus(host, port, LocalDateTime.now(), "ONLINE"));
                try { servidorDAO.upsert(host, port, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
                
                // Notificar conexión exitosa: siempre 'conectado' en HELLO directo
                if (serverStatusHandler != null) {
                    int sp = peerServerPorts.getOrDefault(key(host, port), -1);
                    serverStatusHandler.onServerConnected(host, port, sp);
                }
            } catch (Exception e) {
                logger.warn("P2P HELLO fallo -> {}:{} ({})", host, port, e.getMessage());
                
                // Marcar como OFFLINE si falla
                PeerStatus prevStatus = peerStatus.get(key(host, port));
                boolean estabaOnline = prevStatus != null && "ONLINE".equals(prevStatus.getEstado());
                
                peerStatus.put(key(host, port), new PeerStatus(host, port, null, "OFFLINE"));
                try { servidorDAO.upsert(host, port, "OFFLINE", null); } catch (Exception ignored) {}
                
                // Notificar desconexión si estaba online
                if (serverStatusHandler != null && estabaOnline) {
                    serverStatusHandler.onServerOffline(host, port);
                }
            }
        });
    }

    public void broadcastUserPresence(String usuario, int idUsuario, String estado, String clientIp, Integer clientPort) {
        // estado: CONNECTED / DISCONNECTED
        for (String peer : allPeersOnce()) {
            String[] hp = peer.split(":");
            if (hp.length != 2) continue;
            String host = hp[0]; int port = parseIntSafe(hp[1], -1);
            if (port <= 0) continue;
            pool.execute(() -> doSendPresence(host, port, usuario, idUsuario, estado, clientIp, clientPort));
        }
    }

    private void doSendPresence(String host, int port, String usuario, int idUsuario, String estado, String clientIp, Integer clientPort) {
        try (Socket s = new Socket(host, port);
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {
            JsonObject j = new JsonObject();
            j.addProperty("tipo", "USER_PRESENCE");
            j.addProperty("serverHost", localHost);
            j.addProperty("serverPort", localServerPort);
            j.addProperty("p2pPort", localP2pPort);
            j.addProperty("usuario", usuario);
            j.addProperty("idUsuario", idUsuario);
            j.addProperty("estado", estado);
            if (clientIp != null) j.addProperty("clientIp", clientIp);
            if (clientPort != null) j.addProperty("clientPort", clientPort);
            j.addProperty("ts", System.currentTimeMillis());
            pw.println(j.toString());
            logger.info("P2P TX USER_PRESENCE [{}] user={} id={} -> {}:{}", estado, usuario, idUsuario, host, port);
            peerStatus.compute(key(host, port), (k, ps) -> {
                PeerStatus n = ps != null ? ps : new PeerStatus(host, port, null, "UNKNOWN");
                n.setLastSeen(LocalDateTime.now()); n.setEstado("ONLINE"); return n;
            });
            try { servidorDAO.upsert(host, port, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
        } catch (Exception e) {
            logger.warn("P2P PRESENCE fallo -> {}:{} ({})", host, port, e.getMessage());
            peerStatus.compute(key(host, port), (k, ps) -> {
                PeerStatus n = ps != null ? ps : new PeerStatus(host, port, null, "UNKNOWN");
                n.setEstado("OFFLINE"); return n;
            });
            try { servidorDAO.upsert(host, port, "OFFLINE", null); } catch (Exception ignored) {}
        }
    }

    public List<PeerStatus> getPeerStatuses() {
        List<PeerStatus> list = new ArrayList<>();
        for (PeerStatus ps : peerStatus.values()) {
            if (ps == null) continue;
            // Filtrar nosotros mismos por seguridad
            if (ps.getPort() == localP2pPort && (ps.getHost().equals(localHost) || "localhost".equalsIgnoreCase(ps.getHost()) || "127.0.0.1".equals(ps.getHost()))) {
                continue;
            }
            list.add(ps);
        }
        return list;
    }

    public static class RemoteUserPresence {
        public final String servidorHost; public final int servidorP2pPort; public final Integer servidorPort; public final int idUsuario; public final String nombreUsuario; public final String clientIp; public final Integer clientPort;
        public RemoteUserPresence(String h, int p2p, Integer srvPort, int id, String nom, String clientIp, Integer clientPort) { this.servidorHost=h; this.servidorP2pPort=p2p; this.servidorPort=srvPort; this.idUsuario=id; this.nombreUsuario=nom; this.clientIp=clientIp; this.clientPort=clientPort; }
    }
    public List<RemoteUserPresence> getRemoteUserPresences() {
        List<RemoteUserPresence> out = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, String>> e : remoteUsers.entrySet()) {
            String[] hp = e.getKey().split(":");
            if (hp.length!=2) continue; String h=hp[0]; int p=parseIntSafe(hp[1],-1); if (p<=0) continue;
            Integer srvPort = peerServerPorts.getOrDefault(e.getKey(), null);
            for (Map.Entry<Integer, String> u : e.getValue().entrySet()) {
                ClientInfo ci = remoteUserClients.getOrDefault(e.getKey(), java.util.Map.of()).get(u.getKey());
                String cip = ci != null ? ci.ip : null; Integer cpt = ci != null ? ci.port : null;
                out.add(new RemoteUserPresence(h, p, srvPort, u.getKey(), u.getValue(), cip, cpt));
            }
        }
        return out;
    }

    public void setHandler(Handler handler) { this.handler = handler; }
    
    public void setLocalConnectedUsersProvider(LocalConnectedUsersProvider provider) {
        this.localConnectedUsersProvider = provider;
    }

    public void setServerStatusHandler(ServerStatusHandler handler) {
        this.serverStatusHandler = handler;
    }

    public void setRemotePresenceHandler(RemotePresenceHandler handler) {
        this.remotePresenceHandler = handler;
    }

    private void sweepPeersAging() {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, PeerStatus> e : peerStatus.entrySet()) {
            PeerStatus ps = e.getValue();
            LocalDateTime ls = ps.getLastSeen();
            // Si no hay lastSeen nunca o han pasado >60s, marcar como OFFLINE
            boolean stale = (ls == null) || java.time.Duration.between(ls, now).getSeconds() > 60;
            if (stale && !"OFFLINE".equals(ps.getEstado())) {
                ps.setEstado("OFFLINE");
                try { servidorDAO.upsert(ps.getHost(), ps.getPort(), "OFFLINE", ps.getLastSeen()); } catch (Exception ignored) {}
                String k = key(ps.getHost(), ps.getPort());
                Map<Integer, String> removed = remoteUsers.remove(k);
                if (removed != null && remotePresenceHandler != null) {
                    for (Map.Entry<Integer, String> ru : removed.entrySet()) {
                        try { remotePresenceHandler.onRemotePresenceChanged(ps.getHost(), ps.getPort(), ru.getKey(), ru.getValue(), "DISCONNECTED"); } catch (Exception ignored) {}
                    }
                }
                // Notificar que servidor pasó a offline por timeout
                if (serverStatusHandler != null) {
                    serverStatusHandler.onServerOffline(ps.getHost(), ps.getPort());
                }
            }
        }
    }

    public void forwardDirectMessage(String host, int port, int idEmisor, String nombreEmisor, int idReceptor, String contenido, Long idMensaje) {
        pool.execute(() -> {
            try (Socket s = new Socket(host, port); PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {
                JsonObject j = new JsonObject();
                j.addProperty("tipo", "FORWARD_DM");
                j.addProperty("serverHost", localHost);
                j.addProperty("serverPort", localServerPort);
                j.addProperty("p2pPort", localP2pPort);
                j.addProperty("idEmisor", idEmisor);
                j.addProperty("nombreEmisor", nombreEmisor);
                j.addProperty("idReceptor", idReceptor);
                j.addProperty("contenido", contenido);
                if (idMensaje != null) j.addProperty("idMensaje", idMensaje);
                j.addProperty("ts", System.currentTimeMillis());
                pw.println(j.toString());
                logger.info("P2P TX FORWARD_DM {}:{} emisor={}({}) receptor={} contenido='{}'", host, port, idEmisor, nombreEmisor, idReceptor, contenido);
                peerStatus.compute(key(host, port), (k, ps) -> { PeerStatus n = ps!=null?ps:new PeerStatus(host, port, null, "UNKNOWN"); n.setLastSeen(LocalDateTime.now()); n.setEstado("ONLINE"); return n; });
                try { servidorDAO.upsert(host, port, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
            } catch (Exception e) {
                logger.warn("P2P FORWARD_DM fallo -> {}:{} ({})", host, port, e.getMessage());
                peerStatus.compute(key(host, port), (k, ps) -> { PeerStatus n = ps!=null?ps:new PeerStatus(host, port, null, "UNKNOWN"); n.setEstado("OFFLINE"); return n; });
                try { servidorDAO.upsert(host, port, "OFFLINE", null); } catch (Exception ignored) {}
            }
        });
    }

    public void forwardDirectAudio(String host, int port, int idEmisor, String nombreEmisor, int idReceptor, String audioBase64, String transcripcion, Long idMensaje) {
        pool.execute(() -> {
            try (Socket s = new Socket(host, port); PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {
                JsonObject j = new JsonObject();
                j.addProperty("tipo", "FORWARD_AUDIO");
                j.addProperty("serverHost", localHost);
                j.addProperty("serverPort", localServerPort);
                j.addProperty("p2pPort", localP2pPort);
                j.addProperty("idEmisor", idEmisor);
                j.addProperty("nombreEmisor", nombreEmisor);
                j.addProperty("idReceptor", idReceptor);
                j.addProperty("audioData", audioBase64);
                if (transcripcion != null) j.addProperty("transcripcion", transcripcion);
                if (idMensaje != null) j.addProperty("idMensaje", idMensaje);
                j.addProperty("ts", System.currentTimeMillis());
                pw.println(j.toString());
                logger.info("P2P TX FORWARD_AUDIO {}:{} emisor={}({}) receptor={} bytes={} trans='{}'", host, port, idEmisor, nombreEmisor, idReceptor, audioBase64!=null?audioBase64.length():0, transcripcion);
                peerStatus.compute(key(host, port), (k, ps) -> { PeerStatus n = ps!=null?ps:new PeerStatus(host, port, null, "UNKNOWN"); n.setLastSeen(LocalDateTime.now()); n.setEstado("ONLINE"); return n; });
                try { servidorDAO.upsert(host, port, "ONLINE", LocalDateTime.now()); } catch (Exception ignored) {}
            } catch (Exception e) {
                logger.warn("P2P FORWARD_AUDIO fallo -> {}:{} ({})", host, port, e.getMessage());
                peerStatus.compute(key(host, port), (k, ps) -> { PeerStatus n = ps!=null?ps:new PeerStatus(host, port, null, "UNKNOWN"); n.setEstado("OFFLINE"); return n; });
                try { servidorDAO.upsert(host, port, "OFFLINE", null); } catch (Exception ignored) {}
            }
        });
    }

    public static class PeerAddress {
        public final String host; public final int p2pPort;
        public PeerAddress(String host, int p2pPort) { this.host = host; this.p2pPort = p2pPort; }
    }

    /**
     * Intenta resolver en qué peer remoto reside un usuario remoto por su id.
     * Asume IDs globalmente únicos a través de servidores.
     */
    public PeerAddress findPeerForUser(int idUsuario) {
        for (Map.Entry<String, Map<Integer, String>> e : remoteUsers.entrySet()) {
            if (e.getValue().containsKey(idUsuario)) {
                String[] hp = e.getKey().split(":");
                if (hp.length == 2) {
                    String h = hp[0]; int p = parseIntSafe(hp[1], -1);
                    if (p > 0) return new PeerAddress(h, p);
                }
            }
        }
        return null;
    }

    /** Notificar a todos los pares que este servidor va a desconectarse (OFFLINE). */
    public void broadcastOffline() {
        for (String peer : allPeersOnce()) {
            String[] hp = peer.split(":"); if (hp.length!=2) continue;
            String host = hp[0]; int port = parseIntSafe(hp[1], -1); if (port<=0) continue;
            pool.execute(() -> {
                try (Socket s = new Socket(host, port); PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {
                    JsonObject j = new JsonObject();
                    j.addProperty("tipo", "SERVER_OFFLINE");
                    j.addProperty("serverHost", localHost);
                    j.addProperty("serverPort", localServerPort);
                    j.addProperty("p2pPort", localP2pPort);
                    j.addProperty("ts", System.currentTimeMillis());
                    pw.println(j.toString());
                    logger.info("P2P TX SERVER_OFFLINE -> {}:{}", host, port);
                } catch (Exception e) {
                    logger.debug("SERVER_OFFLINE fallo -> {}:{} ({})", host, port, e.getMessage());
                }
            });
        }
    }

    /**
     * Solicitar historial de un usuario a un servidor específico.
     * La respuesta se almacena en cache y debe ser recuperada con getHistoryResponse().
     */
    public void requestUserHistory(String host, int port, int idUsuario) {
        pool.execute(() -> {
            try (Socket s = new Socket(host, port);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                 BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                JsonObject req = new JsonObject();
                req.addProperty("tipo", "REQUEST_HISTORY");
                req.addProperty("serverHost", localHost);
                req.addProperty("p2pPort", localP2pPort);
                req.addProperty("idUsuario", idUsuario);
                pw.println(req.toString());
                logger.info("P2P TX REQUEST_HISTORY -> {}:{} para usuario {}", host, port, idUsuario);
                
                // Esperar respuesta
                String respuesta = br.readLine();
                if (respuesta != null) {
                    JsonObject resp = gson.fromJson(respuesta, JsonObject.class);
                    if ("RESPONSE_HISTORY".equals(resp.get("tipo").getAsString())) {
                        com.google.gson.JsonArray mensajesJson = resp.has("mensajes") ? resp.getAsJsonArray("mensajes") : new com.google.gson.JsonArray();
                        com.google.gson.JsonArray canalesJson = resp.has("canales") ? resp.getAsJsonArray("canales") : new com.google.gson.JsonArray();
                        
                        String cacheKey = "history_" + idUsuario + "_" + host + ":" + port;
                        Map<String, Object> historyData = new ConcurrentHashMap<>();
                        historyData.put("mensajes", mensajesJson);
                        historyData.put("canales", canalesJson);
                        historyData.put("timestamp", System.currentTimeMillis());
                        historyCache.put(cacheKey, historyData);
                        
                        logger.info("P2P RX RESPONSE_HISTORY de {}:{} - {} mensajes, {} canales", 
                            host, port, mensajesJson.size(), canalesJson.size());
                    }
                }
            } catch (Exception e) {
                logger.warn("Error solicitando historial a {}:{} para usuario {}: {}", host, port, idUsuario, e.getMessage());
            }
        });
    }

    /**
     * Solicitar historial a todos los servidores conectados para un usuario.
     */
    public void requestUserHistoryFromAllPeers(int idUsuario) {
        for (String peer : allPeersOnce()) {
            String[] hp = peer.split(":"); 
            if (hp.length != 2) continue;
            String host = hp[0]; 
            int port = parseIntSafe(hp[1], -1); 
            if (port <= 0) continue;
            requestUserHistory(host, port, idUsuario);
        }
    }

    /**
     * Obtener respuesta de historial desde el cache.
     * Retorna null si no está disponible.
     */
    public Map<String, Object> getHistoryResponse(int idUsuario, String host, int port) {
        String cacheKey = "history_" + idUsuario + "_" + host + ":" + port;
        return historyCache.get(cacheKey);
    }

    /**
     * Obtener todas las respuestas de historial disponibles para un usuario.
     */
    public List<Map<String, Object>> getAllHistoryResponses(int idUsuario) {
        List<Map<String, Object>> responses = new ArrayList<>();
        String prefix = "history_" + idUsuario + "_";
        for (Map.Entry<String, Map<String, Object>> entry : historyCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                responses.add(entry.getValue());
            }
        }
        return responses;
    }

    /**
     * Limpiar respuestas de historial del cache.
     */
    public void clearHistoryCache(int idUsuario) {
        String prefix = "history_" + idUsuario + "_";
        historyCache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }
}

