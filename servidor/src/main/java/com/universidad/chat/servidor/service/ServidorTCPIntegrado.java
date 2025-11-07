package com.universidad.chat.servidor.service;

import com.universidad.chat.servidor.model.Usuario;
import com.universidad.chat.servidor.model.MensajeLog;
import com.universidad.chat.servidor.model.Canal;
import com.universidad.chat.servidor.util.Mensaje;
import com.universidad.chat.servidor.util.TipoMensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import com.universidad.chat.servidor.util.adapter.LocalDateTimeAdapter;
import com.universidad.chat.servidor.config.ServidorConfig;
import com.universidad.chat.servidor.p2p.InterServerService;
import com.universidad.chat.servidor.p2p.PeerStatus;

/**
 * Servidor TCP completamente integrado con servicios de negocio.
 * Aplica patrón Object Pool para gestionar hilos.
 */
public class ServidorTCPIntegrado {
    private static final Logger logger = LoggerFactory.getLogger(ServidorTCPIntegrado.class);
    private static final int MAX_CLIENTES = 100;

    private ServerSocket serverSocket;
    private ExecutorService poolHilos;
    private ConcurrentHashMap<Integer, ManejadorCliente> clientesConectados;
    private boolean ejecutando;
    private volatile ServidorEventListener listener;
    
    // Servicios de negocio
    private UsuarioService usuarioService;
    private CanalService canalService;
    private MensajeService mensajeService;
    private TranscripcionService transcripcionService;
    private Gson gson;

    // P2P entre servidores
    private ServidorConfig servidorConfig;
    private InterServerService interServerService;

    public ServidorTCPIntegrado() {
        this.poolHilos = Executors.newFixedThreadPool(MAX_CLIENTES); // Object Pool pattern
        this.clientesConectados = new ConcurrentHashMap<>();
        this.ejecutando = false;
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
        // Inicializar servicios
        this.usuarioService = new UsuarioService();
        this.canalService = new CanalService();
        this.mensajeService = new MensajeService();
        this.transcripcionService = new TranscripcionService();
        this.servidorConfig = new ServidorConfig();
    }

    public void setListener(ServidorEventListener listener) { this.listener = listener; }

