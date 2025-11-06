package com.universidad.chat.cliente.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import com.universidad.chat.cliente.service.ClienteProtocoloService;
import com.universidad.chat.cliente.service.MensajePersistenciaService;
import com.universidad.chat.cliente.model.MensajeLog;
import com.universidad.chat.cliente.util.AudioPlayer;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ChatView extends BorderPane {
    private final ClienteProtocoloService svc;
    private final MensajePersistenciaService persistencia;
    // Sidebars
    private final ListView<UsuarioItem> usuariosList = new ListView<>();
    private final ListView<String> canalesList = new ListView<>();
    // Center conversation
    private final ListView<MensajeItem> chatArea = new ListView<>();
    private final ObservableList<MensajeItem> mensajes = FXCollections.observableArrayList();
    private final TextField mensajeField = new TextField();
    private final Button enviarBtn = new Button("Enviar");
    private final Button audioBtn = new Button("🎤 Grabar");
    private final Label statusLabel = new Label();
    // Header labels
    private final Label tituloConversacion = new Label("Conversación");
    private final Label nombreChatLabel = new Label("");
    // Acciones de canal (trasladadas a la sección de canales)
    private final Button crearCanalBtn = new Button("Crear canal");
    private final Button unirseCanalBtn = new Button("Invitar a canal");

    // Audio recorder
    private com.universidad.chat.cliente.util.AudioRecorder audioRecorder;
    private volatile boolean grabando = false;
    
    // Control de carga de historial para prevenir duplicados
    private volatile boolean cargandoHistorial = false;
    private volatile int ultimoChatCargado = -1;
    private volatile Destino ultimoDestinoCargado = null;

    // Audio players - mapa de identificador -> reproductor
    private final Map<String, AudioPlayer> audioPlayers = new HashMap<>();
    private final Map<String, byte[]> audioDataMap = new HashMap<>();

    // Context state
    private enum Destino { CANAL, USUARIO }
    private Destino destinoActual = Destino.CANAL;
    private int idDestino = 1; // canal general por defecto

    public ChatView(ClienteProtocoloService svc) {
        this.svc = svc;
        // Inicializar servicio de persistencia con el ID del usuario actual
        this.persistencia = new MensajePersistenciaService(svc.getIdUsuario());

        // Left panel: usuarios y canales
        usuariosList.setPrefWidth(160);
        canalesList.setPrefWidth(160);
        var leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.VERTICAL);
    var usuariosBox = new VBox(new Label("Conectados"), usuariosList);
    var canalesButtons = new HBox(8, crearCanalBtn, unirseCanalBtn);
    var canalesBox = new VBox(new Label("Canales"), canalesList, canalesButtons);
        usuariosBox.setSpacing(6); canalesBox.setSpacing(6);
        usuariosBox.setPadding(new Insets(6)); canalesBox.setPadding(new Insets(6));
        leftSplit.getItems().addAll(usuariosBox, canalesBox);
        leftSplit.setDividerPositions(0.5);
        setLeft(leftSplit);

        // Center chat
        chatArea.setItems(mensajes);
        chatArea.setCellFactory(lv -> new MensajeCelda(this::reproducirAudioConBoton));
        chatArea.setPrefHeight(300);
        // Que la lista de mensajes ocupe todo el alto disponible
        VBox.setVgrow(chatArea, javafx.scene.layout.Priority.ALWAYS);

        mensajeField.setPromptText("Escribe tu mensaje...");
        mensajeField.setMaxWidth(Double.MAX_VALUE);
        HBox input = new HBox(8, mensajeField, enviarBtn, audioBtn);
        input.setPadding(new Insets(8));
        // Hacer que el campo de texto ocupe todo el ancho disponible de la fila
        HBox.setHgrow(mensajeField, javafx.scene.layout.Priority.ALWAYS);

    // Encabezado: "Conversación" a la izquierda y nombre del chat abierto a la derecha
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(8, tituloConversacion, spacer, nombreChatLabel);
    header.setPadding(new Insets(8, 8, 0, 8));

    VBox center = new VBox(header, chatArea, input, statusLabel);
        center.setSpacing(8);
        center.setPadding(new Insets(8));
        setCenter(center);

    // Sin barra superior: los botones se movieron a la sección de canales

        // Event handlers
        enviarBtn.setOnAction(e -> enviarMensaje());
        audioBtn.setOnAction(e -> toggleGrabacionAudio());
        crearCanalBtn.setOnAction(e -> crearCanal());
    unirseCanalBtn.setOnAction(e -> invitarAUsuarioACanal());

        usuariosList.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> {
            if (b != null) {
                // Deseleccionar canal para evitar conflictos
                canalesList.getSelectionModel().clearSelection();
                destinoActual = Destino.USUARIO;
                idDestino = b.id();
                limpiarChat();
                setNombreChatActual("@" + b.nombre());
                // Cargar historial de mensajes con este usuario
                cargarHistorialUsuario(b.id());
            }
        });
        canalesList.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> {
            if (b != null) {
                // Deseleccionar usuario para evitar conflictos
                usuariosList.getSelectionModel().clearSelection();
                destinoActual = Destino.CANAL;
                idDestino = parseId(b);
                limpiarChat();
                setNombreChatActual(b);
                // Cargar historial de mensajes del canal
                cargarHistorialCanal(idDestino);
            }
        });

        // Eventos desde el servidor
        this.svc.setEventos(new ClienteProtocoloService.EventosCliente() {
            @Override
            public void onServidorDetenido(String mensaje) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Servidor");
                    alert.setHeaderText(null);
                    alert.setContentText(mensaje);
                    alert.showAndWait();
                    getScene().setRoot(new LoginView());
                });
            }

            @Override
            public void onBroadcast(String mensaje) {
                Platform.runLater(() -> agregarMensajeTexto("[Broadcast]", mensaje));
            }

            @Override
            public void onUserConnectionChange() {
                // Actualizar lista de conectados cuando alguien se conecta o desconecta
                refrescarSoloUsuarios();
            }

            @Override
            public void onMensajeUsuario(int idEmisor, String contenido, JsonObject raw) {
                Integer miId = svc.getIdUsuario();
                Integer idReceptor = raw.has("idReceptor") ? raw.get("idReceptor").getAsInt() : miId; // fallback
                Integer idServidor = raw.has("id") ? raw.get("id").getAsInt() : null;
                System.out.println("📨 [DEBUG] onMensajeUsuario: idEmisor=" + idEmisor + ", idReceptor=" + idReceptor + ", idServidor=" + idServidor + ", contenido='" + contenido + "'");

                // Determinar si el mensaje es entrante o saliente respecto a miId
                boolean soyEmisor = (miId != null && idEmisor == miId);

                // Persistir usando los IDs reales
                if (miId != null) {
                    // Caso mensaje saliente (yo lo envié): emisor = miId, receptor = idReceptor
                    // Caso mensaje entrante (otro usuario): emisor = idEmisor, receptor = miId
                    int emisorPersist = idEmisor;
                    int receptorPersist = idReceptor;
                    persistencia.guardarMensajeTexto(emisorPersist, receptorPersist, null, contenido, idServidor);
                }

                // Mostrar solo si el chat abierto corresponde al otro usuario (el contraparte)
                Platform.runLater(() -> {
                    if (destinoActual == Destino.USUARIO && miId != null) {
                        int contraparte = soyEmisor ? idReceptor : idEmisor;
                        if (idDestino == contraparte) {
                            agregarMensajeTexto("Usuario " + idEmisor, contenido);
                        }
                    }
                });
            }

            @Override
            public void onMensajeCanal(int idCanal, int idEmisor, String contenido, JsonObject raw) {
                System.out.println("📨 [DEBUG] onMensajeCanal: idCanal=" + idCanal + ", idEmisor=" + idEmisor + ", contenido='" + contenido + "'");
                System.out.println("📨 [DEBUG] Estado actual: destinoActual=" + destinoActual + ", idDestino=" + idDestino);
                
                // Extraer ID del servidor si está disponible
                Integer idServidor = raw.has("id") ? raw.get("id").getAsInt() : null;
                System.out.println("📨 [DEBUG] ID del servidor: " + idServidor);
                
                // Guardar en BD local con ID del servidor
                persistencia.guardarMensajeTexto(idEmisor, null, idCanal, contenido, idServidor);
                // Mostrar en UI SOLO si el canal está abierto
                Platform.runLater(() -> {
                    if (destinoActual == Destino.CANAL && idDestino == idCanal) {
                        System.out.println("✓ [DEBUG] Mostrando mensaje de canal en UI");
                        agregarMensajeTexto("[Canal " + idCanal + "] Usuario " + idEmisor, contenido);
                    } else {
                        System.out.println("⚠ [DEBUG] Mensaje de canal NO mostrado (canal no abierto)");
                    }
                });
            }

            @Override
            public void onAudioUsuario(int idEmisor, String archivoAudio, String transcripcion, String audioData, JsonObject raw) {
                Integer idServidor = raw.has("id") ? raw.get("id").getAsInt() : null;
                System.out.println("🎤 [DEBUG] onAudioUsuario: idEmisor=" + idEmisor + ", idServidor=" + idServidor + ", transcripcion='" + transcripcion + "'");
                
                // Guardar en BD local
                Integer miId = svc.getIdUsuario();
                if (miId != null) {
                    persistencia.guardarMensajeAudio(idEmisor, miId, null, transcripcion, audioData, idServidor);
                }
                // Mostrar en UI SOLO si el chat con este usuario está abierto
                Platform.runLater(() -> {
                    if (destinoActual == Destino.USUARIO && idDestino == idEmisor) {
                        System.out.println("✓ [DEBUG] Mostrando audio en UI");
                        agregarMensajeAudio("Usuario " + idEmisor, transcripcion, audioData);
                    } else {
                        System.out.println("⚠ [DEBUG] Audio NO mostrado (chat no abierto)");
                    }
                });
            }

            @Override
            public void onAudioCanal(int idCanal, int idEmisor, String archivoAudio, String transcripcion, String audioData, JsonObject raw) {
                Integer idServidor = raw.has("id") ? raw.get("id").getAsInt() : null;
                System.out.println("🎤 [DEBUG] onAudioCanal: idCanal=" + idCanal + ", idEmisor=" + idEmisor + ", idServidor=" + idServidor + ", transcripcion='" + transcripcion + "'");
                
                // Guardar en BD local
                persistencia.guardarMensajeAudio(idEmisor, null, idCanal, transcripcion, audioData, idServidor);
                // Mostrar en UI SOLO si el canal está abierto
                Platform.runLater(() -> {
                    if (destinoActual == Destino.CANAL && idDestino == idCanal) {
                        System.out.println("✓ [DEBUG] Mostrando audio de canal en UI");
                        agregarMensajeAudio("[Canal " + idCanal + "] Usuario " + idEmisor, transcripcion, audioData);
                    } else {
                        System.out.println("⚠ [DEBUG] Audio de canal NO mostrado (canal no abierto)");
                    }
                });
            }

            @Override
            public void onArchivoUsuario(int idEmisor, String nombre, JsonObject raw) {
                Platform.runLater(() -> agregarMensajeTexto("Archivo de usuario " + idEmisor, nombre));
            }

            @Override
            public void onArchivoCanal(int idCanal, int idEmisor, String nombre, JsonObject raw) {
                Platform.runLater(() -> agregarMensajeTexto("[Canal " + idCanal + "] archivo de usuario " + idEmisor, nombre));
            }

            @Override
            public void onNotificacion(JsonObject notificacion) {
                // Mostrar aceptación/rechazo de solicitudes a canal si vienen en notificación
                try {
                    if (notificacion.has("tipo")) {
                        String tipo = notificacion.get("tipo").getAsString();
                        if ("INVITACION_CANAL".equals(tipo)) {
                            // Recibió invitación a un canal - mostrar diálogo de aceptar/rechazar
                            Platform.runLater(() -> ChatView.this.mostrarDialogoInvitacion(notificacion));
                            return;
                        }
                    }
                    if (notificacion.has("canal") && notificacion.has("estado")) {
                        String estado = notificacion.get("estado").getAsString();
                        Platform.runLater(() -> agregarMensajeSistema("[Canal] Solicitud: " + estado));
                    }
                } catch (Exception ignored) {}
            }
        });

        // Cargar listas iniciales
        refrescarListas();
    }

    private int parseId(String item) {
        // Items form "#<id> <name>"
        try {
            if (item.startsWith("#")) {
                int sp = item.indexOf(' ');
                String id = sp > 1 ? item.substring(1, sp) : item.substring(1);
                return Integer.parseInt(id.trim());
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private void enviarMensaje() {
        String texto = mensajeField.getText().trim();
        if (texto.isEmpty()) return;
        mensajeField.clear();
        
        // Detectar comandos especiales
        if (texto.startsWith("/play ")) {
            String audioId = "audio_" + texto.substring(6).trim();
            reproducirAudio(audioId);
            return;
        }
        
        // Echo local inmediato para que el mensaje se vea al instante
        agregarMensajeTexto("Tú", texto);
        
        // Guardar mensaje en BD antes de enviarlo al servidor
        try {
            int userId = svc.getIdUsuario();
            if (destinoActual == Destino.CANAL) {
                persistencia.guardarMensajeTexto(userId, null, idDestino, texto);
            } else {
                persistencia.guardarMensajeTexto(userId, idDestino, null, texto);
            }
        } catch (Exception e) {
            System.err.println("Error al guardar mensaje en BD: " + e.getMessage());
            e.printStackTrace();
        }
        
        statusLabel.setText("Enviando...");
        // Enviar en background sin bloquear la UI; actualizamos estado al completar
        new Thread(() -> {
            try {
                if (destinoActual == Destino.CANAL) {
                    svc.enviarMensajeACanal(idDestino, texto)
                       .whenComplete((r, t) -> Platform.runLater(() -> statusLabel.setText(t == null ? "Enviado" : "Enviado (sin confirmación)")));
                } else {
                    svc.enviarMensajeATextoUsuario(idDestino, texto)
                       .whenComplete((r, t) -> Platform.runLater(() -> statusLabel.setText(t == null ? "Enviado" : "Enviado (sin confirmación)")));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al enviar"));
            }
        }, "enviar-mensaje").start();
    }

    // Envío de archivos deshabilitado por requerimiento: solo audios grabados

    private void refrescarListas() {
        statusLabel.setText("Actualizando listas...");
    CompletableFuture<List<JsonObject>> fUsuarios = svc.listarUsuariosConectados();
        CompletableFuture<List<JsonObject>> fCanales = svc.listarCanales();
        CompletableFuture.allOf(fUsuarios, fCanales).whenComplete((v, t) -> {
            if (t != null) {
                Platform.runLater(() -> statusLabel.setText("Error al actualizar listas"));
                return;
            }
            var usuarios = fUsuarios.join();
            var canales = fCanales.join();
            Platform.runLater(() -> {
                Integer miId = svc.getIdUsuario();
                // Filtrar el usuario propio de la lista de conectados
                var usuariosFiltrados = usuarios.stream()
                    .filter(o -> {
                        if (miId == null) return true;
                        try {
                            int id = o.has("id") ? o.get("id").getAsInt() : -1;
                            return id != miId;
                        } catch (Exception e) { return true; }
                    })
                    .map(ChatView::toUsuarioItem)
                    .toList();
                usuariosList.getItems().setAll(usuariosFiltrados);
                var listaCanales = canales.stream().map(o -> formatItem(o, "id", "nombre", "canal")).toList();
                canalesList.getItems().setAll(listaCanales);
                // Actualizar nombre del chat según selección/destino actual
                if (destinoActual == Destino.CANAL) {
                    String etiqueta = listaCanales.stream()
                        .filter(s -> ("#" + idDestino).equals(s.split(" ")[0]))
                        .findFirst().orElse("#" + idDestino);
                    setNombreChatActual(etiqueta);
                }
                statusLabel.setText("Listas actualizadas");
            });
        });
    }

    private void refrescarSoloUsuarios() {
        svc.listarUsuariosConectados().whenComplete((usuarios, t) -> {
            if (t != null) return;
            Platform.runLater(() -> {
                Integer miId = svc.getIdUsuario();
                var usuariosFiltrados = usuarios.stream()
                    .filter(o -> {
                        if (miId == null) return true;
                        try {
                            int id = o.has("id") ? o.get("id").getAsInt() : -1;
                            return id != miId;
                        } catch (Exception e) { return true; }
                    })
                    .map(ChatView::toUsuarioItem)
                    .toList();
                usuariosList.getItems().setAll(usuariosFiltrados);
            });
        });
    }

    private void setNombreChatActual(String nombre) {
        if (nombre == null) nombre = "";
        nombreChatLabel.setText(nombre);
    }

    private String formatItem(JsonObject o, String idKey, String nombreKey, String fallbackLabel) {
        try {
            int id = o.has(idKey) ? o.get(idKey).getAsInt() : -1;
            String nombre = o.has(nombreKey) ? o.get(nombreKey).getAsString() : fallbackLabel + " " + id;
            return "#" + id + " " + nombre;
        } catch (Exception e) {
            return fallbackLabel;
        }
    }

    // ==== Renderizado de usuarios con avatar ====
    private static record UsuarioItem(int id, String nombre, Image avatar) {}

    private static UsuarioItem toUsuarioItem(com.google.gson.JsonObject o) {
        int id = -1; String nombre = "usuario"; Image img = null;
        try { id = o.has("id") ? o.get("id").getAsInt() : -1; } catch (Exception ignored) {}
        try { nombre = o.has("nombreUsuario") ? o.get("nombreUsuario").getAsString() : ("usuario " + id); } catch (Exception ignored) {}
        try {
            if (o.has("fotoBase64") && !o.get("fotoBase64").isJsonNull()) {
                String b64 = o.get("fotoBase64").getAsString();
                if (b64 != null && !b64.isBlank()) {
                    byte[] bytes = java.util.Base64.getDecoder().decode(b64);
                    java.io.ByteArrayInputStream bin = new java.io.ByteArrayInputStream(bytes);
                    // Solicitar tamaño razonable (32x32) y mantener proporción
                    img = new Image(bin, 32, 32, true, true);
                }
            }
        } catch (Exception ignored) {}
        return new UsuarioItem(id, nombre, img);
    }

    {
        // Bloque de inicialización para configurar el cell factory de usuariosList
        usuariosList.setCellFactory(lv -> new ListCell<UsuarioItem>() {
            private final HBox box = new HBox(8);
            private final ImageView imageView = new ImageView();
            private final Label label = new Label();
            {
                imageView.setFitWidth(24);
                imageView.setFitHeight(24);
                imageView.setPreserveRatio(true);
                box.getChildren().addAll(imageView, label);
            }
            @Override
            protected void updateItem(UsuarioItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    label.setText(item.nombre());
                    if (item.avatar() != null) {
                        imageView.setImage(item.avatar());
                        imageView.setVisible(true);
                    } else {
                        imageView.setImage(null);
                        imageView.setVisible(false);
                    }
                    setGraphic(box);
                }
            }
        });
    }

    private void crearCanal() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Crear canal");
        dialog.setHeaderText("Nombre del canal");
        dialog.setContentText("Nombre:");
        dialog.showAndWait().ifPresent(nombre -> new Thread(() -> {
            try {
                svc.crearCanalPublico(nombre).get();
                Platform.runLater(this::refrescarListas);
            } catch (Exception ignored) { Platform.runLater(() -> statusLabel.setText("Error creando canal")); }
        }).start());
    }

    private void invitarAUsuarioACanal() {
        String selCanal = canalesList.getSelectionModel().getSelectedItem();
        if (selCanal == null) { statusLabel.setText("Selecciona un canal"); return; }
        int idCanal = parseId(selCanal);
        
        // Mostrar modal con checkboxes de usuarios disponibles
        mostrarModalInvitacion(idCanal);
    }

    private void mostrarModalInvitacion(int idCanal) {
        statusLabel.setText("Cargando usuarios disponibles...");
        
        // Obtener usuarios conectados, miembros del canal y pendientes en paralelo
        CompletableFuture<List<JsonObject>> fConectados = svc.listarUsuariosConectados();
        CompletableFuture<JsonObject> fMiembrosInfo = svc.solicitarMiembrosCanal(idCanal);
        
        CompletableFuture.allOf(fConectados, fMiembrosInfo).whenComplete((v, t) -> {
            if (t != null) {
                Platform.runLater(() -> statusLabel.setText("Error cargando información"));
                return;
            }
            
            var conectados = fConectados.join();
            var miembrosInfo = fMiembrosInfo.join();
            
            // Extraer IDs de miembros y pendientes
            java.util.Set<Integer> miembrosSet = new java.util.HashSet<>();
            java.util.Set<Integer> pendientesSet = new java.util.HashSet<>();
            
            try {
                if (miembrosInfo.has("miembros")) {
                    miembrosInfo.getAsJsonArray("miembros").forEach(el -> {
                        try { miembrosSet.add(el.getAsInt()); } catch (Exception ignored) {}
                    });
                }
                if (miembrosInfo.has("pendientes")) {
                    miembrosInfo.getAsJsonArray("pendientes").forEach(el -> {
                        try { pendientesSet.add(el.getAsInt()); } catch (Exception ignored) {}
                    });
                }
            } catch (Exception ignored) {}
            
            // Filtrar usuarios: quitar miembros, pendientes y el usuario actual
            Integer miId = svc.getIdUsuario();
            var disponibles = conectados.stream()
                .map(ChatView::toUsuarioItem)
                .filter(u -> {
                    if (miId != null && u.id() == miId) return false;
                    if (miembrosSet.contains(u.id())) return false;
                    if (pendientesSet.contains(u.id())) return false;
                    return true;
                })
                .toList();
            
            Platform.runLater(() -> {
                if (disponibles.isEmpty()) {
                    statusLabel.setText("No hay usuarios disponibles para invitar");
                    return;
                }
                
                // Crear el diálogo modal
                Dialog<java.util.List<UsuarioItem>> dialog = new Dialog<>();
                dialog.setTitle("Invitar usuarios al canal");
                dialog.setHeaderText("Selecciona los usuarios a invitar");
                
                // Contenedor de checkboxes
                VBox vbox = new VBox(8);
                vbox.setPadding(new Insets(16));
                
                var checkBoxes = new java.util.HashMap<CheckBox, UsuarioItem>();
                
                for (UsuarioItem usuario : disponibles) {
                    HBox row = new HBox(8);
                    CheckBox cb = new CheckBox();
                    
                    // Avatar + nombre
                    if (usuario.avatar() != null) {
                        ImageView iv = new ImageView(usuario.avatar());
                        iv.setFitWidth(24);
                        iv.setFitHeight(24);
                        iv.setPreserveRatio(true);
                        row.getChildren().add(iv);
                    }
                    
                    Label lbl = new Label(usuario.nombre());
                    row.getChildren().addAll(cb, lbl);
                    vbox.getChildren().add(row);
                    checkBoxes.put(cb, usuario);
                }
                
                ScrollPane scroll = new ScrollPane(vbox);
                scroll.setFitToWidth(true);
                scroll.setPrefHeight(300);
                
                dialog.getDialogPane().setContent(scroll);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                
                dialog.setResultConverter(btnType -> {
                    if (btnType == ButtonType.OK) {
                        return checkBoxes.entrySet().stream()
                            .filter(e -> e.getKey().isSelected())
                            .map(java.util.Map.Entry::getValue)
                            .toList();
                    }
                    return null;
                });
                
                dialog.showAndWait().ifPresent(seleccionados -> {
                    if (seleccionados.isEmpty()) {
                        statusLabel.setText("No se seleccionó ningún usuario");
                        return;
                    }
                    
                    // Enviar invitaciones a todos los seleccionados
                    enviarInvitacionesMultiples(idCanal, seleccionados);
                });
                
                statusLabel.setText("");
            });
        });
    }
    
    private void enviarInvitacionesMultiples(int idCanal, java.util.List<UsuarioItem> usuarios) {
        statusLabel.setText("Enviando invitaciones...");
        
        // Enviar invitaciones en paralelo
        var futures = usuarios.stream()
            .map(u -> svc.invitarUsuarioACanal(idCanal, u.id()))
            .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((v, t) -> {
                Platform.runLater(() -> {
                    if (t != null) {
                        statusLabel.setText("Error enviando algunas invitaciones");
                    } else {
                        statusLabel.setText("Invitaciones enviadas a " + usuarios.size() + " usuario(s)");
                    }
                });
            });
    }

    private void mostrarDialogoInvitacion(JsonObject invitacion) {
        try {
            int idCanal = invitacion.get("idCanal").getAsInt();
            String nombreCanal = invitacion.has("nombreCanal") ? invitacion.get("nombreCanal").getAsString() : "Canal";
            String nombreInvitador = invitacion.has("nombreInvitador") ? invitacion.get("nombreInvitador").getAsString() : "Alguien";
            
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Invitación a Canal");
            alert.setHeaderText("Has sido invitado a un canal");
            alert.setContentText(nombreInvitador + " te ha invitado a unirte al canal \"" + nombreCanal + "\".\n\n¿Deseas aceptar la invitación?");
            
            ButtonType btnAceptar = new ButtonType("Aceptar");
            ButtonType btnRechazar = new ButtonType("Rechazar", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnAceptar, btnRechazar);
            
            alert.showAndWait().ifPresent(respuesta -> {
                boolean aceptar = respuesta == btnAceptar;
                statusLabel.setText(aceptar ? "Aceptando invitación..." : "Rechazando invitación...");
                
                new Thread(() -> {
                    try {
                        var resultado = svc.responderInvitacionCanal(idCanal, aceptar).get();
                        Platform.runLater(() -> {
                            String mensaje = resultado.has("mensaje") ? resultado.get("mensaje").getAsString() : 
                                (aceptar ? "Te has unido al canal" : "Invitación rechazada");
                            statusLabel.setText(mensaje);
                            agregarMensajeSistema(mensaje);
                            if (aceptar) {
                                // Refrescar lista de canales
                                refrescarListas();
                            }
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Error al responder invitación");
                            agregarMensajeSistema("[Error] No se pudo responder la invitación");
                        });
                    }
                }).start();
            });
        } catch (Exception e) {
            agregarMensajeSistema("[Error] Error procesando invitación");
        }
    }

    private void cargarHistorialUsuario(int idOtroUsuario) {
        System.out.println("📂 [DEBUG] Solicitando cargar historial usuario: " + idOtroUsuario);
        System.out.println("   Estado actual - cargandoHistorial: " + cargandoHistorial + 
                         ", ultimoChatCargado: " + ultimoChatCargado + 
                         ", ultimoDestinoCargado: " + ultimoDestinoCargado);
        
        // Prevenir cargas concurrentes del mismo chat
        if (cargandoHistorial && ultimoChatCargado == idOtroUsuario && ultimoDestinoCargado == Destino.USUARIO) {
            System.out.println("⚠ Ya se está cargando el historial de este usuario, omitiendo...");
            return;
        }
        
        cargandoHistorial = true;
        ultimoChatCargado = idOtroUsuario;
        ultimoDestinoCargado = Destino.USUARIO;
        
        new Thread(() -> {
            try {
                // Cargar SOLO desde BD local (sin sincronizar con servidor)
                // La sincronización ya se hizo al iniciar sesión
                System.out.println("🔍 [DEBUG] Consultando BD para usuario: " + idOtroUsuario);
                List<MensajeLog> mensajes = persistencia.cargarMensajesUsuario(idOtroUsuario);
                System.out.println("✓ [DEBUG] BD retornó " + mensajes.size() + " mensajes");

                // Fallback: si la BD local no tiene mensajes, solicitar historial al servidor una sola vez
                if (mensajes.isEmpty()) {
                    try {
                        System.out.println("↻ [DEBUG] BD vacía para este chat, solicitando historial al servidor...");
                        var lista = svc.solicitarHistorialUsuario(idOtroUsuario).get();
                        int persistidos = 0;
                        for (JsonObject jm : lista) {
                            Integer idServidor = jm.has("id") ? jm.get("id").getAsInt() : null;
                            int idEmisor = jm.has("idEmisor") ? jm.get("idEmisor").getAsInt() : 0;
                            String tipo = jm.has("tipoMensaje") ? jm.get("tipoMensaje").getAsString() : "TEXTO";
                            if ("TEXT".equalsIgnoreCase(tipo)) tipo = "TEXTO";
                            if ("AUDIO".equalsIgnoreCase(tipo) || jm.has("audioData")) {
                                String trans = jm.has("transcripcion") ? jm.get("transcripcion").getAsString() : "";
                                String audioB64 = jm.has("audioData") ? jm.get("audioData").getAsString() : "";
                                persistencia.guardarMensajeAudio(idEmisor, svc.getIdUsuario(), null, trans, audioB64, idServidor);
                            } else {
                                String contenido = jm.has("contenido") ? jm.get("contenido").getAsString() : "";
                                // Determinar receptor correcto
                                Integer idReceptor = jm.has("idReceptor") ? jm.get("idReceptor").getAsInt() : svc.getIdUsuario();
                                persistencia.guardarMensajeTexto(idEmisor, idReceptor, null, contenido, idServidor);
                            }
                            persistidos++;
                        }
                        System.out.println("✓ [DEBUG] Fallback persistió " + persistidos + " mensajes desde servidor");
                        // Volver a cargar desde BD para mostrar
                        mensajes = persistencia.cargarMensajesUsuario(idOtroUsuario);
                    } catch (Exception ex) {
                        System.err.println("❌ [ERROR] Fallback de historial falló: " + ex.getMessage());
                    }
                }
                
                final List<MensajeLog> mensajesFinal = mensajes;
                Platform.runLater(() -> {
                    // Verificar que seguimos en el mismo chat antes de mostrar
                    if (destinoActual == Destino.USUARIO && idDestino == idOtroUsuario) {
                        System.out.println("✓ [DEBUG] Mostrando " + mensajesFinal.size() + " mensajes en UI");
                        for (MensajeLog msg : mensajesFinal) {
                            mostrarMensajeDesdeBD(msg);
                        }
                        scrollToBottom();
                        statusLabel.setText("Historial cargado: " + mensajesFinal.size() + " mensajes");
                    } else {
                        System.out.println("⚠ El usuario cambió de chat, descartando historial cargado");
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ [ERROR] Error cargando historial: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    agregarMensajeSistema("[Error] No se pudo cargar el historial local: " + e.getMessage());
                });
            } finally {
                cargandoHistorial = false;
                System.out.println("🏁 [DEBUG] Finalizó carga de historial usuario " + idOtroUsuario);
            }
        }, "cargar-historial-usuario").start();
    }

    private void cargarHistorialCanal(int idCanal) {
        // Prevenir cargas concurrentes del mismo chat
        if (cargandoHistorial && ultimoChatCargado == idCanal && ultimoDestinoCargado == Destino.CANAL) {
            System.out.println("⚠ Ya se está cargando el historial de este canal, omitiendo...");
            return;
        }
        
        cargandoHistorial = true;
        ultimoChatCargado = idCanal;
        ultimoDestinoCargado = Destino.CANAL;
        
        new Thread(() -> {
            try {
                // Cargar SOLO desde BD local (sin sincronizar con servidor)
                // La sincronización ya se hizo al iniciar sesión
                List<MensajeLog> mensajes = persistencia.cargarMensajesCanal(idCanal);
                Platform.runLater(() -> {
                    // Verificar que seguimos en el mismo chat antes de mostrar
                    if (destinoActual == Destino.CANAL && idDestino == idCanal) {
                        for (MensajeLog msg : mensajes) {
                            mostrarMensajeDesdeBD(msg);
                        }
                        scrollToBottom();
                        statusLabel.setText("Historial cargado desde BD local");
                    } else {
                        System.out.println("⚠ El usuario cambió de chat, descartando historial cargado");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    agregarMensajeSistema("[Error] No se pudo cargar el historial local del canal");
                });
            } finally {
                cargandoHistorial = false;
            }
        }, "cargar-historial-canal").start();
    }
    
    /**
     * Mostrar mensaje cargado desde la BD local.
     */
    private void mostrarMensajeDesdeBD(MensajeLog msg) {
        try {
            Integer miId = svc.getIdUsuario();
            String prefijo = (miId != null && msg.getIdEmisor() == miId) ? "Tú" : "Usuario #" + msg.getIdEmisor();
            
            if ("TEXTO".equals(msg.getTipoMensaje())) {
                agregarMensajeTexto(prefijo, msg.getContenido());
            } else if ("AUDIO".equals(msg.getTipoMensaje())) {
                // Convertir bytes a Base64 para reproducir
                String audioData = "";
                if (msg.getAudioData() != null) {
                    audioData = Base64.getEncoder().encodeToString(msg.getAudioData());
                }
                agregarMensajeAudio(prefijo, msg.getTranscripcion(), audioData);
            }
        } catch (Exception e) {
            agregarMensajeSistema("[Error] Mensaje corrupto en BD local");
        }
    }
    
    private void toggleGrabacionAudio() {
        if (!grabando) {
            // Iniciar grabación
            try {
                audioRecorder = new com.universidad.chat.cliente.util.AudioRecorder();
                audioRecorder.startRecording();
                grabando = true;
                audioBtn.setText("⏹ Detener");
                audioBtn.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
                statusLabel.setText("Grabando audio...");
                agregarMensajeSistema("[Sistema] Grabación iniciada");
            } catch (Exception e) {
                statusLabel.setText("Error al iniciar grabación: " + e.getMessage());
                agregarMensajeSistema("[Error] No se pudo iniciar la grabación: " + e.getMessage());
            }
        } else {
            // Detener grabación y enviar
            try {
                byte[] audioData = audioRecorder.stopRecording();
                grabando = false;
                audioBtn.setText("🎤 Grabar");
                audioBtn.setStyle("");
                statusLabel.setText("Enviando audio...");
                agregarMensajeSistema("[Sistema] Grabación finalizada, enviando...");
                // Echo local inmediato con botón de reproducción
                try {
                    String b64 = Base64.getEncoder().encodeToString(audioData);
                    agregarMensajeAudio("Tú", "", b64);
                } catch (Exception ignored) {}
                
                // Guardar audio en BD antes de enviarlo al servidor
                try {
                    int userId = svc.getIdUsuario();
                    String audioBase64 = Base64.getEncoder().encodeToString(audioData);
                    if (destinoActual == Destino.CANAL) {
                        persistencia.guardarMensajeAudio(userId, null, idDestino, "", audioBase64);
                    } else {
                        persistencia.guardarMensajeAudio(userId, idDestino, null, "", audioBase64);
                    }
                } catch (Exception e) {
                    System.err.println("Error al guardar audio en BD: " + e.getMessage());
                    e.printStackTrace();
                }
                
                // Enviar audio según destino
                new Thread(() -> {
                    try {
                        if (destinoActual == Destino.USUARIO) {
                            var resultado = svc.enviarAudioAUsuario(idDestino, audioData).get();
                            Platform.runLater(() -> {
                                String mensaje = resultado.has("mensaje") ? resultado.get("mensaje").getAsString() : "Audio enviado";
                                statusLabel.setText(mensaje);
                                agregarMensajeSistema("[Tú - Audio enviado]");
                            });
                        } else if (destinoActual == Destino.CANAL) {
                            var resultado = svc.enviarAudioACanal(idDestino, audioData).get();
                            Platform.runLater(() -> {
                                String mensaje = resultado.has("mensaje") ? resultado.get("mensaje").getAsString() : "Audio enviado";
                                statusLabel.setText(mensaje);
                                agregarMensajeSistema("[Tú - Audio enviado al canal]");
                            });
                        }
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Error al enviar audio");
                            agregarMensajeSistema("[Error] No se pudo enviar el audio: " + ex.getMessage());
                        });
                    }
                }, "enviar-audio").start();
                
            } catch (Exception e) {
                grabando = false;
                audioBtn.setText("🎤 Grabar");
                audioBtn.setStyle("");
                statusLabel.setText("Error al detener grabación: " + e.getMessage());
                agregarMensajeSistema("[Error] Error al procesar el audio: " + e.getMessage());
            }
        }
    }

    /**
     * Agrega un mensaje de texto al chat.
     */
    private void agregarMensajeTexto(String remitente, String contenido) {
        mensajes.add(new MensajeItem(remitente, contenido));
        scrollToBottom();
    }
    
    /**
     * Muestra un mensaje del sistema SOLO en la barra de estado, no en la conversación.
     * Petición del usuario: ocultar los mensajes [Sistema] del chat.
     */
    private void agregarMensajeSistema(String contenido) {
        // Quitar prefijo [Sistema] si viene y mostrar en la barra inferior
        String limpio = contenido == null ? "" : contenido.replaceFirst("^\\[Sistema\\]\\s*", "");
        statusLabel.setText(limpio);
        // No agregar a la lista de mensajes para mantener la conversación limpia
    }
    
    /**
     * Limpia todos los mensajes del chat.
     */
    private void limpiarChat() {
        mensajes.clear();
    }
    
    /**
     * Scroll automático al último mensaje.
     */
    private void scrollToBottom() {
        if (!mensajes.isEmpty()) {
            Platform.runLater(() -> chatArea.scrollTo(mensajes.size() - 1));
        }
    }
    
    /**
     * Agrega un mensaje de audio al chat con botón de reproducción.
     * 
     * @param remitente El nombre del remitente
     * @param transcripcion La transcripción del audio (puede ser null o vacío)
     * @param audioDataBase64 Los bytes del audio codificados en Base64
     */
    private void agregarMensajeAudio(String remitente, String transcripcion, String audioDataBase64) {
        try {
            // Generar ID único para este audio
            String audioId = "audio_" + System.currentTimeMillis() + "_" + Math.random();
            
            // Decodificar datos de audio
            byte[] audioBytes = null;
            if (audioDataBase64 != null && !audioDataBase64.isEmpty()) {
                audioBytes = Base64.getDecoder().decode(audioDataBase64);
                audioDataMap.put(audioId, audioBytes);
                
                // Crear reproductor
                AudioPlayer player = new AudioPlayer();
                player.loadAudio(audioBytes);
                audioPlayers.put(audioId, player);
            }
            
            // Agregar mensaje de audio con botón
            MensajeItem audioMsg = new MensajeItem(remitente, audioId, audioBytes, transcripcion);
            mensajes.add(audioMsg);
            scrollToBottom();
            
        } catch (Exception e) {
            agregarMensajeSistema("[Error] Audio de " + remitente + " - Error al decodificar: " + e.getMessage());
        }
    }
    
    /**
     * Reproduce o pausa un audio desde un botón.
     * Este método actualiza el estado visual del botón.
     * 
     * @param audioId El ID del audio a reproducir
     * @param button El botón que activó la reproducción
     */
    private void reproducirAudioConBoton(String audioId, Button button) {
        try {
            AudioPlayer player = audioPlayers.get(audioId);
            if (player == null) {
                agregarMensajeSistema("[Error] Audio no disponible");
                return;
            }
            
            if (player.isPlaying()) {
                player.pause();
                button.setText("▶️ Reproducir");
                button.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            } else {
                // Al iniciar reproducción, registramos callback para volver a "Reproducir" al terminar
                player.setOnPlaybackComplete(() -> Platform.runLater(() -> {
                    button.setText("▶️ Reproducir");
                    button.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                }));
                player.play();
                button.setText("⏸️ Pausar");
                button.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
            }
            
        } catch (Exception e) {
            agregarMensajeSistema("[Error] No se pudo reproducir el audio: " + e.getMessage());
        }
    }
    
    /**
     * Reproduce o pausa un audio por su ID (para comandos /play).
     * 
     * @param audioId El ID completo del audio
     */
    public void reproducirAudio(String audioId) {
        try {
            AudioPlayer player = audioPlayers.get(audioId);
            if (player == null) {
                agregarMensajeSistema("[Error] Audio no encontrado");
                return;
            }
            
            if (player.isPlaying()) {
                player.pause();
                agregarMensajeSistema("[⏸️ Audio pausado]");
            } else if (player.isPaused()) {
                player.play();
                agregarMensajeSistema("[▶️ Reproduciendo...]");
            } else {
                player.play();
                agregarMensajeSistema("[▶️ Reproduciendo audio...]");
            }
            
        } catch (Exception e) {
            agregarMensajeSistema("[Error] No se pudo reproducir el audio: " + e.getMessage());
        }
    }
}


