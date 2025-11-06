package com.universidad.chat.servidor.ui;

import com.universidad.chat.servidor.app.ServidorAppIntegrado;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ServidorUIApp extends Application {
    private ServidorAppIntegradoMainWrapper wrapper;

    @Override
    public void start(Stage primaryStage) {
        wrapper = new ServidorAppIntegradoMainWrapper();
        ServerDashboardView root = new ServerDashboardView(wrapper);
        Scene scene = new Scene(root, 900, 600);
        primaryStage.setTitle("Servidor Chat - Panel");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            wrapper.shutdown();
            Platform.exit();
            // Forzar la salida de la JVM por si quedan hilos no-daemon (pools, sockets, etc.)
            System.exit(0);
        });
        primaryStage.show();

        // Arrancar servidor en hilo aparte
        Thread starter = new Thread(() -> wrapper.startServer(), "server-starter");
        // Marcar como daemon para no impedir el cierre si ya se ordenó shutdown
        starter.setDaemon(true);
        starter.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