    public void iniciar() throws IOException {
        int puerto = servidorConfig.getServerPort();
        serverSocket = new ServerSocket(puerto);
        ejecutando = true;
    logger.info("Servidor TCP iniciado en puerto {}", puerto);
    if (listener != null) listener.onServerStarted(puerto);

        // Iniciar servicio P2P entre servidores (no bloqueante)
        try {
            if (servidorConfig.isP2pEnabled()) {
                interServerService = new InterServerService(
                        servidorConfig.getServerHost(), servidorConfig.getServerPort(),
                        servidorConfig.getP2pPort(), servidorConfig.getP2pPeers());
                // Handler para mensajes reenviados
                interServerService.setHandler(new InterServerService.Handler() {
                    @Override
                    public void onDirectMessageFromPeer(String peerHost, int peerPort, int idEmisor, String nombreEmisor, int idReceptor, String contenido, Long idMensajeOrigen) {
                        try {
                            // Verificar si el mensaje ya existe para evitar duplicados
                            if (mensajeService.mensajeExiste(idEmisor, idReceptor, null, contenido)) {
                                logger.debug("Mensaje duplicado detectado de {} a {}, no se guardará", idEmisor, idReceptor);
                                // Aunque no se guarde, enviar al usuario si está conectado
                                JsonObject mensajeReenvio = new JsonObject();
                                mensajeReenvio.addProperty("idEmisor", idEmisor);
                                mensajeReenvio.addProperty("idReceptor", idReceptor);
                                mensajeReenvio.addProperty("contenido", contenido);
                                enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_TEXTO, idEmisor, mensajeReenvio.toString()));
                                return;
                            }
                            
                            int idMsg = mensajeService.registrarMensajeTexto(idEmisor, idReceptor, null, contenido);
                            try {
                                String ev = String.format("DM recibido de %d a %d (via %s:%d)", idEmisor, idReceptor, peerHost, peerPort);
                                mensajeService.registrarEvento("DM_RECIBIDO", ev, idReceptor, null);
                            } catch (Exception ignored) {}
                            if (listener != null) listener.onLogsChanged();
                            JsonObject mensajeReenvio = new JsonObject();
                            mensajeReenvio.addProperty("idEmisor", idEmisor);
                            mensajeReenvio.addProperty("idReceptor", idReceptor);
                            if (idMsg > 0) mensajeReenvio.addProperty("id", idMsg);
                            mensajeReenvio.addProperty("contenido", contenido);
                            enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_TEXTO, idEmisor, mensajeReenvio.toString()));
                        } catch (Exception ex) {
                            logger.error("Error entregando DM reenviado desde {}:{}", peerHost, peerPort, ex);
                        }
                    }

                    @Override
                    public void onDirectAudioFromPeer(String peerHost, int peerPort, int idEmisor, String nombreEmisor, int idReceptor, String audioBase64, String transcripcion, Long idMensajeServidorOrigen) {
                        try {
                            // Verificar si el mensaje de audio ya existe (por transcripción)
                            String contenidoCheck = transcripcion != null && !transcripcion.isEmpty() ? transcripcion : audioBase64.substring(0, Math.min(100, audioBase64.length()));
                            if (mensajeService.mensajeExiste(idEmisor, idReceptor, null, contenidoCheck)) {
                                logger.debug("Mensaje de audio duplicado detectado de {} a {}, no se guardará", idEmisor, idReceptor);
                                // Aunque no se guarde, enviar al usuario si está conectado
                                JsonObject mensajeReenvio = new JsonObject();
                                mensajeReenvio.addProperty("idEmisor", idEmisor);
                                mensajeReenvio.addProperty("idReceptor", idReceptor);
                                mensajeReenvio.addProperty("audioData", audioBase64);
                                if (transcripcion != null) mensajeReenvio.addProperty("transcripcion", transcripcion);
                                enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_AUDIO, idEmisor, mensajeReenvio.toString()));
                                return;
                            }
                            
                            // Guardar archivo en disco
                            byte[] audioBytes = java.util.Base64.getDecoder().decode(audioBase64);
                            String directorioAudios = "uploads/audios";
                            java.nio.file.Path dirPath = java.nio.file.Paths.get(directorioAudios);
                            if (!java.nio.file.Files.exists(dirPath)) {
                                java.nio.file.Files.createDirectories(dirPath);
                            }
                            String nombreArchivo = String.format("audio_%d_%d.wav", System.currentTimeMillis(), idEmisor);
                            java.nio.file.Path archivoPath = dirPath.resolve(nombreArchivo);
                            java.nio.file.Files.write(archivoPath, audioBytes);

                            // Persistir audio recibido
                            int idMsg = mensajeService.registrarMensajeAudio(idEmisor, idReceptor, null, archivoPath.toString(), transcripcion != null ? transcripcion : "");
                            try {
                                String ev = String.format("AUDIO recibido de %d a %d (via %s:%d)", idEmisor, idReceptor, peerHost, peerPort);
                                mensajeService.registrarEvento("AUDIO_RECIBIDO", ev, idReceptor, null);
                            } catch (Exception ignored) {}
                            if (listener != null) { listener.onAudioLogsChanged(); listener.onLogsChanged(); }

                            // Entregar a receptor local
                            JsonObject mensajeReenvio = new JsonObject();
                            mensajeReenvio.addProperty("idEmisor", idEmisor);
                            mensajeReenvio.addProperty("idReceptor", idReceptor);
                            if (idMsg > 0) mensajeReenvio.addProperty("id", idMsg);
                            mensajeReenvio.addProperty("archivoAudio", archivoPath.toString());
                            mensajeReenvio.addProperty("audioData", audioBase64);
                            if (transcripcion != null) mensajeReenvio.addProperty("transcripcion", transcripcion);
                            enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_AUDIO, idEmisor, mensajeReenvio.toString()));
                        } catch (Exception ex) {
                            logger.error("Error entregando AUDIO reenviado desde {}:{}", peerHost, peerPort, ex);
                        }
                    }

                    @Override
                    public List<Map<String, Object>> onRequestUserHistory(int idUsuario) {
                        List<Map<String, Object>> mensajes = new ArrayList<>();
                        try {
                            // Obtener mensajes recientes del usuario (últimos 30 días)
                            List<MensajeLog> logs = mensajeService.obtenerMensajesRecientesDeUsuario(idUsuario, 30);
                            for (MensajeLog log : logs) {
                                Map<String, Object> msgData = new HashMap<>();
                                msgData.put("id", log.getId());
                                msgData.put("idEmisor", log.getIdEmisor());
                                msgData.put("idReceptor", log.getIdReceptor());
                                if (log.getIdCanal() != null) msgData.put("idCanal", log.getIdCanal());
                                msgData.put("contenido", log.getContenido());
                                msgData.put("tipoMensaje", log.getTipoMensaje());
                                if (log.getArchivoAudio() != null) msgData.put("archivoAudio", log.getArchivoAudio());
                                if (log.getTranscripcion() != null) msgData.put("transcripcion", log.getTranscripcion());
                                msgData.put("fecha", log.getFecha().toString());
                                mensajes.add(msgData);
                            }
                            logger.info("Enviando {} mensajes de historial para usuario {}", mensajes.size(), idUsuario);
                        } catch (Exception ex) {
                            logger.error("Error obteniendo historial de usuario {}", idUsuario, ex);
                        }
                        return mensajes;
                    }

                    @Override
                    public List<Map<String, Object>> onRequestUserChannels(int idUsuario) {
                        List<Map<String, Object>> canales = new ArrayList<>();
                        try {
                            List<Canal> canalesUsuario = canalService.obtenerCanalesDeUsuario(idUsuario);
                            for (Canal canal : canalesUsuario) {
                                Map<String, Object> canalData = new HashMap<>();
                                canalData.put("id", canal.getId());
                                canalData.put("nombre", canal.getNombre());
                                canalData.put("idCreador", canal.getIdCreador());
                                canalData.put("esPrivado", canal.isEsPrivado());
                                canalData.put("fechaCreacion", canal.getFechaCreacion().toString());
                                
                                // Obtener lista de usuarios del canal
                                List<Integer> usuariosCanal = canalService.obtenerUsuariosCanal(canal.getId());
                                canalData.put("usuarios", usuariosCanal);
                                
                                canales.add(canalData);
                            }
                            logger.info("Enviando {} canales de historial para usuario {}", canales.size(), idUsuario);
                        } catch (Exception ex) {
                            logger.error("Error obteniendo canales de usuario {}", idUsuario, ex);
                        }
                        return canales;
                    }

                    @Override
                    public void onChannelInvitationFromPeer(String peerHost, int peerPort, int idCanal, String nombreCanal, int idCreador, boolean esPrivado, int idInvitador, String nombreInvitador, int idInvitado) {
                        try {
                            // Notificar al usuario invitado local
                            JsonObject notificacion = new JsonObject();
                            notificacion.addProperty("tipo", "INVITACION_CANAL_REMOTO");
                            notificacion.addProperty("idCanal", idCanal);
                            notificacion.addProperty("nombreCanal", nombreCanal);
                            notificacion.addProperty("idCreador", idCreador);
                            notificacion.addProperty("esPrivado", esPrivado);
                            notificacion.addProperty("idInvitador", idInvitador);
                            notificacion.addProperty("nombreInvitador", nombreInvitador);
                            notificacion.addProperty("servidorHost", peerHost);
                            // CRÍTICO: peerPort ahora es el puerto TCP del servidor (8080, 8081, etc)
                            // Este es el puerto que debe usarse para identificar usuarios en BD
                            notificacion.addProperty("servidorPort", peerPort);
                            // Mantener compatibilidad (aunque ya no es el puerto P2P real)
                            notificacion.addProperty("servidorP2pPort", peerPort);
                            
                            enviarAUsuario(idInvitado, new Mensaje(TipoMensaje.NOTIFICACION, 0, notificacion.toString()));
                            logger.info("Invitación de canal remoto {} enviada a usuario {}", nombreCanal, idInvitado);
                            
                            // Registrar evento
                            try {
                                String ev = String.format("Invitación a canal remoto %s de servidor %s:%d", nombreCanal, peerHost, peerPort);
                                mensajeService.registrarEvento("INVITACION_CANAL_REMOTO", ev, idInvitado, null);
                            } catch (Exception ignored) {}
                        } catch (Exception ex) {
                            logger.error("Error procesando invitación de canal remoto", ex);
                        }
                    }

                    @Override
                    public void onChannelAcceptanceFromPeer(String peerHost, int peerPort, int idCanal, int idUsuario, String nombreUsuario) {
                        try {
                            // Registrar que usuario remoto aceptó unirse al canal
                            logger.info("╔═══════════════════════════════════════════════════════════════");
                            logger.info("║ ACEPTACIÓN DE CANAL DESDE SERVIDOR REMOTO");
                            logger.info("╠═══════════════════════════════════════════════════════════════");
                            logger.info("║ Canal ID: {}", idCanal);
                            logger.info("║ Usuario remoto: {} (ID: {})", nombreUsuario, idUsuario);
                            logger.info("║ Servidor: {}:{}", peerHost, peerPort);
                            logger.info("╚═══════════════════════════════════════════════════════════════");

                            // Paso 1: Asegurar que el usuario remoto exista en la tabla usuarios
                            final int idUsuarioLocal; // usar final para lambdas posteriores
                            int tmpId = idUsuario;
                            try {
                                int creado = usuarioService.obtenerOCrearUsuarioRemoto(idUsuario, nombreUsuario, peerHost, peerPort);
                                if (creado > 0) {
                                    tmpId = creado;
                                    if (creado != idUsuario) {
                                        logger.info("Mapeo de usuario remoto: remoteId={} -> localId={}", idUsuario, creado);
                                    } else {
                                        logger.debug("Usuario remoto mantiene mismo ID local={}", creado);
                                    }
                                } else {
                                    logger.error("Fallo creación/obtención de usuario remoto, se mantiene ID remoto {}", idUsuario);
                                }
                            } catch (Exception exRem) {
                                logger.error("No se pudo asegurar usuario remoto {}:{} (remoteId={}) - {}", peerHost, peerPort, idUsuario, exRem.getMessage());
                            }
                            idUsuarioLocal = tmpId;
                            
                            // IMPORTANTE: Agregar al usuario remoto como miembro del canal en BD local
                            // Solo si no existe ya (puede haber sido agregado al enviar la invitación)
                            try {
                                // Verificar si ya existe la relación
                                var miembrosActuales = canalService.obtenerMiembrosConServidor(idCanal);
                                boolean yaExiste = miembrosActuales.stream()
                                    .anyMatch(m -> m.getIdUsuario() == idUsuarioLocal && 
                                                   peerHost.equals(m.getServidorHost()) && 
                                                   peerPort == (m.getServidorPuerto() != null ? m.getServidorPuerto() : -1));
                                
                                if (yaExiste) {
                                    logger.info("✓ Usuario remoto {}:{}:{} ya existe en canal {} - no es necesario agregarlo nuevamente", 
                                               idUsuarioLocal, peerHost, peerPort, idCanal);
                                } else {
                                    // El usuario está en el servidor remoto (peerHost:peerPort)
                                    canalService.agregarUsuarioAceptadoDirecto(idCanal, idUsuarioLocal, peerHost, peerPort);
                                    logger.info("✓ Usuario remoto {}:{}:{} agregado como miembro del canal {} en BD local", 
                                               idUsuarioLocal, peerHost, peerPort, idCanal);
                                }
                            } catch (Exception ex) {
                                logger.error("✗ Error agregando usuario remoto (localId={}) al canal {}: {}", idUsuarioLocal, idCanal, ex.getMessage());
                            }
                            
                            // Notificar a todos los miembros del canal
                            JsonObject notifMiembro = new JsonObject();
                            notifMiembro.addProperty("tipo", "NUEVO_MIEMBRO_CANAL");
                            notifMiembro.addProperty("idCanal", idCanal);
                            notifMiembro.addProperty("idUsuario", idUsuarioLocal);
                            notifMiembro.addProperty("nombreUsuario", nombreUsuario);
                            notifMiembro.addProperty("esRemoto", true);
                            notifMiembro.addProperty("servidorHost", peerHost);
                            notifMiembro.addProperty("servidorP2pPort", peerPort);
                            
                            // Enviar notificación a todos los miembros del canal
                            try {
                                var miembros = canalService.obtenerMiembrosCanal(idCanal);
                                Mensaje notificacionMsg = new Mensaje(TipoMensaje.NOTIFICACION, 0, notifMiembro.toString());
                                for (int idMiembro : miembros) {
                                    enviarAUsuario(idMiembro, notificacionMsg);
                                }
                            } catch (Exception ex) {
                                logger.warn("Error enviando notificación de nuevo miembro remoto: {}", ex.getMessage());
                            }
                            
                            // Registrar evento
                            try {
                                String ev = String.format("Usuario remoto %s se unió al canal desde %s:%d", nombreUsuario, peerHost, peerPort);
                                mensajeService.registrarEvento("CANAL_MIEMBRO_REMOTO", ev, 0, idCanal);
                                if (listener != null) listener.onCanalMiembrosChanged(idCanal);
                            } catch (Exception ignored) {}
                        } catch (Exception ex) {
                            logger.error("Error procesando aceptación de canal remoto", ex);
                        }
                    }

                    @Override
                    public void onChannelMessageFromPeer(String peerHost, int peerPort, int idCanal, int idEmisor, String nombreEmisor, String contenido) {
                        try {
                            logger.info("╔═══════════════════════════════════════════════════════════════");
                            logger.info("║ MENSAJE DE CANAL REMOTO RECIBIDO");
                            logger.info("╠═══════════════════════════════════════════════════════════════");
                            logger.info("║ Origen: {}:{}", peerHost, peerPort);
                            logger.info("║ Canal ID: {}", idCanal);
                            logger.info("║ Emisor: {} (ID: {})", nombreEmisor, idEmisor);
                            logger.info("║ Contenido: '{}'", contenido);
                            logger.info("╚═══════════════════════════════════════════════════════════════");
                            
                            // Guardar mensaje en BD (evitando duplicados)
                            if (!mensajeService.mensajeExiste(idEmisor, 0, idCanal, contenido)) {
                                mensajeService.registrarMensajeTexto(idEmisor, 0, idCanal, contenido);
                                if (listener != null) listener.onLogsChanged();
                                logger.info("✓ Mensaje guardado en BD local");
                            } else {
                                logger.info("⊗ Mensaje duplicado - NO guardado en BD");
                            }
                            
                            // Reenviar a todos los miembros locales del canal
                            var miembros = canalService.obtenerMiembrosCanal(idCanal);
                            logger.info("✓ Miembros locales del canal: {}", miembros.size());
                            
                            JsonObject mensajeReenvio = new JsonObject();
                            mensajeReenvio.addProperty("idEmisor", idEmisor);
                            mensajeReenvio.addProperty("idCanal", idCanal);
                            mensajeReenvio.addProperty("contenido", contenido);
                            mensajeReenvio.addProperty("esRemoto", true);
                            mensajeReenvio.addProperty("servidorHost", peerHost);
                            mensajeReenvio.addProperty("servidorPuerto", peerPort);
                            
                            Mensaje mensajeCanal = new Mensaje(TipoMensaje.MENSAJE_CANAL, idEmisor, mensajeReenvio.toString());
                            
                            logger.info("╔═══════════════════════════════════════════════════════════════");
                            logger.info("║ REENVIANDO A MIEMBROS LOCALES");
                            logger.info("╠═══════════════════════════════════════════════════════════════");
                            for (int idMiembro : miembros) {
                                logger.info("║  ✉ Enviando a usuario local ID={}", idMiembro);
                                enviarAUsuario(idMiembro, mensajeCanal);
                            }
                            logger.info("╚═══════════════════════════════════════════════════════════════");
                            
                            logger.info("✓ Mensaje de canal remoto procesado completamente - {} miembros locales notificados", miembros.size());
                        } catch (Exception ex) {
                            logger.error("✗ ERROR procesando mensaje de canal remoto", ex);
                        }
                    }

                    @Override
                    public List<Map<String, Object>> onRequestChannelHistory(int idCanal) {
                        List<Map<String, Object>> mensajes = new ArrayList<>();
                        try {
                            List<MensajeLog> logs = mensajeService.obtenerMensajesCanal(idCanal);
                            for (MensajeLog log : logs) {
                                Map<String, Object> msgData = new HashMap<>();
                                msgData.put("id", log.getId());
                                msgData.put("idEmisor", log.getIdEmisor());
                                msgData.put("idCanal", log.getIdCanal());
                                msgData.put("contenido", log.getContenido());
                                msgData.put("tipoMensaje", log.getTipoMensaje());
                                if (log.getArchivoAudio() != null) msgData.put("archivoAudio", log.getArchivoAudio());
                                if (log.getTranscripcion() != null) msgData.put("transcripcion", log.getTranscripcion());
                                msgData.put("fecha", log.getFecha().toString());
                                mensajes.add(msgData);
                            }
                            logger.info("Enviando {} mensajes de historial para canal {}", mensajes.size(), idCanal);
                        } catch (Exception ex) {
                            logger.error("Error obteniendo historial de canal {}", idCanal, ex);
                        }
                        return mensajes;
                    }
                });
                interServerService.setLocalConnectedUsersProvider(() -> {
                    java.util.List<InterServerService.LocalUserInfo> lista = new java.util.ArrayList<>();
                    for (Map.Entry<Integer, ManejadorCliente> entry : clientesConectados.entrySet()) {
                        int id = entry.getKey();
                        var u = usuarioService.obtenerUsuarioPorId(id);
                        ManejadorCliente mc = entry.getValue();
                        if (u != null && mc != null && mc.socket != null) {
                            String cip = null; Integer cpt = null;
                            try { cip = mc.socket.getInetAddress().getHostAddress(); } catch (Exception ignored) {}
                            try { cpt = mc.socket.getPort(); } catch (Exception ignored) {}
                            lista.add(new InterServerService.LocalUserInfo(id, u.getNombreUsuario(), cip, cpt));
                        } else if (u != null) {
                            lista.add(new InterServerService.LocalUserInfo(id, u.getNombreUsuario(), null, null));
                        }
                    }
                    return lista;
                });
                // Handler para eventos de estado de servidores P2P
                interServerService.setServerStatusHandler(new InterServerService.ServerStatusHandler() {
                    @Override
                    public void onServerConnected(String host, int p2pPort, int serverPort) {
                        String mensaje = String.format("Servidor P2P conectado: %s:%d (servidor en puerto %d)", 
                            host, p2pPort, serverPort);
                        logger.info("P2P EVENT CONECTADO: {}", mensaje);
                        try {
                            int idEvento = mensajeService.registrarEvento("P2P_SERVIDOR_CONECTADO", mensaje, 0, null);
                            logger.info("Evento P2P_SERVIDOR_CONECTADO registrado con ID: {}", idEvento);
                            if (listener != null) listener.onLogsChanged();
                        } catch (Exception ex) {
                            logger.error("Error registrando evento de servidor conectado", ex);
                        }
                    }

                    @Override
                    public void onServerOnline(String host, int p2pPort, int serverPort) {
                        String mensaje = String.format("Servidor P2P en línea: %s:%d (servidor en puerto %d)", 
                            host, p2pPort, serverPort);
                        logger.info("P2P EVENT ONLINE: {}", mensaje);
                        try {
                            int idEvento = mensajeService.registrarEvento("P2P_SERVIDOR_ONLINE", mensaje, 0, null);
                            logger.info("Evento P2P_SERVIDOR_ONLINE registrado con ID: {}", idEvento);
                            if (listener != null) listener.onLogsChanged();
                        } catch (Exception ex) {
                            logger.error("Error registrando evento de servidor online", ex);
                        }
                    }

                    @Override
                    public void onServerOffline(String host, int p2pPort) {
                        String mensaje = String.format("Servidor P2P desconectado: %s:%d", host, p2pPort);
                        logger.info("P2P EVENT OFFLINE: {}", mensaje);
                        try {
                            int idEvento = mensajeService.registrarEvento("P2P_SERVIDOR_DESCONECTADO", mensaje, 0, null);
                            logger.info("Evento P2P_SERVIDOR_DESCONECTADO registrado con ID: {}", idEvento);
                            if (listener != null) listener.onLogsChanged();
                        } catch (Exception ex) {
                            logger.error("Error registrando evento de servidor offline", ex);
                        }
                    }

                    @Override
                    public void onServerRegistered(String host, int p2pPort) {
                        String mensaje = String.format("Servidor P2P registrado: %s:%d", host, p2pPort);
                        logger.info("P2P EVENT REGISTRADO: {}", mensaje);
                        try {
                            int idEvento = mensajeService.registrarEvento("P2P_SERVIDOR_REGISTRADO", mensaje, 0, null);
                            logger.info("Evento P2P_SERVIDOR_REGISTRADO registrado con ID: {}", idEvento);
                            if (listener != null) listener.onLogsChanged();
                        } catch (Exception ex) {
                            logger.error("Error registrando evento de servidor registrado", ex);
                        }
                    }
                });
                // Notificar a clientes ante cambios de presencia remota
                interServerService.setRemotePresenceHandler((host, p2pPort, idUsuarioRemoto, nombreUsuario, estado) -> {
                    try {
                        JsonObject notif = new JsonObject();
                        notif.addProperty("tipo", "USER_" + ("CONNECTED".equals(estado) ? "CONNECTED" : "DISCONNECTED"));
                        notif.addProperty("idUsuario", idUsuarioRemoto);
                        notif.addProperty("nombreUsuario", nombreUsuario);
                        difundirATodos(new Mensaje(TipoMensaje.NOTIFICACION, 0, notif.toString()));
                        logger.info("Notificada a clientes presencia remota {}: {}@{}:{}", estado, nombreUsuario, host, p2pPort);
                    } catch (Exception ex) {
                        logger.warn("No se pudo notificar presencia remota {} de {}: {}", estado, nombreUsuario, ex.getMessage());
                    }
                });
                interServerService.start();
            } else {
                logger.info("P2P deshabilitado por configuración");
            }
        } catch (Exception e) {
            logger.error("No se pudo iniciar P2P: {}", e.getMessage());
        }

        while (ejecutando) {
            try {
                Socket clienteSocket = serverSocket.accept();
                String remoteIp = clienteSocket.getInetAddress().getHostAddress();
                int remotePort = clienteSocket.getPort();
                String localIp = clienteSocket.getLocalAddress().getHostAddress();
                int localPort = clienteSocket.getLocalPort();
                logger.info("Nueva conexión desde {}:{} hacia {}:{}", remoteIp, remotePort, localIp, localPort);
                if (listener != null) listener.onClientSocketConnected(remoteIp + ":" + remotePort);
                
                ManejadorCliente manejador = new ManejadorCliente(clienteSocket);
                poolHilos.execute(manejador);
            } catch (IOException e) {
                if (ejecutando) {
                    logger.error("Error aceptando conexión", e);
                }
            }
        }
    }

    public void detener() {
        ejecutando = false;

        // Avisar a todos los clientes que el servidor se detiene
        try {
            broadcastATodosUsuarios("Servidor detenido");
        } catch (Exception ex) {
            logger.warn("No se pudo enviar aviso de detención a todos los usuarios", ex);
        }
        
        // Desconectar todos los clientes y cerrar sus sockets
        for (ManejadorCliente cliente : clientesConectados.values()) {
            try {
                if (cliente.idUsuario > 0) {
                    usuarioService.desconectarUsuario(cliente.idUsuario);
                }
                cliente.cerrarConexion();
            } catch (Exception ex) {
                logger.debug("Error cerrando conexión de cliente {}", cliente.idUsuario, ex);
            }
        }
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            poolHilos.shutdown();
            try {
                if (interServerService != null) {
                    // Avisar a pares P2P que este servidor se apaga
                    interServerService.broadcastOffline();
                    try { mensajeService.registrarEvento("P2P_SERVER_OFFLINE_BROADCAST", "Anuncio de apagado a pares", 0, null); } catch (Exception ignored) {}
                    interServerService.stop();
                }
            } catch (Exception ignored) {}
        } catch (IOException e) {
            logger.error("Error cerrando servidor", e);
        }
        if (listener != null) listener.onServerStopped();
    }

    // ==== Métodos para UI ====
    public java.util.List<com.universidad.chat.servidor.model.Usuario> obtenerUsuariosConectados() {
        var lista = usuarioService.obtenerUsuariosConectados();
        // Enriquecer con datos SOLO para usuarios locales que tenemos conectados por socket.
        for (var u : lista) {
            var mc = clientesConectados.get(u.getId());
            if (mc != null) {
                try { u.setPuertoConexion(mc.socket.getPort()); } catch (Exception ignored) {}
                // Para locales, aseguramos host/puerto de servidor actual si faltaran
                try {
                    if (u.getServidorHost() == null) u.setServidorHost(servidorConfig.getServerHost());
                    if (u.getServidorPuerto() == null) u.setServidorPuerto(servidorConfig.getServerPort());
                } catch (Exception ignored) {}
            }
            // Flag conectado proviene del DAO, pero lo normalizamos por seguridad
            try { u.setConectado(true); } catch (Exception ignored) {}
        }
        // Añadir usuarios remotos anunciados por P2P
        if (interServerService != null) {
            for (var r : interServerService.getRemoteUserPresences()) {
                com.universidad.chat.servidor.model.Usuario ur = new com.universidad.chat.servidor.model.Usuario();
                ur.setId(r.idUsuario);
                ur.setNombreUsuario(r.nombreUsuario);
                ur.setConectado(true);
                ur.setServidorHost(r.servidorHost);
                // Preferir el puerto del servidor de clientes si lo conocemos; si no, lo dejamos null (evita
                // duplicados temporales con claves diferentes host:puertoP2P vs host:puertoServidor)
                ur.setServidorPuerto(r.servidorPort);
                // Rellenar IP:Puerto del cliente remoto si lo conocemos
                try { if (r.clientIp != null) ur.setDireccionIP(r.clientIp); } catch (Exception ignored) {}
                try { if (r.clientPort != null) ur.setPuertoConexion(r.clientPort); } catch (Exception ignored) {}
                lista.add(ur);
            }
        }
        // Deduplicar con preferencia por entradas con puerto de servidor conocido.
        // 1) Reunimos claves "fuertes" (con puerto) y "débiles" (sin puerto)
        java.util.LinkedHashMap<String, com.universidad.chat.servidor.model.Usuario> strong = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, com.universidad.chat.servidor.model.Usuario> weak = new java.util.LinkedHashMap<>();
        java.util.HashSet<String> strongBases = new java.util.HashSet<>();
        for (var u : lista) {
            String nombre = u.getNombreUsuario()!=null?u.getNombreUsuario():("id-"+u.getId());
            String nombreKey = nombre.toLowerCase(); // normalizar por si cambia capitalización
            String host = u.getServidorHost()!=null?u.getServidorHost():"";
            Integer port = u.getServidorPuerto();
            String base = nombreKey + "@" + host;
            if (port != null && port > 0) {
                String k = base + ":" + port;
                if (!strong.containsKey(k)) {
                    strong.put(k, u);
                    strongBases.add(base);
                }
            } else {
                // Guardar sólo si aún no hay una fuerte para ese base; resolveremos después
                weak.putIfAbsent(base, u);
            }
        }
        // 2) Agregar débiles sólo si no existe una fuerte para el mismo (usuario@host)
        java.util.ArrayList<com.universidad.chat.servidor.model.Usuario> result = new java.util.ArrayList<>();
        result.addAll(strong.values());
        for (Map.Entry<String, com.universidad.chat.servidor.model.Usuario> e : weak.entrySet()) {
            if (!strongBases.contains(e.getKey())) {
                result.add(e.getValue());
            }
        }
        return result;
    }

    public java.util.List<com.universidad.chat.servidor.model.Usuario> obtenerTodosUsuarios() {
        var lista = usuarioService.obtenerTodosLosUsuarios();
        // Añadir puerto sólo para los que están conectados actualmente
        for (var u : lista) {
            var mc = clientesConectados.get(u.getId());
            if (mc != null) {
                try { u.setPuertoConexion(mc.socket.getPort()); } catch (Exception ignored) {}
            }
        }
        return lista;
    }

    public java.util.List<com.universidad.chat.servidor.model.Canal> obtenerCanales() {
        return canalService.obtenerTodosLosCanales();
    }

    public java.util.List<com.universidad.chat.servidor.model.MensajeLog> obtenerLogs() {
        return mensajeService.obtenerTodosLosLogs();
    }

    public java.util.List<com.universidad.chat.servidor.model.MensajeLog> obtenerMensajesAudioLogs() {
        return mensajeService.obtenerMensajesAudio();
    }

    public java.util.List<com.universidad.chat.servidor.model.Usuario> obtenerMiembrosCanalUsuarios(int idCanal) {
        var ids = canalService.obtenerMiembrosCanal(idCanal);
        java.util.List<com.universidad.chat.servidor.model.Usuario> usuarios = new java.util.ArrayList<>();
        for (int id : ids) {
            var u = usuarioService.obtenerUsuarioPorId(id);
            if (u != null) usuarios.add(u);
        }
        return usuarios;
    }

    public void forzarDesconexionUsuario(int idUsuario) {
        ManejadorCliente mc = clientesConectados.get(idUsuario);
        if (mc != null) {
            // Enviar notificación al cliente antes de desconectarlo
            try {
                JsonObject notif = new JsonObject();
                notif.addProperty("desconexion_forzada", true);
                notif.addProperty("contenido", "El servidor te ha desconectado");
                Mensaje mensaje = new Mensaje(TipoMensaje.NOTIFICACION, 0, notif.toString());
                mc.enviarMensaje(mensaje);
                // Dar tiempo para que el mensaje llegue antes de cerrar
                Thread.sleep(200);
            } catch (Exception e) {
                logger.error("Error enviando notificación de desconexión forzada", e);
            }
            mc.cerrarConexion();
        }
    }

    // Exponer estado de servidores pares
    public java.util.List<PeerStatus> obtenerServidoresPares() {
        if (interServerService == null) return java.util.List.of();
        return interServerService.getPeerStatuses();
    }

    // Anunciar manualmente a pares P2P (HELLO)
    public void anunciarAServidores() {
        if (interServerService != null) {
            interServerService.announceOnline();
            try { mensajeService.registrarEvento("P2P_HELLO_MANUAL", "HELLO enviado manualmente a pares", 0, null); } catch (Exception ignored) {}
            if (listener != null) listener.onLogsChanged();
        }
    }

    // Registrar servidor peer y opcionalmente anunciar HELLO directo
    public void registrarServidorPeer(String host, int puerto, boolean anunciar) {
        if (interServerService == null) return;
        interServerService.registerPeer(host, puerto);
        try { mensajeService.registrarEvento("P2P_PEER_REGISTRADO", host + ":" + puerto, 0, null); } catch (Exception ignored) {}
        if (anunciar) {
            interServerService.announceTo(host, puerto);
            try { mensajeService.registrarEvento("P2P_HELLO_DIRECTO", host + ":" + puerto, 0, null); } catch (Exception ignored) {}
        }
        if (listener != null) listener.onLogsChanged();
    }

    public int registrarUsuario(String nombreUsuario, String email, String contrasena, String fotoBase64, String ip) {
        return usuarioService.registrarUsuario(nombreUsuario, email, contrasena, fotoBase64, ip);
    }

    /**
     * Enviar una notificación a todos los usuarios conectados.
     */
    public void broadcastATodosUsuarios(String contenido) {
        JsonObject notif = new JsonObject();
        notif.addProperty("broadcast", true);
        notif.addProperty("contenido", contenido);
        Mensaje m = new Mensaje(TipoMensaje.NOTIFICACION, 0, notif.toString());
        difundirATodos(m);
        logger.info("Broadcast a todos los usuarios: {}", contenido);
        try { mensajeService.registrarEvento("BROADCAST_USUARIOS", contenido, 0, null); } catch (Exception ignored) {}
        if (listener != null) listener.onLogsChanged();
    }

    /**
     * Enviar un mensaje a todos los canales (a todos sus miembros).
     * Nota: Usuarios en múltiples canales pueden recibir el mensaje múltiples veces.
     */
    public void broadcastATodosCanales(String contenido) {
        java.util.List<com.universidad.chat.servidor.model.Canal> canales = canalService.obtenerTodosLosCanales();
        for (com.universidad.chat.servidor.model.Canal canal : canales) {
            if (canal == null) continue;
            int idCanal = canal.getId();
            var miembros = canalService.obtenerMiembrosCanal(idCanal);
            JsonObject mensajeReenvio = new JsonObject();
            mensajeReenvio.addProperty("idEmisor", 0);
            mensajeReenvio.addProperty("idCanal", idCanal);
            mensajeReenvio.addProperty("contenido", contenido);
            Mensaje mensajeCanal = new Mensaje(TipoMensaje.MENSAJE_CANAL, 0, mensajeReenvio.toString());
            for (int idMiembro : miembros) {
                enviarAUsuario(idMiembro, mensajeCanal);
            }
        }
        logger.info("Broadcast a todos los canales: {}", contenido);
        try { mensajeService.registrarEvento("BROADCAST_CANALES", contenido, 0, null); } catch (Exception ignored) {}
        if (listener != null) listener.onLogsChanged();
    }

    /**
     * Difundir mensaje a todos los usuarios conectados.
     */
    private void difundirATodos(Mensaje mensaje) {
        for (ManejadorCliente cliente : clientesConectados.values()) {
            try {
                cliente.enviarMensaje(mensaje);
            } catch (IOException e) {
                logger.error("Error difundiendo mensaje a usuario {}", cliente.idUsuario, e);
            }
        }
    }

    /**
     * Enviar mensaje a un usuario específico.
     */
    private void enviarAUsuario(int idUsuario, Mensaje mensaje) {
        ManejadorCliente cliente = clientesConectados.get(idUsuario);
        if (cliente != null) {
            try {
                cliente.enviarMensaje(mensaje);
            } catch (IOException e) {
                logger.error("Error enviando mensaje a usuario {}", idUsuario, e);
            }
        }
    }

    /**
     * Manejador de cliente individual.
     */
    private class ManejadorCliente implements Runnable {
        private final Socket socket;
        private DataInputStream entrada;
        private DataOutputStream salida;
        private int idUsuario = 0;
        private boolean autenticado = false;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                entrada = new DataInputStream(socket.getInputStream());
                salida = new DataOutputStream(socket.getOutputStream());

                while (ejecutando && !socket.isClosed()) {
                    // Leer cabecera del mensaje (10 bytes)
                    byte version = entrada.readByte();
                    if (version != 1) {
                        logger.debug("Protocolo versión {} recibido", version);
                    }
                    byte tipoCodigo = entrada.readByte();
                    int longitudCuerpo = entrada.readInt();
                    int idUsuarioMensaje = entrada.readInt();

                    // Validar tamaño del mensaje para evitar OutOfMemoryError
                    final int MAX_MESSAGE_SIZE = 50 * 1024 * 1024; // 50 MB
                    if (longitudCuerpo < 0 || longitudCuerpo > MAX_MESSAGE_SIZE) {
                        logger.error("Tamaño de mensaje inválido: {} bytes. Cerrando conexión.", longitudCuerpo);
                        break;
                    }

                    // Leer cuerpo
                    byte[] cuerpo = new byte[longitudCuerpo];
                    entrada.readFully(cuerpo);

                    // Construir mensaje
                    Mensaje mensaje = new Mensaje(TipoMensaje.fromCodigo(tipoCodigo), idUsuarioMensaje, cuerpo);
            logger.info("Mensaje recibido de usuario {}: {}", idUsuarioMensaje, mensaje.getTipo());
                    if (listener != null) {
                        byte[] cbytes = mensaje.getCuerpo();
                        String resumen = new String(cbytes, java.nio.charset.StandardCharsets.UTF_8);
                        if (resumen.length() > 120) resumen = resumen.substring(0, 120);
                        listener.onMessage(mensaje.getTipo(), idUsuarioMensaje, resumen);
                    }

                    // Procesar mensaje
                    procesarMensaje(mensaje);
                }
            } catch (EOFException e) {
                logger.info("Cliente desconectado: {}:{}", socket.getInetAddress().getHostAddress(), socket.getPort());
            } catch (IOException e) {
                logger.error("Error procesando cliente", e);
                if (listener != null) listener.onError("cliente", e.getMessage());
            } finally {
                cerrarConexion();
            }
        }

        private void procesarMensaje(Mensaje mensaje) {
            try {
                switch (mensaje.getTipo()) {
                    case REGISTRO -> procesarRegistro(mensaje);
                    case AUTENTICACION -> procesarAutenticacion(mensaje);
                    case MENSAJE_TEXTO -> procesarMensajeTexto(mensaje);
                    case MENSAJE_AUDIO -> procesarMensajeAudio(mensaje);
                    case SOLICITUD_CANAL -> procesarSolicitudCanal(mensaje);
                    case RESPUESTA_CANAL -> procesarRespuestaCanal(mensaje);
                    case MENSAJE_CANAL -> procesarMensajeCanal(mensaje);
                    case SOLICITUD_LISTA -> procesarSolicitudLista(mensaje);
                    case ENVIO_ARCHIVO -> procesarEnvioArchivo(mensaje);
                    case CIERRE_SESION -> procesarCierreSesion(mensaje);
                    default -> {
                        logger.warn("Tipo de mensaje no soportado: {}", mensaje.getTipo());
                        enviarError("Tipo de mensaje no soportado");
                    }
                }
            } catch (Exception e) {
                logger.error("Error procesando mensaje", e);
                try {
                    enviarError("Error procesando mensaje: " + e.getMessage());
                } catch (IOException ex) {
                    logger.error("Error enviando respuesta de error", ex);
                }
            }
        }

        private void procesarEnvioArchivo(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            String nombre = datos.get("nombre").getAsString();
            String dataBase64 = datos.get("data").getAsString();
            Integer idCanal = datos.has("idCanal") && !datos.get("idCanal").isJsonNull() ? datos.get("idCanal").getAsInt() : null;
            Integer idReceptor = datos.has("idReceptor") && !datos.get("idReceptor").isJsonNull() ? datos.get("idReceptor").getAsInt() : null;

            byte[] bytes = Base64.getDecoder().decode(dataBase64);
            Path dir = Path.of("uploads");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String fileSafe = System.currentTimeMillis() + "_" + nombre.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path destino = dir.resolve(fileSafe);
            Files.write(destino, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Registrar en log como tipo AUDIO/archivo (reutilizamos campo archivo_audio para ruta)
            mensajeService.registrarMensajeAudio(idUsuario, idReceptor != null ? idReceptor : 0, idCanal, destino.toString(), "");
            if (listener != null) { listener.onAudioLogsChanged(); listener.onLogsChanged(); }

            // Notificar receptor/es
            JsonObject notif = new JsonObject();
            notif.addProperty("idEmisor", idUsuario);
            notif.addProperty("nombre", nombre);
            notif.addProperty("ruta", destino.toString());
            if (idReceptor != null) {
                enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.ENVIO_ARCHIVO, idUsuario, notif.toString()));
            } else if (idCanal != null) {
                var miembros = canalService.obtenerMiembrosCanal(idCanal);
                Mensaje msg = new Mensaje(TipoMensaje.ENVIO_ARCHIVO, idUsuario, notif.toString());
                for (int idMiembro : miembros) {
                    if (idMiembro != idUsuario) enviarAUsuario(idMiembro, msg);
                }
            }

            // Confirmación al emisor
            JsonObject ok = new JsonObject();
            ok.addProperty("exito", true);
            ok.addProperty("mensaje", "Archivo recibido y guardado");
            enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, ok.toString()));
        }

        private void procesarRegistro(Mensaje mensaje) throws IOException {
            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            String nombreUsuario = datos.get("nombreUsuario").getAsString();
            String email = datos.get("email").getAsString();
            String contrasena = datos.get("contrasena").getAsString();
            String foto = datos.has("foto") ? datos.get("foto").getAsString() : null;
            String ip = socket.getInetAddress().getHostAddress();

            int id = usuarioService.registrarUsuario(nombreUsuario, email, contrasena, foto, ip);
            
            JsonObject respuesta = new JsonObject();
            if (id > 0) {
                respuesta.addProperty("exito", true);
                respuesta.addProperty("id", id);
                respuesta.addProperty("mensaje", "Usuario registrado exitosamente");
                try { mensajeService.registrarEvento("REGISTRO", "Usuario registrado: " + nombreUsuario, id, null); } catch (Exception ignored) {}
                if (listener != null) { listener.onUsuariosChanged(); listener.onLogsChanged(); }
            } else {
                respuesta.addProperty("exito", false);
                respuesta.addProperty("mensaje", "Error al registrar usuario");
            }
            
            enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, respuesta.toString()));
        }

        private void procesarAutenticacion(Mensaje mensaje) throws IOException {
            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            String nombreUsuario = datos.get("nombreUsuario").getAsString();
            String contrasena = datos.get("contrasena").getAsString();

            Usuario usuario = usuarioService.autenticar(nombreUsuario, contrasena);
            
            JsonObject respuesta = new JsonObject();
            if (usuario != null) {
                this.idUsuario = usuario.getId();
                this.autenticado = true;
                clientesConectados.put(idUsuario, this);
                
                // Actualizar estado de conexión
                usuarioService.conectarUsuario(idUsuario, socket.getInetAddress().getHostAddress());
                
                respuesta.addProperty("exito", true);
                respuesta.addProperty("id", usuario.getId());
                respuesta.addProperty("nombreUsuario", usuario.getNombreUsuario());
                respuesta.addProperty("email", usuario.getEmail());
                
                logger.info("Usuario autenticado: {} (ID: {})", nombreUsuario, idUsuario);
                if (listener != null) listener.onUserAuthenticated(idUsuario, nombreUsuario, socket.getInetAddress().getHostAddress());
                String detalleConexion = String.format("Usuario %s autenticado desde %s:%d al servidor %s:%d", 
                    nombreUsuario, 
                    socket.getInetAddress().getHostAddress(), 
                    socket.getPort(),
                    servidorConfig.getServerHost(),
                    servidorConfig.getServerPort());
                try { mensajeService.registrarEvento("LOGIN", detalleConexion, idUsuario, null); } catch (Exception ignored) {}
                if (listener != null) { listener.onConectadosChanged(); listener.onLogsChanged(); }
                // Difundir presencia a otros servidores
                try { if (interServerService != null) interServerService.broadcastUserPresence(nombreUsuario, idUsuario, "CONNECTED", socket.getInetAddress().getHostAddress(), socket.getPort()); } catch (Exception ignored) {}
                
                // Notificar a todos los clientes conectados que un nuevo usuario se conectó
                JsonObject notifConexion = new JsonObject();
                notifConexion.addProperty("tipo", "USER_CONNECTED");
                notifConexion.addProperty("idUsuario", idUsuario);
                notifConexion.addProperty("nombreUsuario", nombreUsuario);
                difundirATodos(new Mensaje(TipoMensaje.NOTIFICACION, 0, notifConexion.toString()));

                // Enviar respuesta de autenticación exitosa PRIMERO
                enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, respuesta.toString()));

                // DESPUÉS: Enviar historial pendiente AL USUARIO RECIÉN CONECTADO (async para no bloquear)
                final int idUsuarioFinal = idUsuario;
                poolHilos.execute(() -> {
                    try {
                        enviarHistorialAlUsuarioRecienConectado(idUsuarioFinal);
                    } catch (Exception e) {
                        logger.error("Error enviando historial al usuario {}: {}", idUsuarioFinal, e.getMessage());
                    }
                });
            } else {
                respuesta.addProperty("exito", false);
                respuesta.addProperty("mensaje", "Credenciales inválidas");
                enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, respuesta.toString()));
            }
        }

        private void procesarMensajeTexto(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            int idReceptor = datos.get("idReceptor").getAsInt();
            String contenido = datos.get("contenido").getAsString();

            // ¿Receptor local o remoto?
            com.universidad.chat.servidor.model.Usuario receptor = usuarioService.obtenerUsuarioPorId(idReceptor);
            boolean receptorLocal = (receptor != null) &&
                servidorConfig.getServerHost().equals(receptor.getServidorHost()) && servidorConfig.getServerPort() == (receptor.getServidorPuerto()!=null?receptor.getServidorPuerto():servidorConfig.getServerPort());

            // Registrar en log local si el emisor es local (siempre lo es)
            int idMensaje = mensajeService.registrarMensajeTexto(idUsuario, idReceptor, null, contenido);
            try {
                String ev = String.format("DM enviado de %d a %d", idUsuario, idReceptor);
                mensajeService.registrarEvento("DM_ENVIADO", ev, idUsuario, null);
            } catch (Exception ignored) {}
            if (listener != null) listener.onLogsChanged();

            if (receptorLocal) {
                // Entrega directa local
                JsonObject mensajeReenvio = new JsonObject();
                mensajeReenvio.addProperty("idEmisor", idUsuario);
                mensajeReenvio.addProperty("idReceptor", idReceptor);
                if (idMensaje > 0) mensajeReenvio.addProperty("id", idMensaje);
                mensajeReenvio.addProperty("contenido", contenido);
                enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_TEXTO, idUsuario, mensajeReenvio.toString()));
            } else {
                // Receptor remoto: intentar usar hint del cliente o la afinidad del receptor si existe
                String receptorHost = null; int receptorP2pPort = -1;
                if (datos.has("servidorHost")) receptorHost = datos.get("servidorHost").getAsString();
                if (datos.has("servidorP2pPort")) receptorP2pPort = datos.get("servidorP2pPort").getAsInt();
                if (receptor != null && receptor.getServidorHost() != null && receptor.getServidorPuerto()!=null) {
                    // Preferir afinidad conocida por la BD (si existiera receptor localmente)
                    receptorHost = receptor.getServidorHost();
                    // Usamos puerto P2P igual al servidorPuerto si no tenemos otro dato
                    receptorP2pPort = receptor.getServidorPuerto();
                }
                // Si aún no tenemos destino, intentar resolver por presencia P2P
                if ((receptorHost == null || receptorP2pPort <= 0) && interServerService != null) {
                    var addr = interServerService.findPeerForUser(idReceptor);
                    if (addr != null) { receptorHost = addr.host; receptorP2pPort = addr.p2pPort; }
                }
                if (interServerService != null && receptorHost != null && receptorP2pPort > 0) {
                    // Necesitamos nombre del emisor para el peer (para UI remota)
                    var emisor = usuarioService.obtenerUsuarioPorId(idUsuario);
                    String nombreEmisor = emisor != null ? emisor.getNombreUsuario() : ("user-" + idUsuario);
                    interServerService.forwardDirectMessage(receptorHost, receptorP2pPort, idUsuario, nombreEmisor, idReceptor, contenido, (long) idMensaje);
                } else {
                    logger.warn("No se pudo determinar servidor del receptor {} para reenvío P2P", idReceptor);
                }
            }

            // Confirmar al emisor
            JsonObject confirmacion = new JsonObject();
            confirmacion.addProperty("exito", true);
            confirmacion.addProperty("mensaje", "Mensaje enviado");
            enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, confirmacion.toString()));
        }

        private void procesarMensajeAudio(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            
            // Verificar si es para usuario o canal
            Integer idReceptor = datos.has("idReceptor") ? datos.get("idReceptor").getAsInt() : null;
            Integer idCanal = datos.has("idCanal") ? datos.get("idCanal").getAsInt() : null;
            
            if (idReceptor == null && idCanal == null) {
                enviarError("Debe especificar idReceptor o idCanal");
                return;
            }

            // Obtener datos de audio en Base64
            String audioBase64 = datos.get("audioData").getAsString();
            byte[] audioBytes = java.util.Base64.getDecoder().decode(audioBase64);
            
            // Transcribir audio a texto usando VOSK
            String transcripcion = "";
            if (!transcripcionService.estaListo()) {
                logger.warn("Servicio de transcripción no disponible. Modelo VOSK no cargado.");
                logger.warn("Descarga el modelo desde: https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip");
                logger.warn("Y descomprime en: vosk-models/vosk-model-small-es-0.42/");
            } else {
                try {
                    logger.info("Iniciando transcripción de audio ({} bytes)...", audioBytes.length);
                    transcripcion = transcripcionService.transcribir(audioBytes);
                    if (transcripcion == null || transcripcion.isEmpty()) {
                        logger.warn("Transcripción resultó vacía (silencio o audio no reconocido)");
                        transcripcion = ""; // Asegurar que no sea null
                    } else {
                        logger.info("✓ Audio transcrito exitosamente: '{}'", transcripcion);
                    }
                } catch (Exception e) {
                    logger.error("Error en transcripción automática", e);
                    transcripcion = "";
                }
            }
            
            // Guardar archivo en disco
            String directorioAudios = "uploads/audios";
            java.nio.file.Path dirPath = java.nio.file.Paths.get(directorioAudios);
            if (!java.nio.file.Files.exists(dirPath)) {
                java.nio.file.Files.createDirectories(dirPath);
            }
            
            // Nombre único para el archivo: audio_<timestamp>_<idEmisor>.wav
            String nombreArchivo = String.format("audio_%d_%d.wav", System.currentTimeMillis(), idUsuario);
            java.nio.file.Path archivoPath = dirPath.resolve(nombreArchivo);
            java.nio.file.Files.write(archivoPath, audioBytes);
            
            String rutaArchivo = archivoPath.toString();
            logger.info("Audio guardado en: {}", rutaArchivo);

            // Registrar en log con la ruta del archivo
            if (idCanal != null) {
                logger.info("Registrando mensaje de audio en BD - Canal: {}, Transcripción: '{}'", idCanal, transcripcion);
                int idMensaje = mensajeService.registrarMensajeAudio(idUsuario, 0, idCanal, rutaArchivo, transcripcion);
                // Enviar a todos los miembros del canal (incluye los bytes del audio para reproducción)
                var miembros = canalService.obtenerMiembrosCanal(idCanal);
                JsonObject mensajeReenvio = new JsonObject();
                mensajeReenvio.addProperty("idEmisor", idUsuario);
                mensajeReenvio.addProperty("idCanal", idCanal);
                if (idMensaje > 0) mensajeReenvio.addProperty("id", idMensaje);
                mensajeReenvio.addProperty("archivoAudio", rutaArchivo);
                mensajeReenvio.addProperty("audioData", audioBase64); // Enviar bytes para reproducción
                mensajeReenvio.addProperty("transcripcion", transcripcion);
                
                Mensaje mensajeCanal = new Mensaje(TipoMensaje.MENSAJE_AUDIO, idUsuario, mensajeReenvio.toString());
                for (int idMiembro : miembros) {
                    if (idMiembro != idUsuario) {
                        enviarAUsuario(idMiembro, mensajeCanal);
                    }
                }
            } else {
                logger.info("Registrando mensaje de audio en BD - Usuario: {}, Transcripción: '{}'", idReceptor, transcripcion);
                int idMensaje = mensajeService.registrarMensajeAudio(idUsuario, idReceptor, null, rutaArchivo, transcripcion);
                try {
                    String ev = String.format("AUDIO enviado de %d a %d", idUsuario, idReceptor);
                    mensajeService.registrarEvento("AUDIO_ENVIADO", ev, idUsuario, null);
                } catch (Exception ignored) {}
                // Determinar si receptor es local o remoto
                com.universidad.chat.servidor.model.Usuario receptor = usuarioService.obtenerUsuarioPorId(idReceptor);
                boolean receptorLocal = (receptor != null) &&
                    servidorConfig.getServerHost().equals(receptor.getServidorHost()) && servidorConfig.getServerPort() == (receptor.getServidorPuerto()!=null?receptor.getServidorPuerto():servidorConfig.getServerPort());

                if (receptorLocal) {
                    // Enviar al destinatario individual local (incluye bytes del audio)
                    JsonObject mensajeReenvio = new JsonObject();
                    mensajeReenvio.addProperty("idEmisor", idUsuario);
                    mensajeReenvio.addProperty("idReceptor", idReceptor);
                    if (idMensaje > 0) mensajeReenvio.addProperty("id", idMensaje);
                    mensajeReenvio.addProperty("archivoAudio", rutaArchivo);
                    mensajeReenvio.addProperty("audioData", audioBase64);
                    mensajeReenvio.addProperty("transcripcion", transcripcion);
                    enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_AUDIO, idUsuario, mensajeReenvio.toString()));
                } else {
                    // Receptor remoto: reenviar vía P2P con el audio embebido
                    String receptorHost = null; int receptorP2pPort = -1;
                    if (receptor != null && receptor.getServidorHost() != null && receptor.getServidorPuerto()!=null) {
                        receptorHost = receptor.getServidorHost();
                        receptorP2pPort = receptor.getServidorPuerto();
                    }
                    if ((receptorHost == null || receptorP2pPort <= 0) && interServerService != null) {
                        var addr = interServerService.findPeerForUser(idReceptor);
                        if (addr != null) { receptorHost = addr.host; receptorP2pPort = addr.p2pPort; }
                    }
                    if (interServerService != null && receptorHost != null && receptorP2pPort > 0) {
                        var emisor = usuarioService.obtenerUsuarioPorId(idUsuario);
                        String nombreEmisor = emisor != null ? emisor.getNombreUsuario() : ("user-" + idUsuario);
                        interServerService.forwardDirectAudio(receptorHost, receptorP2pPort, idUsuario, nombreEmisor, idReceptor, audioBase64, transcripcion, (long) idMensaje);
                    } else {
                        logger.warn("No se pudo determinar servidor del receptor {} para reenvío P2P de audio", idReceptor);
                    }
                }
            }
            
            if (listener != null) { listener.onAudioLogsChanged(); listener.onLogsChanged(); }
            
            // Confirmar al emisor
            JsonObject confirmacion = new JsonObject();
            confirmacion.addProperty("exito", true);
            confirmacion.addProperty("mensaje", "Mensaje de audio enviado");
            confirmacion.addProperty("archivoAudio", rutaArchivo);
            enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, confirmacion.toString()));
        }

        private void procesarSolicitudCanal(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            String accion = datos.get("accion").getAsString();
            
            JsonObject respuesta = new JsonObject();
            
            if ("crear".equals(accion)) {
                String nombre = datos.get("nombre").getAsString();
                boolean esPrivado = datos.get("esPrivado").getAsBoolean();
                
                int idCanal = canalService.crearCanal(nombre, idUsuario, esPrivado);
                
                if (idCanal > 0) {
                    respuesta.addProperty("exito", true);
                    respuesta.addProperty("idCanal", idCanal);
                    respuesta.addProperty("mensaje", "Canal creado exitosamente");
                    try { mensajeService.registrarEvento("CANAL_CREAR", "Canal creado: " + nombre + " (ID:" + idCanal + ")", idUsuario, idCanal); } catch (Exception ignored) {}
                    if (listener != null) { listener.onCanalesChanged(); listener.onLogsChanged(); }
                } else {
                    respuesta.addProperty("exito", false);
                    respuesta.addProperty("mensaje", "Error al crear canal");
                }
            } else if ("unirse".equals(accion)) {
                int idCanal = datos.get("idCanal").getAsInt();
                boolean exito = canalService.solicitarUnirse(idCanal, idUsuario);
                
                respuesta.addProperty("exito", exito);
                respuesta.addProperty("mensaje", exito ? "Solicitud enviada" : "Error al enviar solicitud");
                if (exito) {
                    try { mensajeService.registrarEvento("SOLICITUD_UNIRSE", "Usuario " + idUsuario + " solicitó unirse al canal " + idCanal, idUsuario, idCanal); } catch (Exception ignored) {}
                    if (listener != null) { listener.onLogsChanged(); }
                }
            } else if ("invitar".equals(accion)) {
                int idCanal = datos.get("idCanal").getAsInt();
                int idInvitado = datos.get("idUsuarioInvitado").getAsInt();
                
                logger.info("╔═══════════════════════════════════════════════════════════════");
                logger.info("║ INVITACIÓN A CANAL");
                logger.info("╠═══════════════════════════════════════════════════════════════");
                logger.info("║ Canal ID: {}", idCanal);
                logger.info("║ Invitador ID: {}", idUsuario);
                logger.info("║ Invitado ID: {}", idInvitado);
                
                // Verificar si el invitado es local o remoto
                var usuarioInvitado = usuarioService.obtenerUsuarioPorId(idInvitado);
                boolean invitadoLocal = (usuarioInvitado != null) &&
                    servidorConfig.getServerHost().equals(usuarioInvitado.getServidorHost()) && 
                    servidorConfig.getServerPort() == (usuarioInvitado.getServidorPuerto() != null ? usuarioInvitado.getServidorPuerto() : servidorConfig.getServerPort());
                
                logger.info("║ Invitado es: {}", invitadoLocal ? "LOCAL" : "REMOTO");
                logger.info("╚═══════════════════════════════════════════════════════════════");
                
                boolean exito;
                
                if (invitadoLocal) {
                    // Usuario local: agregar a la BD como pendiente (aceptado=FALSE)
                    exito = canalService.invitarUsuario(idCanal, idUsuario, idInvitado);
                    logger.info(exito ? "✓ Usuario LOCAL agregado a BD como pendiente" : "✗ ERROR: No se pudo agregar usuario local");
                } else {
                    // Usuario remoto: NO agregar a BD todavía, solo reenviar invitación
                    // Se agregará cuando acepte (en onChannelAcceptanceFromPeer)
                    exito = true; // Asumimos éxito si es remoto
                    logger.info("✓ Usuario REMOTO - NO se agrega a BD (se agregará al aceptar)");
                }
                
                respuesta.addProperty("exito", exito);
                respuesta.addProperty("mensaje", exito ? "Usuario invitado al canal" : "No fue posible invitar al usuario");
                
                if (exito) {
                    // Obtener información del canal para la notificación
                    var canal = canalService.obtenerCanalPorId(idCanal);
                    var invitador = usuarioService.obtenerUsuarioPorId(idUsuario);
                    String nombreCanal = canal != null ? canal.getNombre() : "Canal";
                    String nombreInvitador = invitador != null ? invitador.getNombreUsuario() : "Usuario";
                    
                    if (invitadoLocal) {
                        // Invitado local: notificar directamente
                        JsonObject notificacion = new JsonObject();
                        notificacion.addProperty("tipo", "INVITACION_CANAL");
                        notificacion.addProperty("idCanal", idCanal);
                        notificacion.addProperty("nombreCanal", nombreCanal);
                        notificacion.addProperty("idInvitador", idUsuario);
                        notificacion.addProperty("nombreInvitador", nombreInvitador);
                        enviarAUsuario(idInvitado, new Mensaje(TipoMensaje.NOTIFICACION, 0, notificacion.toString()));
                        logger.info("Invitación de canal {} enviada a usuario local {}", nombreCanal, idInvitado);
                    } else {
                        // Invitado remoto: reenviar vía P2P
                        String invitadoHost = null;
                        int invitadoP2pPort = -1;
                        
                        // CRÍTICO: Primero intentar obtener del registro de pares P2P
                        if (interServerService != null) {
                            var addr = interServerService.findPeerForUser(idInvitado);
                            if (addr != null) {
                                invitadoHost = addr.host;
                                invitadoP2pPort = addr.p2pPort;
                                logger.info("✓ Puerto P2P del invitado obtenido del registro: {}:{}", invitadoHost, invitadoP2pPort);
                            }
                        }
                        
                        // Fallback: usar info del usuario (pero convertir puerto TCP a P2P si es necesario)
                        if ((invitadoHost == null || invitadoP2pPort <= 0) && usuarioInvitado != null) {
                            if (usuarioInvitado.getServidorHost() != null) {
                                invitadoHost = usuarioInvitado.getServidorHost();
                                // Intentar inferir puerto P2P desde puerto TCP (heurística: TCP + 1010)
                                // Ej: 8080 -> 9090, 8081 -> 9091
                                Integer tcpPort = usuarioInvitado.getServidorPuerto();
                                if (tcpPort != null) {
                                    invitadoP2pPort = tcpPort + 1010;
                                    logger.warn("⚠ Puerto P2P inferido desde TCP: {} -> {} (puede ser incorrecto)", tcpPort, invitadoP2pPort);
                                }
                            }
                        }
                        
                        if (interServerService != null && invitadoHost != null && invitadoP2pPort > 0) {
                            // Obtener información del canal para enviar
                            int idCreador = canal != null ? canal.getIdCreador() : idUsuario;
                            boolean esPrivado = canal != null ? canal.isEsPrivado() : true;
                            interServerService.forwardChannelInvitation(invitadoHost, invitadoP2pPort, idCanal, nombreCanal, idCreador, esPrivado, idUsuario, nombreInvitador, idInvitado);
                            logger.info("Invitación de canal {} reenviada a usuario remoto {} en {}:{}", nombreCanal, idInvitado, invitadoHost, invitadoP2pPort);
                        } else {
                            logger.warn("No se pudo determinar servidor del invitado {} para reenvío de invitación", idInvitado);
                        }
                    }
                    
                    // Registrar evento
                    try {
                        mensajeService.registrarEvento("INVITACION_ENVIADA", "Usuario " + idInvitado + " invitado al canal " + idCanal + " por " + idUsuario, idUsuario, idCanal);
                    } catch (Exception ignored) {}
                    if (listener != null) {
                        listener.onLogsChanged();
                    }
                }
            } else if ("responder_invitacion".equals(accion)) {
                int idCanal = datos.get("idCanal").getAsInt();
                boolean aceptar = datos.get("aceptar").getAsBoolean();
                
                logger.info("╔═══════════════════════════════════════════════════════════════");
                logger.info("║ RESPUESTA A INVITACIÓN DE CANAL");
                logger.info("╠═══════════════════════════════════════════════════════════════");
                logger.info("║ Canal ID: {}", idCanal);
                logger.info("║ Usuario ID: {}", idUsuario);
                logger.info("║ Respuesta: {}", aceptar ? "ACEPTAR" : "RECHAZAR");
                
                // Detectar si es canal remoto
                boolean esRemoto = datos.has("servidorHost") && (datos.has("servidorPort") || datos.has("servidorP2pPort"));
                String servidorHost = esRemoto ? datos.get("servidorHost").getAsString() : null;
                // Preferir servidorPort (puerto TCP correcto) sobre servidorP2pPort (legacy)
                int servidorPort = -1;
                if (esRemoto) {
                    if (datos.has("servidorPort")) {
                        servidorPort = datos.get("servidorPort").getAsInt();
                    } else if (datos.has("servidorP2pPort")) {
                        servidorPort = datos.get("servidorP2pPort").getAsInt();
                        logger.warn("⚠ Usando servidorP2pPort como fallback (debería ser servidorPort)");
                    }
                }
                
                logger.info("║ Tipo de canal: {}", esRemoto ? "REMOTO" : "LOCAL");
                if (esRemoto) {
                    logger.info("║ Servidor remoto: {}:{} (puerto TCP)", servidorHost, servidorPort);
                }
                logger.info("╚═══════════════════════════════════════════════════════════════");
                
                // Obtener metadatos del canal de la notificación
                String nombreCanal = datos.has("nombreCanal") ? datos.get("nombreCanal").getAsString() : ("Canal-" + idCanal);
                int idCreador = datos.has("idCreador") ? datos.get("idCreador").getAsInt() : 0;
                boolean esPrivado = datos.has("esPrivado") ? datos.get("esPrivado").getAsBoolean() : true;
                
                if (aceptar) {
                    if (esRemoto) {
                        // Canal remoto: registrar localmente y notificar al servidor origen
                        var usuario = usuarioService.obtenerUsuarioPorId(idUsuario);
                        String nombreUsuario = usuario != null ? usuario.getNombreUsuario() : ("user-" + idUsuario);
                        
                        // Registrar el canal remoto localmente y agregar al usuario
                        // El creador está en el servidor remoto, el usuario actual está en este servidor (local)
                        boolean registrado = canalService.registrarCanalRemotoYAgregarUsuario(
                            idCanal, nombreCanal, 
                            idCreador, servidorHost, servidorPort, // ← AHORA USA PUERTO TCP CORRECTO
                            esPrivado, 
                            idUsuario, null, null // Usuario local en este servidor
                        );
                        if (!registrado) {
                            logger.warn("No se pudo registrar canal remoto {} para usuario {}", idCanal, idUsuario);
                        }
                        
                        // Para comunicación P2P, necesitamos el puerto P2P del servidor remoto
                        // Usar heurística: TCP+1010=P2P (8080→9090, 8081→9091)
                        int servidorP2pPort = servidorPort + 1010;
                        logger.info("Calculando puerto P2P para comunicación: {} + 1010 = {}", servidorPort, servidorP2pPort);
                        
                        if (interServerService != null && servidorHost != null && servidorP2pPort > 0) {
                            interServerService.forwardChannelAcceptance(servidorHost, servidorP2pPort, idCanal, idUsuario, nombreUsuario);
                            logger.info("Aceptación de canal remoto {} notificada a {}:{} (P2P)", idCanal, servidorHost, servidorP2pPort);
                            
                            // Solicitar historial del canal
                            interServerService.requestChannelHistory(servidorHost, servidorP2pPort, idCanal, idUsuario);
                            logger.info("Solicitando historial de canal remoto {} desde {}:{} (P2P)", idCanal, servidorHost, servidorP2pPort);
                            
                            // Programar envío de historial después de recibir respuesta
                            final int idUsuarioFinal = idUsuario;
                            final int idCanalFinal = idCanal;
                            final int finalP2pPort = servidorP2pPort;
                            java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                            scheduler.schedule(() -> {
                                try {
                                    Map<String, Object> historyData = interServerService.getChannelHistoryResponse(idCanalFinal, idUsuarioFinal, servidorHost, finalP2pPort);
                                    if (historyData != null) {
                                        com.google.gson.JsonArray mensajesJson = (com.google.gson.JsonArray) historyData.get("mensajes");
                                        if (mensajesJson != null && mensajesJson.size() > 0) {
                                            logger.info("Recibidos {} mensajes de historial de canal remoto {} para usuario {}", 
                                                mensajesJson.size(), idCanalFinal, idUsuarioFinal);
                                            
                                            for (com.google.gson.JsonElement elem : mensajesJson) {
                                                try {
                                                    com.google.gson.JsonObject msgRemoto = elem.getAsJsonObject();
                                                    
                                                    // Extraer datos del mensaje
                                                    int idEmisor = msgRemoto.has("idEmisor") ? msgRemoto.get("idEmisor").getAsInt() : 0;
                                                    String contenido = msgRemoto.has("contenido") ? msgRemoto.get("contenido").getAsString() : "";
                                                    String tipoMensaje = msgRemoto.has("tipoMensaje") ? msgRemoto.get("tipoMensaje").getAsString() : "TEXT";
                                                    String transcripcion = msgRemoto.has("transcripcion") ? msgRemoto.get("transcripcion").getAsString() : "";
                                                    
                                                    // IMPORTANTE: Verificar si el mensaje ya existe antes de guardarlo
                                                    if (!mensajeService.mensajeExiste(idEmisor, 0, idCanalFinal, contenido)) {
                                                        // Guardar en BD local
                                                        if ("AUDIO".equals(tipoMensaje)) {
                                                            mensajeService.registrarMensajeAudio(idEmisor, 0, idCanalFinal, "", transcripcion);
                                                        } else {
                                                            mensajeService.registrarMensajeTexto(idEmisor, 0, idCanalFinal, contenido);
                                                        }
                                                        logger.debug("Mensaje de canal remoto guardado: emisor={}, canal={}", idEmisor, idCanalFinal);
                                                    }
                                                    
                                                    // Construir mensaje para enviar al cliente
                                                    JsonObject o = new JsonObject();
                                                    if (msgRemoto.has("id")) o.addProperty("id", msgRemoto.get("id").getAsInt());
                                                    o.addProperty("idEmisor", idEmisor);
                                                    o.addProperty("idCanal", idCanalFinal);
                                                    if (msgRemoto.has("contenido")) o.addProperty("contenido", contenido);
                                                    if (msgRemoto.has("fecha")) o.addProperty("fecha", msgRemoto.get("fecha").getAsString());
                                                    if (msgRemoto.has("transcripcion")) o.addProperty("transcripcion", transcripcion);
                                                    
                                                    // Enviar al cliente
                                                    if ("AUDIO".equals(tipoMensaje)) {
                                                        enviarAUsuario(idUsuarioFinal, new Mensaje(TipoMensaje.MENSAJE_AUDIO, idEmisor, o.toString()));
                                                    } else {
                                                        enviarAUsuario(idUsuarioFinal, new Mensaje(TipoMensaje.MENSAJE_TEXTO, idEmisor, o.toString()));
                                                    }
                                                } catch (Exception ex) {
                                                    logger.warn("Error procesando mensaje de historial de canal remoto: {}", ex.getMessage());
                                                }
                                            }
                                            logger.info("Historial de canal remoto {} guardado y enviado a usuario {}", idCanalFinal, idUsuarioFinal);
                                        }
                                        interServerService.clearChannelHistoryCache(idCanalFinal, idUsuarioFinal);
                                    }
                                } catch (Exception ex) {
                                    logger.error("Error enviando historial de canal remoto: {}", ex.getMessage());
                                } finally {
                                    scheduler.shutdown();
                                }
                            }, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
                        }
                        
                        respuesta.addProperty("exito", true);
                        respuesta.addProperty("mensaje", "Te has unido al canal remoto exitosamente");
                        try {
                            mensajeService.registrarEvento("CANAL_REMOTO_ACEPTADO", "Usuario " + idUsuario + " aceptó invitación a canal remoto " + idCanal, idUsuario, null);
                        } catch (Exception ignored) {}
                    } else {
                        // Canal local
                        logger.info("✓ Procesando aceptación de canal LOCAL");
                        canalService.aceptarSolicitud(idCanal, idUsuario);
                        logger.info("✓ Usuario {} aceptado en canal local {}", idUsuario, idCanal);
                        
                        // Verificar que se agregó correctamente
                        var miembros = canalService.obtenerMiembrosAceptadosConServidor(idCanal);
                        logger.info("✓ Total de miembros aceptados después de aceptación: {}", miembros.size());
                        for (var m : miembros) {
                            logger.info("  - Miembro: ID={} {}", m.getIdUsuario(), m.esLocal() ? "(LOCAL)" : "(REMOTO)");
                        }
                        
                        respuesta.addProperty("exito", true);
                        respuesta.addProperty("mensaje", "Te has unido al canal exitosamente");
                        try {
                            mensajeService.registrarEvento("INVITACION_ACEPTADA", "Usuario " + idUsuario + " aceptó invitación a canal " + idCanal, idUsuario, idCanal);
                        } catch (Exception ignored) {}
                        if (listener != null) {
                            listener.onCanalesChanged();
                            listener.onCanalMiembrosChanged(idCanal);
                            listener.onLogsChanged();
                        }
                    }
                } else {
                    // Rechazar: eliminar la relación pendiente
                    try {
                        canalService.rechazarInvitacion(idCanal, idUsuario);
                        respuesta.addProperty("exito", true);
                        respuesta.addProperty("mensaje", "Invitación rechazada");
                        try { mensajeService.registrarEvento("INVITACION_RECHAZADA", "Usuario " + idUsuario + " rechazó invitación a canal " + idCanal, idUsuario, idCanal); } catch (Exception ignored) {}
                        if (listener != null) { listener.onLogsChanged(); }
                    } catch (Exception e) {
                        respuesta.addProperty("exito", false);
                        respuesta.addProperty("mensaje", "Error al rechazar invitación");
                    }
                }
            }
            
            enviarMensaje(new Mensaje(TipoMensaje.RESPUESTA_CANAL, 0, respuesta.toString()));
        }

        private void procesarRespuestaCanal(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            int idCanal = datos.get("idCanal").getAsInt();
            int idUsuarioSolicitante = datos.get("idUsuario").getAsInt();
            boolean aceptado = datos.get("aceptado").getAsBoolean();
            
            if (aceptado) {
                canalService.aceptarSolicitud(idCanal, idUsuarioSolicitante);
                try { mensajeService.registrarEvento("SOLICITUD_ACEPTADA", "Solicitud de usuario " + idUsuarioSolicitante + " aceptada en canal " + idCanal, idUsuario, idCanal); } catch (Exception ignored) {}
                if (listener != null) { listener.onCanalesChanged(); listener.onCanalMiembrosChanged(idCanal); listener.onLogsChanged(); }
            } else {
                try { mensajeService.registrarEvento("SOLICITUD_RECHAZADA", "Solicitud de usuario " + idUsuarioSolicitante + " rechazada en canal " + idCanal, idUsuario, idCanal); } catch (Exception ignored) {}
                if (listener != null) { listener.onLogsChanged(); }
            }
            
            // Notificar al solicitante
            JsonObject notificacion = new JsonObject();
            notificacion.addProperty("idCanal", idCanal);
            notificacion.addProperty("aceptado", aceptado);
            enviarAUsuario(idUsuarioSolicitante, new Mensaje(TipoMensaje.NOTIFICACION, 0, notificacion.toString()));
        }

        private void procesarMensajeCanal(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            int idCanal = datos.get("idCanal").getAsInt();
            String contenido = datos.get("contenido").getAsString();

            logger.info("╔═══════════════════════════════════════════════════════════════");
            logger.info("║ TRAZABILIDAD MENSAJE CANAL");
            logger.info("╠═══════════════════════════════════════════════════════════════");
            logger.info("║ Canal ID: {}", idCanal);
            logger.info("║ Emisor ID: {}", idUsuario);
            logger.info("║ Contenido: '{}'", contenido);
            logger.info("╚═══════════════════════════════════════════════════════════════");

            // Registrar en log
            mensajeService.registrarMensajeTexto(idUsuario, 0, idCanal, contenido);
            if (listener != null) listener.onLogsChanged();
            logger.info("✓ Mensaje guardado en BD local");
            
            // Obtener información del emisor para mensajes P2P
            var emisor = usuarioService.obtenerUsuarioPorId(idUsuario);
            String nombreEmisor = emisor != null ? emisor.getNombreUsuario() : ("user-" + idUsuario);
            logger.info("✓ Emisor: {} (ID: {})", nombreEmisor, idUsuario);
            
            // Obtener todos los miembros del canal con información del servidor
            var miembrosConServidor = canalService.obtenerMiembrosAceptadosConServidor(idCanal);
            logger.info("✓ Total de miembros aceptados en canal: {}", miembrosConServidor.size());
            
            // Listar todos los miembros
            logger.info("╔═══════════════════════════════════════════════════════════════");
            logger.info("║ MIEMBROS DEL CANAL {}:", idCanal);
            logger.info("╠═══════════════════════════════════════════════════════════════");
            for (var miembro : miembrosConServidor) {
                if (miembro.esLocal()) {
                    logger.info("║  → Usuario LOCAL: ID={}", miembro.getIdUsuario());
                } else {
                    logger.info("║  → Usuario REMOTO: ID={} en servidor {}:{}", 
                        miembro.getIdUsuario(), miembro.getServidorHost(), miembro.getServidorPuerto());
                }
            }
            logger.info("╚═══════════════════════════════════════════════════════════════");
            
            // Preparar mensaje para miembros locales
            JsonObject mensajeReenvio = new JsonObject();
            mensajeReenvio.addProperty("idEmisor", idUsuario);
            mensajeReenvio.addProperty("idCanal", idCanal);
            mensajeReenvio.addProperty("contenido", contenido);
            
            Mensaje mensajeCanal = new Mensaje(TipoMensaje.MENSAJE_CANAL, idUsuario, mensajeReenvio.toString());
            
            // Agrupar servidores remotos para evitar múltiples envíos al mismo servidor
            java.util.Set<String> servidoresRemotosNotificados = new java.util.HashSet<>();
            int localesEnviados = 0;
            int remotosEnviados = 0;
            
            logger.info("╔═══════════════════════════════════════════════════════════════");
            logger.info("║ DISTRIBUCIÓN DEL MENSAJE");
            logger.info("╠═══════════════════════════════════════════════════════════════");
            
            for (var miembro : miembrosConServidor) {
                if (miembro.getIdUsuario() == idUsuario) {
                    // No enviar al emisor
                    logger.info("║  ⊗ OMITIDO (emisor): Usuario ID={}", miembro.getIdUsuario());
                    continue;
                }
                
                if (miembro.esLocal()) {
                    // Usuario local: enviar directamente
                    logger.info("║  ✉ ENVIANDO LOCAL: Usuario ID={}", miembro.getIdUsuario());
                    enviarAUsuario(miembro.getIdUsuario(), mensajeCanal);
                    localesEnviados++;
                } else {
                    // Usuario remoto: reenviar a su servidor via P2P
                    String servidorKey = miembro.getServidorHost() + ":" + miembro.getServidorPuerto();
                    if (!servidoresRemotosNotificados.contains(servidorKey)) {
                        if (interServerService != null) {
                            // CRÍTICO: Necesitamos el puerto P2P del servidor remoto
                            // miembro.getServidorPuerto() devuelve el puerto TCP (8080, 8081)
                            // pero forwardChannelMessage necesita el puerto P2P (9090, 9091)
                            int puertoP2P = miembro.getServidorPuerto() + 1010; // TCP + 1010 = P2P
                            logger.info("║  ✈ ENVIANDO P2P: Host={} Puerto TCP={} -> P2P={} (para usuario ID={})", 
                                miembro.getServidorHost(), miembro.getServidorPuerto(), puertoP2P, miembro.getIdUsuario());
                            interServerService.forwardChannelMessage(
                                miembro.getServidorHost(), 
                                puertoP2P,  // ← USAR PUERTO P2P, NO TCP
                                idCanal, 
                                idUsuario, 
                                nombreEmisor, 
                                contenido
                            );
                            servidoresRemotosNotificados.add(servidorKey);
                            remotosEnviados++;
                        } else {
                            logger.warn("║  ✗ ERROR: InterServerService es NULL - no se puede enviar a servidor remoto");
                        }
                    } else {
                        logger.info("║  ⊗ OMITIDO (ya enviado a servidor): Usuario ID={} en {}:{}", 
                            miembro.getIdUsuario(), miembro.getServidorHost(), miembro.getServidorPuerto());
                    }
                }
            }
            
            logger.info("╠═══════════════════════════════════════════════════════════════");
            logger.info("║ RESUMEN:");
            logger.info("║  • Mensajes locales enviados: {}", localesEnviados);
            logger.info("║  • Servidores remotos notificados: {}", remotosEnviados);
            logger.info("╚═══════════════════════════════════════════════════════════════");
        }

        private void procesarSolicitudLista(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            String tipoLista = datos.get("tipo").getAsString();
            
            JsonObject respuesta = new JsonObject();
            
            switch (tipoLista) {
                case "usuarios" -> {
                    var usuarios = usuarioService.obtenerTodosLosUsuarios();
                    respuesta.addProperty("tipo", "usuarios");
                    respuesta.add("datos", gson.toJsonTree(usuarios));
                }
                case "conectados" -> {
                    var conectados = obtenerUsuariosConectados();
                    respuesta.addProperty("tipo", "conectados");
                    // Enriquecer con foto y agregar usuarios remotos anunciados por P2P
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (com.universidad.chat.servidor.model.Usuario u : conectados) {
                        com.google.gson.JsonObject ju = new com.google.gson.JsonObject();
                        ju.addProperty("id", u.getId());
                        ju.addProperty("nombreUsuario", u.getNombreUsuario());
                        String host = u.getServidorHost()!=null?u.getServidorHost():servidorConfig.getServerHost();
                        int srvPort = u.getServidorPuerto()!=null?u.getServidorPuerto():servidorConfig.getServerPort();
                        ju.addProperty("servidorHost", host);
                        ju.addProperty("servidorPort", srvPort);
                        // opcional: incluir también p2pPort local para depuración
                        ju.addProperty("servidorP2pPort", servidorConfig.getP2pPort());
                        if (u.getFoto() != null && !u.getFoto().isBlank()) {
                            try {
                                byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(u.getFoto()));
                                String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                                ju.addProperty("fotoBase64", b64);
                            } catch (Exception ignored) {}
                        }
                        String key = u.getNombreUsuario()+"@"+host+":"+srvPort;
                        if (seen.add(key)) arr.add(ju);
                    }
                    respuesta.add("datos", arr);
                }
                case "canales" -> {
                    // Devolver solo los canales donde el usuario es miembro aceptado
                    var canales = canalService.obtenerCanalesDeUsuario(idUsuario);
                    respuesta.addProperty("tipo", "canales");
                    respuesta.add("datos", gson.toJsonTree(canales));
                }
                case "miembros_canal" -> {
                    // Devolver miembros y pendientes de un canal específico con información del servidor
                    int idCanal = datos.get("idCanal").getAsInt();
                    
                    logger.info("╔═══════════════════════════════════════════════════════════════");
                    logger.info("║ SOLICITUD DE MIEMBROS DE CANAL");
                    logger.info("╠═══════════════════════════════════════════════════════════════");
                    logger.info("║ Canal ID: {}", idCanal);
                    logger.info("║ Solicitante: Usuario ID={}", idUsuario);
                    
                    // Obtener miembros con información del servidor
                    var miembrosConServidor = canalService.obtenerMiembrosConServidor(idCanal);
                    logger.info("║ Total de miembros encontrados en BD: {}", miembrosConServidor.size());
                    
                    // Separar en miembros aceptados y pendientes
                    com.google.gson.JsonArray miembrosArray = new com.google.gson.JsonArray();
                    com.google.gson.JsonArray pendientesArray = new com.google.gson.JsonArray();
                    
                    logger.info("╠═══════════════════════════════════════════════════════════════");
                    logger.info("║ PROCESANDO MIEMBROS:");
                    
                    for (var miembro : miembrosConServidor) {
                        com.google.gson.JsonObject miembroJson = new com.google.gson.JsonObject();
                        miembroJson.addProperty("idUsuario", miembro.getIdUsuario());
                        miembroJson.addProperty("esLocal", miembro.esLocal());
                        
                        if (miembro.esRemoto()) {
                            miembroJson.addProperty("servidorHost", miembro.getServidorHost());
                            miembroJson.addProperty("servidorPuerto", miembro.getServidorPuerto());
                            logger.info("║  → Usuario ID={} (REMOTO) en {}:{} - Estado: {}", 
                                miembro.getIdUsuario(), 
                                miembro.getServidorHost(), 
                                miembro.getServidorPuerto(),
                                miembro.isAceptado() ? "ACEPTADO" : "PENDIENTE");
                        } else {
                            // Obtener información adicional del usuario si es local
                            var usuario = usuarioService.obtenerUsuarioPorId(miembro.getIdUsuario());
                            if (usuario != null) {
                                miembroJson.addProperty("nombreUsuario", usuario.getNombreUsuario());
                                logger.info("║  → Usuario ID={} (LOCAL) {} - Estado: {}", 
                                    miembro.getIdUsuario(), 
                                    usuario.getNombreUsuario(),
                                    miembro.isAceptado() ? "ACEPTADO" : "PENDIENTE");
                            } else {
                                logger.info("║  → Usuario ID={} (LOCAL) [nombre no disponible] - Estado: {}", 
                                    miembro.getIdUsuario(),
                                    miembro.isAceptado() ? "ACEPTADO" : "PENDIENTE");
                            }
                        }
                        
                        if (miembro.isAceptado()) {
                            miembrosArray.add(miembroJson);
                        } else {
                            pendientesArray.add(miembroJson);
                        }
                    }
                    
                    logger.info("╠═══════════════════════════════════════════════════════════════");
                    logger.info("║ RESUMEN:");
                    logger.info("║  • Miembros aceptados: {}", miembrosArray.size());
                    logger.info("║  • Miembros pendientes: {}", pendientesArray.size());
                    logger.info("╠═══════════════════════════════════════════════════════════════");
                    logger.info("║ JSON ENVIADO AL CLIENTE:");
                    logger.info("║  Miembros aceptados: {}", miembrosArray.toString());
                    logger.info("║  Miembros pendientes: {}", pendientesArray.toString());
                    logger.info("╚═══════════════════════════════════════════════════════════════");
                    
                    respuesta.addProperty("tipo", "miembros_canal");
                    respuesta.add("miembros", miembrosArray);
                    respuesta.add("pendientes", pendientesArray);
                }
                case "historial_usuario" -> {
                    // Devolver historial con audioData embebido cuando aplique
                    int idOtroUsuario = datos.get("idUsuario").getAsInt();
                    var mensajes = mensajeService.obtenerHistorialUsuarios(idUsuario, idOtroUsuario);
                    respuesta.addProperty("tipo", "historial_usuario");
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (Object m : mensajes) {
                        com.google.gson.JsonObject jm = gson.toJsonTree(m).getAsJsonObject();
                        try {
                            if (jm.has("archivoAudio")) {
                                String ruta = jm.get("archivoAudio").getAsString();
                                if (ruta != null && !ruta.isBlank()) {
                                    byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(ruta));
                                    String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                                    jm.addProperty("audioData", b64);
                                }
                            }
                        } catch (Exception ignored) {}
                        arr.add(jm);
                    }
                    respuesta.add("mensajes", arr);
                }
                case "historial_canal" -> {
                    // Devolver historial del canal con audioData embebido cuando aplique
                    int idCanal = datos.get("idCanal").getAsInt();
                    var mensajes = mensajeService.obtenerMensajesCanal(idCanal);
                    respuesta.addProperty("tipo", "historial_canal");
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (Object m : mensajes) {
                        com.google.gson.JsonObject jm = gson.toJsonTree(m).getAsJsonObject();
                        try {
                            if (jm.has("archivoAudio")) {
                                String ruta = jm.get("archivoAudio").getAsString();
                                if (ruta != null && !ruta.isBlank()) {
                                    byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(ruta));
                                    String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                                    jm.addProperty("audioData", b64);
                                }
                            }
                        } catch (Exception ignored) {}
                        arr.add(jm);
                    }
                    respuesta.add("mensajes", arr);
                }
            }
            
            enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, respuesta.toString()));
        }

        private void procesarCierreSesion(Mensaje mensaje) throws IOException {
            if (autenticado) {
                usuarioService.desconectarUsuario(idUsuario);
                clientesConectados.remove(idUsuario);
                logger.info("Usuario {} cerró sesión", idUsuario);
                try { mensajeService.registrarEvento("LOGOUT", "Usuario cerró sesión", idUsuario, null); } catch (Exception ignored) {}
                if (listener != null) { listener.onConectadosChanged(); listener.onLogsChanged(); }
                try { if (interServerService != null) interServerService.broadcastUserPresence("", idUsuario, "DISCONNECTED", null, null); } catch (Exception ignored) {}
            }
            cerrarConexion();
        }

        private void enviarMensaje(Mensaje mensaje) throws IOException {
            synchronized (salida) {
                byte[] datos = mensaje.serializar();
                salida.write(datos);
                salida.flush();
            }
        }

        /**
         * Enviar al usuario recién conectado todo su historial previo:
         *  - Conversaciones directas con cada usuario actualmente conectado
         *  - Mensajes de todos los canales a los que pertenece
         */
        private void enviarHistorialAlUsuarioRecienConectado(int idUsuarioConectado) {
            try {
                logger.info("Enviando historial pendiente al usuario {}", idUsuarioConectado);

                // 1) Conversaciones directas con usuarios conectados
                for (Map.Entry<Integer, ManejadorCliente> entry : clientesConectados.entrySet()) {
                    int idOtroUsuario = entry.getKey();
                    if (idOtroUsuario == idUsuarioConectado) continue;

                    List<MensajeLog> mensajes = mensajeService.obtenerHistorialUsuarios(idUsuarioConectado, idOtroUsuario);
                    if (!mensajes.isEmpty()) {
                        logger.debug(" → Enviando {} mensajes entre {} y {}", mensajes.size(), idUsuarioConectado, idOtroUsuario);
                        for (MensajeLog msg : mensajes) {
                            try {
                                JsonObject o = new JsonObject();
                                o.addProperty("id", msg.getId());
                                o.addProperty("idEmisor", msg.getIdEmisor());
                                if (msg.getIdReceptor() > 0) o.addProperty("idReceptor", msg.getIdReceptor());
                                o.addProperty("contenido", msg.getContenido() != null ? msg.getContenido() : "");
                                o.addProperty("fecha", msg.getFecha().toString());

                                if ("AUDIO".equals(msg.getTipoMensaje())) {
                                    o.addProperty("transcripcion", msg.getTranscripcion() != null ? msg.getTranscripcion() : "");
                                    if (msg.getArchivoAudio() != null && !msg.getArchivoAudio().isEmpty()) {
                                        try {
                                            Path p = Path.of("uploads", msg.getArchivoAudio());
                                            if (Files.exists(p)) {
                                                byte[] bytes = Files.readAllBytes(p);
                                                o.addProperty("audioData", Base64.getEncoder().encodeToString(bytes));
                                                o.addProperty("archivoAudio", msg.getArchivoAudio());
                                            }
                                        } catch (Exception ex) {
                                            logger.warn("No se pudo cargar audio {}: {}", msg.getArchivoAudio(), ex.getMessage());
                                        }
                                    }
                                    enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_AUDIO, msg.getIdEmisor(), o.toString()));
                                } else {
                                    enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_TEXTO, msg.getIdEmisor(), o.toString()));
                                }
                            } catch (Exception ex) {
                                logger.warn("Error enviando mensaje de historial al nuevo usuario: {}", ex.getMessage());
                            }
                        }
                    }
                }

                // 2) Mensajes de canales a los que pertenece
                List<com.universidad.chat.servidor.model.Canal> canales = canalService.obtenerCanalesDeUsuario(idUsuarioConectado);
                for (com.universidad.chat.servidor.model.Canal canal : canales) {
                    List<MensajeLog> mensajesCanal = mensajeService.obtenerMensajesCanal(canal.getId());
                    if (!mensajesCanal.isEmpty()) {
                        logger.debug(" → Enviando {} mensajes del canal {} al usuario {}", mensajesCanal.size(), canal.getId(), idUsuarioConectado);
                        for (MensajeLog msg : mensajesCanal) {
                            try {
                                JsonObject o = new JsonObject();
                                o.addProperty("id", msg.getId());
                                o.addProperty("idEmisor", msg.getIdEmisor());
                                o.addProperty("idCanal", canal.getId());
                                o.addProperty("contenido", msg.getContenido() != null ? msg.getContenido() : "");
                                o.addProperty("fecha", msg.getFecha().toString());
                                if ("AUDIO".equals(msg.getTipoMensaje())) {
                                    o.addProperty("transcripcion", msg.getTranscripcion() != null ? msg.getTranscripcion() : "");
                                    if (msg.getArchivoAudio() != null && !msg.getArchivoAudio().isEmpty()) {
                                        try {
                                            Path p = Path.of("uploads", msg.getArchivoAudio());
                                            if (Files.exists(p)) {
                                                byte[] bytes = Files.readAllBytes(p);
                                                o.addProperty("audioData", Base64.getEncoder().encodeToString(bytes));
                                                o.addProperty("archivoAudio", msg.getArchivoAudio());
                                            }
                                        } catch (Exception ex) {
                                            logger.warn("No se pudo cargar audio de canal {}: {}", msg.getArchivoAudio(), ex.getMessage());
                                        }
                                    }
                                    enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_AUDIO, msg.getIdEmisor(), o.toString()));
                                } else {
                                    enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_TEXTO, msg.getIdEmisor(), o.toString()));
                                }
                            } catch (Exception ex) {
                                logger.warn("Error enviando mensaje de canal al nuevo usuario: {}", ex.getMessage());
                            }
                        }
                    }
                }

                // 3) NUEVO: Solicitar historial de otros servidores P2P
                if (interServerService != null && servidorConfig.isP2pEnabled()) {
                    logger.info("Solicitando historial cross-server para usuario {}", idUsuarioConectado);
                    
                    // Solicitar historial a todos los servidores P2P
                    interServerService.requestUserHistoryFromAllPeers(idUsuarioConectado);
                    
                    // Programar procesamiento de respuestas después de 1.5 segundos (async)
                    java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                    scheduler.schedule(() -> {
                        try {
                            procesarHistorialRemoto(idUsuarioConectado);
                        } catch (Exception ex) {
                            logger.error("Error procesando historial remoto diferido: {}", ex.getMessage());
                        } finally {
                            scheduler.shutdown();
                        }
                    }, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                logger.error("Error enviando historial al usuario recién conectado: {}", e.getMessage(), e);
            }
        }

        /**
         * Procesa y envía historial remoto obtenido de servidores P2P.
         */
        private void procesarHistorialRemoto(int idUsuarioConectado) {
            try {
                // Recuperar y procesar respuestas
                List<Map<String, Object>> allHistoryResponses = interServerService.getAllHistoryResponses(idUsuarioConectado);
                logger.info("Procesando {} respuestas de historial remoto para usuario {}", 
                    allHistoryResponses.size(), idUsuarioConectado);
                
                for (Map<String, Object> historyData : allHistoryResponses) {
                    try {
                        com.google.gson.JsonArray mensajesRemote = (com.google.gson.JsonArray) historyData.get("mensajes");
                        if (mensajesRemote != null) {
                            logger.info("Enviando {} mensajes de historial remoto para usuario {}", 
                                mensajesRemote.size(), idUsuarioConectado);
                            
                            for (com.google.gson.JsonElement elem : mensajesRemote) {
                                try {
                                    com.google.gson.JsonObject msgRemoto = elem.getAsJsonObject();
                                    
                                    // Enviar mensaje al cliente
                                    JsonObject o = new JsonObject();
                                    if (msgRemoto.has("id")) o.addProperty("id", msgRemoto.get("id").getAsInt());
                                    if (msgRemoto.has("idEmisor")) o.addProperty("idEmisor", msgRemoto.get("idEmisor").getAsInt());
                                    if (msgRemoto.has("idReceptor")) o.addProperty("idReceptor", msgRemoto.get("idReceptor").getAsInt());
                                    if (msgRemoto.has("idCanal") && !msgRemoto.get("idCanal").isJsonNull()) 
                                        o.addProperty("idCanal", msgRemoto.get("idCanal").getAsInt());
                                    if (msgRemoto.has("contenido")) o.addProperty("contenido", msgRemoto.get("contenido").getAsString());
                                    if (msgRemoto.has("fecha")) o.addProperty("fecha", msgRemoto.get("fecha").getAsString());
                                    if (msgRemoto.has("transcripcion")) o.addProperty("transcripcion", msgRemoto.get("transcripcion").getAsString());
                                    
                                    String tipoMensaje = msgRemoto.has("tipoMensaje") ? msgRemoto.get("tipoMensaje").getAsString() : "TEXT";
                                    
                                    if ("AUDIO".equals(tipoMensaje)) {
                                        enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_AUDIO, 
                                            msgRemoto.has("idEmisor") ? msgRemoto.get("idEmisor").getAsInt() : 0, 
                                            o.toString()));
                                    } else {
                                        enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_TEXTO, 
                                            msgRemoto.has("idEmisor") ? msgRemoto.get("idEmisor").getAsInt() : 0, 
                                            o.toString()));
                                    }
                                } catch (Exception ex) {
                                    logger.warn("Error procesando mensaje remoto: {}", ex.getMessage());
                                }
                            }
                        }
                        
                        // Procesar canales remotos (información de metadatos)
                        com.google.gson.JsonArray canalesRemote = (com.google.gson.JsonArray) historyData.get("canales");
                        if (canalesRemote != null) {
                            logger.info("Enviando {} canales de historial remoto para usuario {}", 
                                canalesRemote.size(), idUsuarioConectado);
                            
                            for (com.google.gson.JsonElement elem : canalesRemote) {
                                try {
                                    com.google.gson.JsonObject canalRemoto = elem.getAsJsonObject();
                                    
                                    // Enviar notificación de canal remoto al cliente
                                    JsonObject notifCanal = new JsonObject();
                                    notifCanal.addProperty("tipo", "CANAL_REMOTO");
                                    if (canalRemoto.has("id")) notifCanal.addProperty("id", canalRemoto.get("id").getAsInt());
                                    if (canalRemoto.has("nombre")) notifCanal.addProperty("nombre", canalRemoto.get("nombre").getAsString());
                                    if (canalRemoto.has("idCreador")) notifCanal.addProperty("idCreador", canalRemoto.get("idCreador").getAsInt());
                                    if (canalRemoto.has("esPrivado")) notifCanal.addProperty("esPrivado", canalRemoto.get("esPrivado").getAsBoolean());
                                    
                                    enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, notifCanal.toString()));
                                } catch (Exception ex) {
                                    logger.warn("Error procesando canal remoto: {}", ex.getMessage());
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("Error procesando respuesta de historial remoto: {}", ex.getMessage());
                    }
                }
                
                // Limpiar cache de historial
                interServerService.clearHistoryCache(idUsuarioConectado);
            } catch (Exception e) {
                logger.error("Error procesando historial remoto para usuario {}: {}", idUsuarioConectado, e.getMessage());
            }
        }

        private void enviarError(String mensajeError) throws IOException {
            JsonObject error = new JsonObject();
            error.addProperty("error", true);
            error.addProperty("mensaje", mensajeError);
            enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, error.toString()));
        }

        private void cerrarConexion() {
            if (autenticado && idUsuario > 0) {
                usuarioService.desconectarUsuario(idUsuario);
                clientesConectados.remove(idUsuario);
                if (listener != null) listener.onUserDisconnected(idUsuario);
                try { mensajeService.registrarEvento("DESCONEXION", "Usuario desconectado", idUsuario, null); } catch (Exception ignored) {}
                if (listener != null) { listener.onConectadosChanged(); listener.onLogsChanged(); }
                try { if (interServerService != null) interServerService.broadcastUserPresence("", idUsuario, "DISCONNECTED", null, null); } catch (Exception ignored) {}
                
                // Notificar a todos los clientes conectados que un usuario se desconectó
                JsonObject notifDesconexion = new JsonObject();
                notifDesconexion.addProperty("tipo", "USER_DISCONNECTED");
                notifDesconexion.addProperty("idUsuario", idUsuario);
                difundirATodos(new Mensaje(TipoMensaje.NOTIFICACION, 0, notifDesconexion.toString()));
            }
            
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                logger.error("Error cerrando socket", e);
            }
        }
    }
}
