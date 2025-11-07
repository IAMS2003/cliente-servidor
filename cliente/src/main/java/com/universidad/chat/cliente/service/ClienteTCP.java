package com.universidad.chat.cliente.service;

import com.google.gson.JsonObject;
import com.universidad.chat.cliente.util.Mensaje;
import com.universidad.chat.cliente.util.TipoMensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cliente TCP que se conecta al servidor con callbacks y helpers de protocolo.
 */
public class ClienteTCP {
    private static final Logger logger = LoggerFactory.getLogger(ClienteTCP.class);
    private String host = "localhost";
    private int puerto = 8080;
    public String getHost() { return host; }
    public int getPuerto() { return puerto; }

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream salida;
    private volatile boolean conectado;

    public interface Listener {
        void onMensaje(Mensaje mensaje);
        default void onError(Exception e) {}
        default void onDesconexion() {}
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public ClienteTCP() {}

    public ClienteTCP(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public void conectar() throws IOException {
        socket = new Socket(host, puerto);
        entrada = new DataInputStream(socket.getInputStream());
        salida = new DataOutputStream(socket.getOutputStream());
        conectado = true;
        logger.info("Conectado al servidor en {}:{}", host, puerto);

    Thread lector = new Thread(this::escucharMensajes, "cliente-lector");
    // Hacerlo daemon para que no impida salir de la JVM si la ventana se cierra
    lector.setDaemon(true);
    lector.start();
    }

    public synchronized void enviarMensaje(Mensaje mensaje) throws IOException {
        if (!conectado) throw new IOException("No conectado al servidor");
        byte[] datos = mensaje.serializar();
        salida.write(datos);
        salida.flush();
        logger.debug("Mensaje enviado: {}", mensaje.getTipo());
    }

    private void escucharMensajes() {
        try {
            while (conectado) {
                byte version = entrada.readByte();
                byte tipoCodigo = entrada.readByte();
                int longitudCuerpo = entrada.readInt();
                int idUsuario = entrada.readInt();

                byte[] cuerpo = new byte[longitudCuerpo];
                entrada.readFully(cuerpo);

                Mensaje mensaje = new Mensaje(TipoMensaje.fromCodigo(tipoCodigo), idUsuario, cuerpo);
                for (Listener l : listeners) {
                    try { l.onMensaje(mensaje); } catch (Exception ignored) {}
                }
            }
        } catch (EOFException e) {
            logger.info("Servidor cerró la conexión");
        } catch (IOException e) {
            if (conectado) {
                logger.error("Error recibiendo mensajes", e);
                for (Listener l : listeners) l.onError(e);
            }
        } finally {
            desconectar();
            for (Listener l : listeners) l.onDesconexion();
        }
    }

    public void desconectar() {
        conectado = false;
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) { logger.error("Error cerrando conexión", e); }
    }

    public boolean isConectado() { return conectado; }

    // Helpers de protocolo
    public void enviarRegistro(String nombreUsuario, String email, String contrasena, String fotoBase64) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("nombreUsuario", nombreUsuario);
        body.addProperty("email", email);
        body.addProperty("contrasena", contrasena);
        if (fotoBase64 != null) body.addProperty("foto", fotoBase64);
        enviarMensaje(new Mensaje(TipoMensaje.REGISTRO, 0, body.toString()));
    }

