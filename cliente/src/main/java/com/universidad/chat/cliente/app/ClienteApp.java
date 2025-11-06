package com.universidad.chat.cliente.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.universidad.chat.cliente.ui.LoginView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal del cliente de chat.
 * Inicia la aplicación JavaFX y gestiona la interfaz de usuario.
 */
public class ClienteApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApp.class);

    @Override
    public void start(Stage primaryStage) {
        logger.info("Iniciando Cliente de Chat Universidad...");
        
        try {
            // La BD se inicializará después del login con el ID del usuario específico
            
            // Crear y mostrar la vista de Login
            var root = new LoginView();
            // Aumentar tamaño de la ventana: +250px ancho y +100px alto
            var scene = new Scene(root, 480 + 250, 420 + 100);
            primaryStage.setTitle("Chat Universidad - Cliente");
            primaryStage.setScene(scene);
            // Establecer tamaño mínimo igual al tamaño inicial para evitar solapes al reducir
            primaryStage.setMinWidth(scene.getWidth());
            primaryStage.setMinHeight(scene.getHeight());
            // Asegurar cierre limpio de la aplicación al cerrar la ventana principal
            primaryStage.setOnCloseRequest(e -> {
                try {
                    // Dar oportunidad a handlers de vistas (si existen) de ejecutar primero
                    // Luego cerrar JavaFX y la JVM por si quedan hilos no daemon
                    javafx.application.Platform.exit();
                } finally {
                    System.exit(0);
                }
            });
            primaryStage.show();
            
            logger.info("Cliente iniciado correctamente");
            
        } catch (Exception e) {
            logger.error("Error al iniciar el cliente", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
