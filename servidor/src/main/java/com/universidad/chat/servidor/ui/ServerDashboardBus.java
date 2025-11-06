package com.universidad.chat.servidor.ui;

import javafx.application.Platform;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Bus de eventos simple para comunicar el servidor con la vista JavaFX.
 */
public class ServerDashboardBus {
    private static Consumer<String> logConsumer;
    private static BiConsumer<Integer, String> connectConsumer;
    private static Consumer<Integer> disconnectConsumer;
    // Callbacks para refresco de datos
    private static Runnable usuariosChanged;
    private static Runnable conectadosChanged;
    private static Runnable canalesChanged;
    private static Runnable logsChanged;
    private static Runnable audioLogsChanged;
    private static java.util.function.IntConsumer canalMiembrosChanged;

    public static void bind(Consumer<String> log, BiConsumer<Integer, String> onConnect, Consumer<Integer> onDisconnect) {
        logConsumer = log;
        connectConsumer = onConnect;
        disconnectConsumer = onDisconnect;
    }

    public static void bindDataRefresh(Runnable onUsuarios, Runnable onConectados, Runnable onCanales,
                                       Runnable onLogs, Runnable onAudioLogs,
                                       java.util.function.IntConsumer onCanalMiembros) {
        usuariosChanged = onUsuarios;
        conectadosChanged = onConectados;
        canalesChanged = onCanales;
        logsChanged = onLogs;
        audioLogsChanged = onAudioLogs;
        canalMiembrosChanged = onCanalMiembros;
    }

    public static void appendLog(String msg) {
        if (logConsumer != null) Platform.runLater(() -> logConsumer.accept("[" + LocalDateTime.now() + "] " + msg));
    }

    public static void userConnected(int id, String nombre) {
        if (connectConsumer != null) Platform.runLater(() -> connectConsumer.accept(id, nombre));
        if (conectadosChanged != null) Platform.runLater(conectadosChanged);
    }

    public static void userDisconnected(int id) {
        if (disconnectConsumer != null) Platform.runLater(() -> disconnectConsumer.accept(id));
        if (conectadosChanged != null) Platform.runLater(conectadosChanged);
    }

    // Disparadores de refresco
    public static void notifyUsuariosChanged() { if (usuariosChanged != null) Platform.runLater(usuariosChanged); }
    public static void notifyConectadosChanged() { if (conectadosChanged != null) Platform.runLater(conectadosChanged); }
    public static void notifyCanalesChanged() { if (canalesChanged != null) Platform.runLater(canalesChanged); }
    public static void notifyLogsChanged() { if (logsChanged != null) Platform.runLater(logsChanged); }
    public static void notifyAudioLogsChanged() { if (audioLogsChanged != null) Platform.runLater(audioLogsChanged); }
    public static void notifyCanalMiembrosChanged(int idCanal) { if (canalMiembrosChanged != null) Platform.runLater(() -> canalMiembrosChanged.accept(idCanal)); }
}
