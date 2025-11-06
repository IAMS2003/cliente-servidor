package com.universidad.chat.cliente.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import com.universidad.chat.cliente.service.ClienteProtocoloService;

public class LoginView extends VBox {
    private final TextField usuarioField = new TextField();
    private final PasswordField contrasenaField = new PasswordField();
    private final Button loginBtn = new Button("Iniciar sesión");
    private final Label statusLabel = new Label();

    public LoginView() {
        setSpacing(16);
        setPadding(new Insets(32));
        setAlignment(Pos.CENTER);

        Text title = new Text("Chat Universidad");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        usuarioField.setPromptText("Usuario");
        contrasenaField.setPromptText("Contraseña");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-text-fill: #c00;");
    Label hint = new Label("El registro de usuarios lo realiza el administrador del servidor.");
    hint.setStyle("-fx-text-fill: #555;");

    getChildren().addAll(title, usuarioField, contrasenaField, loginBtn, hint, statusLabel);

    loginBtn.setOnAction(e -> onLogin());
    }

    private void onLogin() {
        String usuario = usuarioField.getText().trim();
        String contrasena = contrasenaField.getText();
        if (usuario.isEmpty() || contrasena.isEmpty()) {
            statusLabel.setText("Completa usuario y contraseña");
            return;
        }
        statusLabel.setText("Conectando...");
        new Thread(() -> {
            ClienteProtocoloService svc = new ClienteProtocoloService();
            try {
                svc.conectar();
                var resp = svc.autenticar(usuario, contrasena).get();
                if (resp.has("exito") && resp.get("exito").getAsBoolean()) {
                    // Guardar ID de usuario fuera del bloque try para usarlo después
                    final int idUsuario = svc.getIdUsuario();
                    
                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText("¡Bienvenido " + usuario + "!");
                        
                        // Inicializar BD específica para este usuario
                        try {
                            com.universidad.chat.cliente.config.ConexionBD.getInstancia()
                                .inicializar(idUsuario, usuario);
                            com.universidad.chat.cliente.config.ConexionBD.getInstancia()
                                .inicializarEsquema();
                            
                        } catch (Exception e) {
                            System.err.println("Error al inicializar BD del usuario: " + e.getMessage());
                            e.printStackTrace();
                        }
                        
                        ChatView chatView = new ChatView(svc);
                        
                        // Cambiar a ChatView primero
                        javafx.scene.Scene scene = getScene();
                        if (scene != null) {
                            scene.setRoot(chatView);
                            
                            // Configurar cierre de sesión al cerrar la ventana
                            javafx.stage.Window window = scene.getWindow();
                            if (window != null) {
                                window.addEventHandler(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, evt -> {
                                    try {
                                        svc.cerrarSesion().get(2, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (Exception ignored) {}
                                    try {
                                        svc.close();
                                    } catch (Exception ignored) {}
                                });
                            }
                            
                            // Iniciar sincronización DESPUÉS de mostrar ChatView
                            sincronizarMensajesEnBackground(svc, idUsuario, usuario);
                        }
                    });
                } else {
                    // Cerrar si no autenticó
                    try { svc.close(); } catch (Exception ignored) {}
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Credenciales incorrectas"));
                }
            } catch (Exception ex) {
                try { svc.close(); } catch (Exception ignored) {}
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error de conexión"));
            }
        }).start();
    }

    // Registro desde cliente deshabilitado por política: solo el servidor puede registrar usuarios.
    
    /**
     * Sincroniza los mensajes del servidor a la base de datos local en background.
     */
    private void sincronizarMensajesEnBackground(ClienteProtocoloService svc, int idUsuario, String nombreUsuario) {
        new Thread(() -> {
            try {
                // Crear servicios necesarios
                com.universidad.chat.cliente.service.MensajePersistenciaService persistenciaService = 
                    new com.universidad.chat.cliente.service.MensajePersistenciaService(idUsuario);
                
                com.universidad.chat.cliente.service.SincronizacionService sincronizacionService = 
                    new com.universidad.chat.cliente.service.SincronizacionService(svc, persistenciaService, idUsuario);
                
                // Ejecutar sincronización completa
                var futuro = sincronizacionService.sincronizarTodoAlIniciarSesion();
                var resultado = futuro.get(); // Esperar a que termine
                
                // Actualizar UI con el resultado
                javafx.application.Platform.runLater(() -> {
                    if (resultado.exitoso) {
                        int totalMensajes = resultado.mensajesUsuarios + resultado.mensajesCanales;
                        if (totalMensajes > 0) {
                            statusLabel.setText("✓ " + totalMensajes + " mensajes sincronizados");
                        } else {
                            statusLabel.setText("✓ Todo sincronizado");
                        }
                        System.out.println("Sincronización completada:");
                        System.out.println("  - Mensajes de usuarios: " + resultado.mensajesUsuarios);
                        System.out.println("  - Mensajes de canales: " + resultado.mensajesCanales);
                    } else {
                        statusLabel.setText("⚠ Error en sincronización");
                        System.err.println("Error en sincronización: " + resultado.error);
                    }
                });
                
            } catch (Exception e) {
                System.err.println("Error durante la sincronización: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("⚠ Error en sincronización");
                });
            }
        }).start();
    }
}
