package com.universidad.chat.cliente.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.universidad.chat.cliente.util.Mensaje;
import com.universidad.chat.cliente.util.TipoMensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Servicio de alto nivel que envuelve ClienteTCP y provee una API asíncrona basada en CompletableFuture.
 * Gestiona la sesión (idUsuario) y correlaciona respuestas usando predicados por tipo/contenido.
 */
public class ClienteProtocoloService implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(ClienteProtocoloService.class);

    private final ClienteTCP cliente;
    private final Gson gson = new Gson();
    private final List<Waiter> waiters = java.util.Collections.synchronizedList(new LinkedList<>());
    private volatile Integer idUsuario;
    private volatile boolean conectado = false;
    public interface EventosCliente {
        void onServidorDetenido(String mensaje);
        default void onBroadcast(String mensaje) {}
        default void onMensajeUsuario(int idEmisor, String contenido, JsonObject raw) {}
        default void onMensajeCanal(int idCanal, int idEmisor, String contenido, JsonObject raw) {}
        default void onAudioUsuario(int idEmisor, String archivoAudio, String transcripcion, String audioData, JsonObject raw) {}
        default void onAudioCanal(int idCanal, int idEmisor, String archivoAudio, String transcripcion, String audioData, JsonObject raw) {}
        default void onArchivoUsuario(int idEmisor, String nombre, JsonObject raw) {}
        default void onArchivoCanal(int idCanal, int idEmisor, String nombre, JsonObject raw) {}
        default void onNotificacion(JsonObject notificacion) {}
        default void onUserConnectionChange() {}
    }
    private volatile EventosCliente eventos;

    public ClienteProtocoloService() {
        this("localhost", 8080);
    }

    public ClienteProtocoloService(String host, int port) {
        this.cliente = new ClienteTCP(host, port);
        this.cliente.addListener(new ClienteTCP.Listener() {
            @Override
            public void onMensaje(Mensaje mensaje) {
                // Interceptar apagado de servidor
                if (mensaje.getTipo() == TipoMensaje.NOTIFICACION) {
                    try {
                        JsonObject o = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
                        // Detectar desconexión forzada por el servidor
                        if (o != null && o.has("desconexion_forzada") && o.get("desconexion_forzada").getAsBoolean()) {
                            String contenido = o.has("contenido") ? o.get("contenido").getAsString() : "El servidor te ha desconectado";
                            // Notificar UI y cerrar sesión
                            try { if (eventos != null) eventos.onServidorDetenido(contenido); } catch (Exception ignored) {}
                            desconectar();
                            return;
                        }
                        if (o != null && o.has("broadcast") && o.get("broadcast").getAsBoolean()) {
                            String contenido = o.has("contenido") ? o.get("contenido").getAsString() : "";
                            if (contenido != null && contenido.toLowerCase().contains("servidor detenido")) {
                                // Notificar UI y cerrar sesión
                                try { if (eventos != null) eventos.onServidorDetenido(contenido); } catch (Exception ignored) {}
                                desconectar();
                                return;
                            }
                            // Broadcast general
                            try { if (eventos != null) eventos.onBroadcast(contenido); } catch (Exception ignored) {}
                        } else if (o != null && o.has("tipo")) {
                            String tipo = o.get("tipo").getAsString();
                            if ("USER_CONNECTED".equals(tipo) || "USER_DISCONNECTED".equals(tipo)) {
                                // Notificar cambio en lista de conectados
                                try { if (eventos != null) eventos.onUserConnectionChange(); } catch (Exception ignored) {}
                                return;
                            }
                        }
                        if (eventos != null && o != null) {
                            try { eventos.onNotificacion(o); } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
                // Mensajes entrantes a usuario o canal
                if (eventos != null) {
                    try {
                        if (mensaje.getTipo() == TipoMensaje.MENSAJE_TEXTO) {
                            JsonObject o = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
                            String contenido = o.has("contenido") ? o.get("contenido").getAsString() : mensaje.getCuerpoTexto();
                            int emisor = mensaje.getIdUsuario();
                            eventos.onMensajeUsuario(emisor, contenido, o);
                        } else if (mensaje.getTipo() == TipoMensaje.MENSAJE_CANAL) {
                            JsonObject o = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
                            String contenido = o.has("contenido") ? o.get("contenido").getAsString() : mensaje.getCuerpoTexto();
                            int idCanal = o.has("idCanal") ? o.get("idCanal").getAsInt() : -1;
                            int emisor = mensaje.getIdUsuario();
                            eventos.onMensajeCanal(idCanal, emisor, contenido, o);
                        } else if (mensaje.getTipo() == TipoMensaje.MENSAJE_AUDIO) {
                            JsonObject o = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
                            int emisor = mensaje.getIdUsuario();
                            String archivoAudio = o.has("archivoAudio") ? o.get("archivoAudio").getAsString() : "";
                            String transcripcion = o.has("transcripcion") ? o.get("transcripcion").getAsString() : "";
                            String audioData = o.has("audioData") ? o.get("audioData").getAsString() : "";
                            if (o.has("idCanal")) {
                                int idCanal = o.get("idCanal").getAsInt();
                                eventos.onAudioCanal(idCanal, emisor, archivoAudio, transcripcion, audioData, o);
                            } else {
                                eventos.onAudioUsuario(emisor, archivoAudio, transcripcion, audioData, o);
                            }
                        } else if (mensaje.getTipo() == TipoMensaje.ENVIO_ARCHIVO) {
                            JsonObject o = gson.fromJson(mensaje.getCuerpoTexto(), JsonObject.class);
                            String nombre = o.has("nombre") ? o.get("nombre").getAsString() : "archivo";
                            int emisor = mensaje.getIdUsuario();
                            if (o.has("idCanal")) {
                                int idCanal = o.get("idCanal").getAsInt();
                                eventos.onArchivoCanal(idCanal, emisor, nombre, o);
                            } else if (o.has("idReceptor")) {
                                eventos.onArchivoUsuario(emisor, nombre, o);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                despacharMensaje(mensaje);
            }

            @Override
            public void onError(Exception e) {
                logger.error("Error en ClienteTCP", e);
            }

            @Override
            public void onDesconexion() {
                logger.info("Cliente desconectado");
            }
        });
    }

    public synchronized void conectar() throws Exception {
        if (!conectado) {
            cliente.conectar();
            conectado = true;
        }
    }

    public synchronized void desconectar() {
        if (conectado) {
            try { cliente.desconectar(); } catch (Exception ignored) {}
            conectado = false;
        }
    }

    public Integer getIdUsuario() { return idUsuario; }
    public void setEventos(EventosCliente eventos) { this.eventos = eventos; }

    // ============ API de alto nivel ============

    public CompletableFuture<JsonObject> registrar(String nombreUsuario, String email, String contrasena, String direccionIP) {
        CompletableFuture<JsonObject> f = new CompletableFuture<>();
        f.completeExceptionally(new UnsupportedOperationException("El registro de usuarios solo puede realizarlo el servidor/administrador"));
        return f;
    }

    public CompletableFuture<JsonObject> autenticar(String nombreUsuario, String contrasena) {
        try {
            cliente.enviarAutenticacion(nombreUsuario, contrasena);
            return esperarNotificacion(Duration.ofSeconds(5)).thenApply(json -> {
                if (json.has("id")) {
                    idUsuario = json.get("id").getAsInt();
                }
                return json;
            });
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> crearCanal(String nombre, boolean esPrivado) {
        try {
            cliente.crearCanal(nombre, esPrivado);
            return esperarRespuestaCanal(Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> solicitarUnirseACanal(int idCanal) {
        try {
            cliente.solicitarUnirseACanal(idCanal);
            return esperarRespuestaCanal(Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> invitarUsuarioACanal(int idCanal, int idUsuarioInvitado) {
        try {
            cliente.invitarUsuarioACanal(idCanal, idUsuarioInvitado);
            return esperarRespuestaCanal(Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> responderInvitacionCanal(int idCanal, boolean aceptar) {
        try {
            cliente.responderInvitacionCanal(idCanal, aceptar);
            return esperarRespuestaCanal(Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> solicitarLista(String tipo) {
        try {
            cliente.solicitarLista(tipo);
            return esperarNotificacion(m -> {
                try {
                    JsonObject o = gson.fromJson(m.getCuerpoTexto(), JsonObject.class);
                    return o != null && o.has("tipo") && Objects.equals(tipo, o.get("tipo").getAsString());
                } catch (Exception e) { return false; }
            }, Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    // Helpers de listas para UI
    public CompletableFuture<List<JsonObject>> listarUsuarios() {
        return solicitarLista("usuarios").thenApply(o -> toList(o, "datos"));
    }
    public CompletableFuture<List<JsonObject>> listarCanales() {
        return solicitarLista("canales").thenApply(o -> toList(o, "datos"));
    }
    public CompletableFuture<List<JsonObject>> listarUsuariosConectados() {
        return solicitarLista("conectados").thenApply(o -> toList(o, "datos"));
    }

    public CompletableFuture<JsonObject> solicitarMiembrosCanal(int idCanal) {
        try {
            cliente.solicitarMiembrosCanal(idCanal);
            return esperarNotificacion(m -> {
                try {
                    JsonObject o = gson.fromJson(m.getCuerpoTexto(), JsonObject.class);
                    return o != null && o.has("tipo") && "miembros_canal".equals(o.get("tipo").getAsString());
                } catch (Exception e) { return false; }
            }, Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<List<JsonObject>> solicitarHistorialUsuario(int idOtroUsuario) {
        try {
            JsonObject solicitud = new JsonObject();
            solicitud.addProperty("tipo", "historial_usuario");
            solicitud.addProperty("idUsuario", idOtroUsuario);
            cliente.enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_LISTA, idUsuario, solicitud.toString()));
            
            return esperarNotificacion(m -> {
                try {
                    JsonObject o = gson.fromJson(m.getCuerpoTexto(), JsonObject.class);
                    return o != null && o.has("tipo") && "historial_usuario".equals(o.get("tipo").getAsString());
                } catch (Exception e) { return false; }
            }, Duration.ofSeconds(5)).thenApply(o -> toList(o, "mensajes"));
        } catch (IOException e) {
            CompletableFuture<List<JsonObject>> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<List<JsonObject>> solicitarHistorialCanal(int idCanal) {
        try {
            JsonObject solicitud = new JsonObject();
            solicitud.addProperty("tipo", "historial_canal");
            solicitud.addProperty("idCanal", idCanal);
            cliente.enviarMensaje(new Mensaje(TipoMensaje.SOLICITUD_LISTA, idUsuario, solicitud.toString()));
            
            return esperarNotificacion(m -> {
                try {
                    JsonObject o = gson.fromJson(m.getCuerpoTexto(), JsonObject.class);
                    return o != null && o.has("tipo") && "historial_canal".equals(o.get("tipo").getAsString());
                } catch (Exception e) { return false; }
            }, Duration.ofSeconds(5)).thenApply(o -> toList(o, "mensajes"));
        } catch (IOException e) {
            CompletableFuture<List<JsonObject>> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    // Flujo de solicitud/unión/aceptación a canal
    public CompletableFuture<JsonObject> unirseACanal(int idCanal) { return solicitarUnirseACanal(idCanal); }

    public CompletableFuture<JsonObject> crearCanalPublico(String nombre) { return crearCanal(nombre, false); }
    public CompletableFuture<JsonObject> crearCanalPrivado(String nombre) { return crearCanal(nombre, true); }

    private List<JsonObject> toList(JsonObject root, String key) {
        List<JsonObject> list = new LinkedList<>();
        try {
            var arr = root.getAsJsonArray(key);
            if (arr != null) {
                arr.forEach(el -> {
                    try { list.add(el.getAsJsonObject()); } catch (Exception ignored) {}
                });
            }
        } catch (Exception ignored) {}
        return list;
    }

    public CompletableFuture<JsonObject> enviarMensajeATextoUsuario(int idReceptor, String contenido) {
        int emisor = requireIdUsuario();
        try {
            cliente.enviarMensajeTextoAUsuario(emisor, idReceptor, contenido);
            return esperarNotificacion(Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> enviarMensajeACanal(int idCanal, String contenido) {
        int emisor = requireIdUsuario();
        try {
            cliente.enviarMensajeCanal(emisor, idCanal, contenido);
            // Muchos servidores responden con NOTIFICACION de éxito
            return esperarNotificacion(Duration.ofSeconds(5));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> enviarArchivoAUsuario(int idReceptor, String nombreArchivo, String contenidoBase64) {
        int emisor = requireIdUsuario();
        try {
            cliente.enviarArchivoAUsuario(emisor, idReceptor, nombreArchivo, contenidoBase64);
            return esperarNotificacion(Duration.ofSeconds(10));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> enviarArchivoACanal(int idCanal, String nombreArchivo, String contenidoBase64) {
        int emisor = requireIdUsuario();
        try {
            cliente.enviarArchivoACanal(emisor, idCanal, nombreArchivo, contenidoBase64);
            return esperarNotificacion(Duration.ofSeconds(10));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> enviarAudioAUsuario(int idReceptor, byte[] audioData) {
        int emisor = requireIdUsuario();
        try {
            cliente.enviarAudioAUsuario(emisor, idReceptor, audioData);
            return esperarNotificacion(Duration.ofSeconds(10));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<JsonObject> enviarAudioACanal(int idCanal, byte[] audioData) {
        int emisor = requireIdUsuario();
        try {
            cliente.enviarAudioACanal(emisor, idCanal, audioData);
            return esperarNotificacion(Duration.ofSeconds(10));
        } catch (IOException e) {
            CompletableFuture<JsonObject> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    public CompletableFuture<Void> cerrarSesion() {
        int emisor = requireIdUsuario();
        try {
            cliente.cerrarSesion(emisor);
            // Completar cuando recibamos una notificación o se cierre la conexión
            CompletableFuture<Void> f = new CompletableFuture<>();
            esperarNotificacion(Duration.ofSeconds(3)).whenComplete((j, t) -> f.complete(null));
            return f.orTimeout(4, TimeUnit.SECONDS).exceptionally(ex -> null);
        } catch (IOException e) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    private int requireIdUsuario() {
        if (idUsuario == null) throw new IllegalStateException("No autenticado");
        return idUsuario;
    }

    // ============ Correlación de respuestas ============

    private record Waiter(Predicate<Mensaje> predicate, CompletableFuture<Mensaje> future, String desc) {}

    private void despacharMensaje(Mensaje mensaje) {
        // Intentar resolver un waiter que haga match
        synchronized (waiters) {
            for (int i = 0; i < waiters.size(); i++) {
                Waiter w = waiters.get(i);
                boolean match = false;
                try { match = w.predicate().test(mensaje); } catch (Exception ignored) {}
                if (match) {
                    waiters.remove(i);
                    w.future().complete(mensaje);
                    return;
                }
            }
        }
    }

    private CompletableFuture<JsonObject> esperarNotificacion(Duration timeout) {
        return esperarNotificacion(m -> true, timeout);
    }

    private CompletableFuture<JsonObject> esperarNotificacion(Predicate<Mensaje> filtro, Duration timeout) {
        return esperar(m -> m.getTipo() == TipoMensaje.NOTIFICACION && filtro.test(m), timeout)
                .thenApply(m -> gson.fromJson(m.getCuerpoTexto(), JsonObject.class));
    }

    private CompletableFuture<JsonObject> esperarRespuestaCanal(Duration timeout) {
        return esperar(m -> m.getTipo() == TipoMensaje.RESPUESTA_CANAL, timeout)
                .thenApply(m -> gson.fromJson(m.getCuerpoTexto(), JsonObject.class));
    }

    private CompletableFuture<Mensaje> esperar(Predicate<Mensaje> predicate, Duration timeout) {
        CompletableFuture<Mensaje> future = new CompletableFuture<>();
        Waiter w = new Waiter(predicate, future, "esperar:" + predicate);
        waiters.add(w);
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((r, ex) -> waiters.remove(w));
        return future;
    }

    @Override
    public void close() {
        desconectar();
    }
}
