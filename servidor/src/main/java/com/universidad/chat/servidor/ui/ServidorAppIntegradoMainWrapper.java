package com.universidad.chat.servidor.ui;

import com.universidad.chat.servidor.config.ConexionBD;
import com.universidad.chat.servidor.service.ServidorTCPIntegrado;
import com.universidad.chat.servidor.service.ServidorEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper para iniciar/detener el servidor desde una UI JavaFX.
 */
public class ServidorAppIntegradoMainWrapper {
    private static final Logger logger = LoggerFactory.getLogger(ServidorAppIntegradoMainWrapper.class);

    private ServidorTCPIntegrado servidor;
    private ConexionBD conexionBD;

    public void startServer() {
        try {
            conexionBD = ConexionBD.getInstancia();
            conexionBD.inicializarEsquema();
            servidor = new ServidorTCPIntegrado();
            servidor.setListener(new ServidorEventListener() {
                @Override public void onServerStarted(int port) {}
                @Override public void onServerStopped() {}
                @Override public void onClientSocketConnected(String address) { ServerDashboardBus.appendLog("Conexión desde " + address); }
                @Override public void onUserAuthenticated(int idUsuario, String nombreUsuario, String ip) { ServerDashboardBus.userConnected(idUsuario, nombreUsuario); }
                @Override public void onUserDisconnected(int idUsuario) { ServerDashboardBus.userDisconnected(idUsuario); }
                @Override public void onMessage(com.universidad.chat.servidor.util.TipoMensaje tipo, int idUsuario, String resumen) { ServerDashboardBus.appendLog("Msg " + tipo + " de " + idUsuario + ": " + resumen); }
                @Override public void onError(String where, String message) { ServerDashboardBus.appendLog("Error(" + where + "): " + message); }
                // Nuevos eventos de dominio -> refrescar vistas en tiempo real
                @Override public void onUsuariosChanged() { ServerDashboardBus.notifyUsuariosChanged(); }
                @Override public void onConectadosChanged() { ServerDashboardBus.notifyConectadosChanged(); }
                @Override public void onCanalesChanged() { ServerDashboardBus.notifyCanalesChanged(); }
                @Override public void onCanalMiembrosChanged(int idCanal) { ServerDashboardBus.notifyCanalMiembrosChanged(idCanal); }
                @Override public void onLogsChanged() { ServerDashboardBus.notifyLogsChanged(); }
                @Override public void onAudioLogsChanged() { ServerDashboardBus.notifyAudioLogsChanged(); }
            });
            servidor.iniciar();
        } catch (Exception e) {
            logger.error("Error iniciando servidor", e);
        }
    }

    public void shutdown() {
        try {
            if (servidor != null) servidor.detener();
            if (conexionBD != null) conexionBD.cerrarConexion();
        } catch (Exception e) {
            logger.error("Error deteniendo servidor", e);
        }
    }

    // Métodos para que la UI consulte y controle el servidor
    public java.util.List<com.universidad.chat.servidor.model.Usuario> listarUsuarios() {
        if (servidor == null) return java.util.List.of();
        return servidor.obtenerTodosUsuarios();
    }

    public java.util.List<com.universidad.chat.servidor.model.Usuario> listarUsuariosConectados() {
        if (servidor == null) return java.util.List.of();
        return servidor.obtenerUsuariosConectados();
    }

    public java.util.List<com.universidad.chat.servidor.model.Canal> listarCanales() {
        if (servidor == null) return java.util.List.of();
        return servidor.obtenerCanales();
    }

    public java.util.List<com.universidad.chat.servidor.model.Usuario> listarMiembrosCanal(int idCanal) {
        if (servidor == null) return java.util.List.of();
        return servidor.obtenerMiembrosCanalUsuarios(idCanal);
    }

    public java.util.List<com.universidad.chat.servidor.model.MensajeLog> listarLogs() {
        if (servidor == null) return java.util.List.of();
        return servidor.obtenerLogs();
    }

    public java.util.List<com.universidad.chat.servidor.model.MensajeLog> listarLogsAudio() {
        if (servidor == null) return java.util.List.of();
        return servidor.obtenerMensajesAudioLogs();
    }

    public void forzarDesconexionUsuario(int idUsuario) {
        if (servidor != null) servidor.forzarDesconexionUsuario(idUsuario);
    }

    public int registrarUsuario(String nombreUsuario, String email, String contrasena, String fotoBase64, String ip) {
        if (servidor == null) return -1;
        return servidor.registrarUsuario(nombreUsuario, email, contrasena, fotoBase64, ip);
    }

    public void broadcastATodosUsuarios(String contenido) {
        if (servidor != null && contenido != null && !contenido.isBlank()) {
            servidor.broadcastATodosUsuarios(contenido);
        }
    }

    public void broadcastATodosCanales(String contenido) {
        if (servidor != null && contenido != null && !contenido.isBlank()) {
            servidor.broadcastATodosCanales(contenido);
        }
    }
}
