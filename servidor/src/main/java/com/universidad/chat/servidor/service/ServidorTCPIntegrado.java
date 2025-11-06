package com.universidad.chat.servidor.service;

import com.universidad.chat.servidor.model.Usuario;
import com.universidad.chat.servidor.model.MensajeLog;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import com.universidad.chat.servidor.util.adapter.LocalDateTimeAdapter;

/**
 * Servidor TCP completamente integrado con servicios de negocio.
 * Aplica patrón Object Pool para gestionar hilos.
 */
public class ServidorTCPIntegrado {
    private static final Logger logger = LoggerFactory.getLogger(ServidorTCPIntegrado.class);
    private static final int PUERTO = 8080;
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
    }

    public void setListener(ServidorEventListener listener) { this.listener = listener; }

    public void iniciar() throws IOException {
        serverSocket = new ServerSocket(PUERTO);
        ejecutando = true;
    logger.info("Servidor TCP iniciado en puerto {}", PUERTO);
    if (listener != null) listener.onServerStarted(PUERTO);

        while (ejecutando) {
            try {
                Socket clienteSocket = serverSocket.accept();
                logger.info("Nueva conexión desde: {}", clienteSocket.getInetAddress());
                if (listener != null) listener.onClientSocketConnected(clienteSocket.getInetAddress().toString());
                
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
        } catch (IOException e) {
            logger.error("Error cerrando servidor", e);
        }
        if (listener != null) listener.onServerStopped();
    }

    // ==== Métodos para UI ====
    public java.util.List<com.universidad.chat.servidor.model.Usuario> obtenerUsuariosConectados() {
        return usuarioService.obtenerUsuariosConectados();
    }

    public java.util.List<com.universidad.chat.servidor.model.Usuario> obtenerTodosUsuarios() {
        return usuarioService.obtenerTodosLosUsuarios();
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
                    byte tipoCodigo = entrada.readByte();
                    int longitudCuerpo = entrada.readInt();
                    int idUsuarioMensaje = entrada.readInt();

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
                logger.info("Cliente desconectado: {}", socket.getInetAddress());
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
                try { mensajeService.registrarEvento("LOGIN", "Usuario autenticado: " + nombreUsuario, idUsuario, null); } catch (Exception ignored) {}
                if (listener != null) { listener.onConectadosChanged(); listener.onLogsChanged(); }
                
                // Notificar a todos los clientes conectados que un nuevo usuario se conectó
                JsonObject notifConexion = new JsonObject();
                notifConexion.addProperty("tipo", "USER_CONNECTED");
                notifConexion.addProperty("idUsuario", idUsuario);
                notifConexion.addProperty("nombreUsuario", nombreUsuario);
                difundirATodos(new Mensaje(TipoMensaje.NOTIFICACION, 0, notifConexion.toString()));

                // NUEVO: Enviar historial pendiente AL USUARIO RECIÉN CONECTADO
                enviarHistorialAlUsuarioRecienConectado(idUsuario);
            } else {
                respuesta.addProperty("exito", false);
                respuesta.addProperty("mensaje", "Credenciales inválidas");
            }
            
            enviarMensaje(new Mensaje(TipoMensaje.NOTIFICACION, 0, respuesta.toString()));
        }

        private void procesarMensajeTexto(Mensaje mensaje) throws IOException {
            if (!autenticado) {
                enviarError("Debe autenticarse primero");
                return;
            }

            JsonObject datos = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
            int idReceptor = datos.get("idReceptor").getAsInt();
            String contenido = datos.get("contenido").getAsString();

            // Registrar en log
            int idMensaje = mensajeService.registrarMensajeTexto(idUsuario, idReceptor, null, contenido);
            if (listener != null) listener.onLogsChanged();
            
            // Enviar al destinatario
            JsonObject mensajeReenvio = new JsonObject();
            mensajeReenvio.addProperty("idEmisor", idUsuario);
            mensajeReenvio.addProperty("idReceptor", idReceptor);
            if (idMensaje > 0) mensajeReenvio.addProperty("id", idMensaje);
            mensajeReenvio.addProperty("contenido", contenido);
            
            enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_TEXTO, idUsuario, mensajeReenvio.toString()));
            
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
                // Enviar al destinatario individual (incluye los bytes del audio para reproducción)
                JsonObject mensajeReenvio = new JsonObject();
                mensajeReenvio.addProperty("idEmisor", idUsuario);
                mensajeReenvio.addProperty("idReceptor", idReceptor);
                if (idMensaje > 0) mensajeReenvio.addProperty("id", idMensaje);
                mensajeReenvio.addProperty("archivoAudio", rutaArchivo);
                mensajeReenvio.addProperty("audioData", audioBase64); // Enviar bytes para reproducción
                mensajeReenvio.addProperty("transcripcion", transcripcion);
                enviarAUsuario(idReceptor, new Mensaje(TipoMensaje.MENSAJE_AUDIO, idUsuario, mensajeReenvio.toString()));
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
                boolean exito = canalService.invitarUsuario(idCanal, idUsuario, idInvitado);
                respuesta.addProperty("exito", exito);
                respuesta.addProperty("mensaje", exito ? "Usuario invitado al canal" : "No fue posible invitar al usuario");
                if (exito) {
                    // Obtener información del canal para la notificación
                    var canal = canalService.obtenerCanalPorId(idCanal);
                    var invitador = usuarioService.obtenerUsuarioPorId(idUsuario);
                    // Notificar al usuario invitado con opción de aceptar/rechazar
                    JsonObject notificacion = new JsonObject();
                    notificacion.addProperty("tipo", "INVITACION_CANAL");
                    notificacion.addProperty("idCanal", idCanal);
                    notificacion.addProperty("nombreCanal", canal != null ? canal.getNombre() : "Canal");
                    notificacion.addProperty("idInvitador", idUsuario);
                    notificacion.addProperty("nombreInvitador", invitador != null ? invitador.getNombreUsuario() : "Usuario");
                    enviarAUsuario(idInvitado, new Mensaje(TipoMensaje.NOTIFICACION, 0, notificacion.toString()));
                    // Registrar evento
                    try { mensajeService.registrarEvento("INVITACION_ENVIADA", "Usuario " + idInvitado + " invitado al canal " + idCanal + " por " + idUsuario, idUsuario, idCanal); } catch (Exception ignored) {}
                    if (listener != null) { listener.onLogsChanged(); }
                }
            } else if ("responder_invitacion".equals(accion)) {
                int idCanal = datos.get("idCanal").getAsInt();
                boolean aceptar = datos.get("aceptar").getAsBoolean();
                
                if (aceptar) {
                    canalService.aceptarSolicitud(idCanal, idUsuario);
                    respuesta.addProperty("exito", true);
                    respuesta.addProperty("mensaje", "Te has unido al canal exitosamente");
                    try { mensajeService.registrarEvento("INVITACION_ACEPTADA", "Usuario " + idUsuario + " aceptó invitación a canal " + idCanal, idUsuario, idCanal); } catch (Exception ignored) {}
                    if (listener != null) { listener.onCanalesChanged(); listener.onCanalMiembrosChanged(idCanal); listener.onLogsChanged(); }
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

            // Registrar en log
            mensajeService.registrarMensajeTexto(idUsuario, 0, idCanal, contenido);
            if (listener != null) listener.onLogsChanged();
            
            // Enviar a todos los miembros del canal
            var miembros = canalService.obtenerMiembrosCanal(idCanal);
            JsonObject mensajeReenvio = new JsonObject();
            mensajeReenvio.addProperty("idEmisor", idUsuario);
            mensajeReenvio.addProperty("idCanal", idCanal);
            mensajeReenvio.addProperty("contenido", contenido);
            
            Mensaje mensajeCanal = new Mensaje(TipoMensaje.MENSAJE_CANAL, idUsuario, mensajeReenvio.toString());
            for (int idMiembro : miembros) {
                if (idMiembro != idUsuario) {
                    enviarAUsuario(idMiembro, mensajeCanal);
                }
            }
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
                    var conectados = usuarioService.obtenerUsuariosConectados();
                    respuesta.addProperty("tipo", "conectados");
                    // Enriquecer con foto en Base64 para que el cliente pueda mostrar avatar
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (com.universidad.chat.servidor.model.Usuario u : conectados) {
                        com.google.gson.JsonObject ju = new com.google.gson.JsonObject();
                        ju.addProperty("id", u.getId());
                        ju.addProperty("nombreUsuario", u.getNombreUsuario());
                        String fotoPath = u.getFoto();
                        if (fotoPath != null && !fotoPath.isBlank()) {
                            try {
                                byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(fotoPath));
                                String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                                ju.addProperty("fotoBase64", b64);
                            } catch (Exception ignored) {}
                        }
                        arr.add(ju);
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
                    // Devolver miembros y pendientes de un canal específico
                    int idCanal = datos.get("idCanal").getAsInt();
                    var miembros = canalService.obtenerMiembrosCanal(idCanal);
                    var pendientes = canalService.obtenerPendientesCanal(idCanal);
                    respuesta.addProperty("tipo", "miembros_canal");
                    respuesta.add("miembros", gson.toJsonTree(miembros));
                    respuesta.add("pendientes", gson.toJsonTree(pendientes));
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
            } catch (Exception e) {
                logger.error("Error enviando historial al usuario recién conectado: {}", e.getMessage(), e);
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
