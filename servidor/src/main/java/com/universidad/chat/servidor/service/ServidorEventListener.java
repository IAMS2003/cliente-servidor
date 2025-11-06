package com.universidad.chat.servidor.service;

import com.universidad.chat.servidor.util.TipoMensaje;

public interface ServidorEventListener {
    default void onServerStarted(int port) {}
    default void onServerStopped() {}
    default void onClientSocketConnected(String address) {}
    default void onUserAuthenticated(int idUsuario, String nombreUsuario, String ip) {}
    default void onUserDisconnected(int idUsuario) {}
    default void onMessage(TipoMensaje tipo, int idUsuario, String resumen) {}
    default void onError(String where, String message) {}
    // Nuevos eventos para refresco en tiempo real de la UI
    default void onUsuariosChanged() {}
    default void onConectadosChanged() {}
    default void onCanalesChanged() {}
    default void onCanalMiembrosChanged(int idCanal) {}
    default void onLogsChanged() {}
    default void onAudioLogsChanged() {}
}