    public void enviarAutenticacion(String nombreUsuario, String contrasena) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("nombreUsuario", nombreUsuario);
        body.addProperty("contrasena", contrasena);
        enviarMensaje(new Mensaje(TipoMensaje.AUTENTICACION, 0, body.toString()));
    }

    public void enviarMensajeTextoAUsuario(int idEmisor, int idReceptor, String contenido) throws IOException {
        enviarMensajeTextoAUsuario(idEmisor, idReceptor, contenido, null, null);
    }

    public void enviarMensajeTextoAUsuario(int idEmisor, int idReceptor, String contenido, String servidorHost, Integer servidorP2pPort) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("idReceptor", idReceptor);
        body.addProperty("contenido", contenido);
        if (servidorHost != null && !servidorHost.isBlank()) body.addProperty("servidorHost", servidorHost);
        if (servidorP2pPort != null && servidorP2pPort > 0) body.addProperty("servidorP2pPort", servidorP2pPort);
        enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_TEXTO, idEmisor, body.toString()));
    }

    public void enviarMensajeCanal(int idEmisor, int idCanal, String contenido) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("idCanal", idCanal);
        body.addProperty("contenido", contenido);
        enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_CANAL, idEmisor, body.toString()));
    }

    public void crearCanal(String nombre, boolean esPrivado) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accion", "crear");
        body.addProperty("nombre", nombre);
        body.addProperty("esPrivado", esPrivado);
        enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_CANAL, 0, body.toString()));
    }

    public void solicitarUnirseACanal(int idCanal) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accion", "unirse");
        body.addProperty("idCanal", idCanal);
        enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_CANAL, 0, body.toString()));
    }

    public void invitarUsuarioACanal(int idCanal, int idUsuarioInvitado) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accion", "invitar");
        body.addProperty("idCanal", idCanal);
        body.addProperty("idUsuarioInvitado", idUsuarioInvitado);
        enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_CANAL, 0, body.toString()));
    }

    public void responderInvitacionCanal(int idCanal, boolean aceptar) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accion", "responder_invitacion");
        body.addProperty("idCanal", idCanal);
        body.addProperty("aceptar", aceptar);
        enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_CANAL, 0, body.toString()));
    }

    public void responderInvitacionCanalRemoto(int idCanal, boolean aceptar, String nombreCanal, int idCreador, boolean esPrivado, String servidorHost, int servidorP2pPort) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accion", "responder_invitacion");
        body.addProperty("idCanal", idCanal);
        body.addProperty("aceptar", aceptar);
        body.addProperty("nombreCanal", nombreCanal);
        body.addProperty("idCreador", idCreador);
        body.addProperty("esPrivado", esPrivado);
        body.addProperty("servidorHost", servidorHost);
        body.addProperty("servidorP2pPort", servidorP2pPort);
        enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_CANAL, 0, body.toString()));
    }

    public void solicitarLista(String tipo) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", tipo);
        enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_LISTA, 0, body.toString()));
    }

    public void solicitarMiembrosCanal(int idCanal) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", "miembros_canal");
        body.addProperty("idCanal", idCanal);
        enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_LISTA, 0, body.toString()));
    }

    public void cerrarSesion(int idUsuario) throws IOException {
        enviarMensaje(new Mensaje(TipoMensaje.CIERRE_SESION, idUsuario, ""));
    }

    public void enviarArchivoAUsuario(int idEmisor, int idReceptor, String nombre, String base64) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("idReceptor", idReceptor);
        body.addProperty("nombre", nombre);
        body.addProperty("data", base64);
        enviarMensaje(new Mensaje(TipoMensaje.ENVIO_ARCHIVO, idEmisor, body.toString()));
    }

    public void enviarArchivoACanal(int idEmisor, int idCanal, String nombre, String base64) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("idCanal", idCanal);
        body.addProperty("nombre", nombre);
        body.addProperty("data", base64);
        enviarMensaje(new Mensaje(TipoMensaje.ENVIO_ARCHIVO, idEmisor, body.toString()));
    }

    public void enviarAudioAUsuario(int idEmisor, int idReceptor, byte[] audioData) throws IOException {
        enviarAudioAUsuario(idEmisor, idReceptor, audioData, null, null);
    }

    public void enviarAudioAUsuario(int idEmisor, int idReceptor, byte[] audioData, String servidorHost, Integer servidorP2pPort) throws IOException {
        String audioBase64 = java.util.Base64.getEncoder().encodeToString(audioData);
        JsonObject body = new JsonObject();
        body.addProperty("idReceptor", idReceptor);
        body.addProperty("audioData", audioBase64);
        if (servidorHost != null && !servidorHost.isBlank()) body.addProperty("servidorHost", servidorHost);
        if (servidorP2pPort != null && servidorP2pPort > 0) body.addProperty("servidorP2pPort", servidorP2pPort);
        enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_AUDIO, idEmisor, body.toString()));
    }

    public void enviarAudioACanal(int idEmisor, int idCanal, byte[] audioData) throws IOException {
        String audioBase64 = java.util.Base64.getEncoder().encodeToString(audioData);
        JsonObject body = new JsonObject();
        body.addProperty("idCanal", idCanal);
        body.addProperty("audioData", audioBase64);
        enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_AUDIO, idEmisor, body.toString()));
    }
}

