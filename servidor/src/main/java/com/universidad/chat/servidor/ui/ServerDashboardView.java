package com.universidad.chat.servidor.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import com.universidad.chat.servidor.util.reportes.PDFExporter;

public class ServerDashboardView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(ServerDashboardView.class);

    private final ServidorAppIntegradoMainWrapper wrapper;

    private final TextArea logArea = new TextArea();
    private final Label estadoLabel = new Label("Iniciando...");
    private final Label conectadosLabel = new Label("Conectados: 0");
    private final AtomicInteger conectados = new AtomicInteger(0);
    private final TabPane tabs = new TabPane();
    private TableView<com.universidad.chat.servidor.model.Usuario> usuariosTable;
    private String fotoBase64Sel;
    // Tab Informes: referencias para refresco en vivo
    private TableView<com.universidad.chat.servidor.model.Usuario> tvUsuariosInf;
    private TableView<com.universidad.chat.servidor.model.Usuario> tvConInf;
    private TableView<com.universidad.chat.servidor.model.Canal> tvCanalesInf;
    private TableView<com.universidad.chat.servidor.model.Usuario> tvMiembrosInf;
    private TableView<com.universidad.chat.servidor.model.MensajeLog> tvAudioInf;
    private TableView<com.universidad.chat.servidor.model.MensajeLog> tvLogsInf;

    public ServerDashboardView(ServidorAppIntegradoMainWrapper wrapper) {
        this.wrapper = wrapper;
        setPadding(new Insets(12));

        // Top bar
        HBox top = new HBox(12);
        Button detenerBtn = new Button("Detener servidor");
        Button limpiarLogBtn = new Button("Limpiar Log");
        top.getChildren().addAll(new Text("Panel del Servidor"), estadoLabel, conectadosLabel, detenerBtn, limpiarLogBtn);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(4, 0, 8, 0));
        setTop(top);

        // Tabs center
        logArea.setEditable(false);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().addAll(crearTabUsuarios(), crearTabRegistro(), crearTabBroadcast(), crearTabInformes(), crearTabLog());
        setCenter(tabs);

        detenerBtn.setOnAction(e -> {
            wrapper.shutdown();
            estadoLabel.setText("Detenido");
            appendLog("Servidor detenido por el usuario");
            // Cerrar UI y terminar proceso
            Platform.exit();
            System.exit(0);
        });
        limpiarLogBtn.setOnAction(e -> logArea.clear());

        // Eventos del servidor a la UI
        ServerDashboardBus.bind(
                log -> logArea.appendText(log + "\n"),
                (id, nombre) -> {
                    int cc = conectados.incrementAndGet();
                    conectadosLabel.setText("Conectados: " + cc);
                    appendLog("Usuario conectado: " + nombre + " (" + id + ")");
                    // Auto-refresco de la lista de usuarios conectados
                    refreshUsuarios();
                },
                id -> {
                    int cc = conectados.decrementAndGet();
                    if (cc < 0) conectados.set(0);
                    conectadosLabel.setText("Conectados: " + Math.max(0, cc));
                    appendLog("Usuario desconectado: " + id);
                    // Auto-refresco de la lista de usuarios conectados
                    refreshUsuarios();
                }
        );

        // Suscripción a eventos de datos para auto-refresco en Informes
        ServerDashboardBus.bindDataRefresh(
            this::refreshInformesUsuarios,
            this::refreshInformesConectados,
            this::refreshInformesCanales,
            this::refreshInformesLogs,
            this::refreshInformesAudio,
            canalId -> {
                // Refrescar miembros sólo si hay un canal seleccionado o coincide
                if (tvCanalesInf != null) {
                    var sel = tvCanalesInf.getSelectionModel().getSelectedItem();
                    if (sel != null && (canalId == 0 || sel.getId() == canalId)) {
                        new Thread(() -> {
                            var miembros = wrapper.listarMiembrosCanal(sel.getId());
                            Platform.runLater(() -> tvMiembrosInf.setItems(FXCollections.observableArrayList(miembros)));
                        }, "ref-miembros-live-bg").start();
                    }
                }
            }
        );

        // Datos iniciales
        refreshUsuarios();
    }

    private Tab crearTabLog() {
        Tab t = new Tab("Log");
        t.setContent(logArea);
        return t;
    }

    private Tab crearTabUsuarios() {
        Tab t = new Tab("Usuarios");
        usuariosTable = new TableView<>();
        TableColumn<com.universidad.chat.servidor.model.Usuario, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getId()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, String> colUser = new TableColumn<>("Usuario");
        colUser.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, String> colIP = new TableColumn<>("IP");
        colIP.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDireccionIP()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, Boolean> colCon = new TableColumn<>("Conectado");
        colCon.setCellValueFactory(c -> new javafx.beans.property.SimpleBooleanProperty(c.getValue().isConectado()));
    usuariosTable.getColumns().addAll(colId, colUser, colIP, colCon);

        Button refresh = new Button("Refrescar");
        Button kick = new Button("Desconectar");
        HBox actions = new HBox(8, refresh, kick);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox cont = new VBox(8, actions, usuariosTable);
        cont.setPadding(new Insets(8));

        refresh.setOnAction(e -> refreshUsuarios());
        kick.setOnAction(e -> {
            var selected = usuariosTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                ServerDashboardBus.appendLog("Solicitando desconexión de usuario " + selected.getId());
                new Thread(() -> {
                    wrapper.forzarDesconexionUsuario(selected.getId());
                    Platform.runLater(this::refreshUsuarios);
                }, "kick-user-bg").start();
            }
        });

        t.setContent(cont);
        return t;
    }

    private Tab crearTabRegistro() {
        Tab t = new Tab("Registro clientes");
        GridPane form = new GridPane();
        form.setHgap(8); form.setVgap(8); form.setPadding(new Insets(12));
        Label lUser = new Label("Usuario:"); TextField fUser = new TextField();
        Label lEmail = new Label("Email:"); TextField fEmail = new TextField();
        Label lPass = new Label("Contraseña:"); PasswordField fPass = new PasswordField();
        Label lIP = new Label("IP (opcional):"); TextField fIP = new TextField();
        Label lFoto = new Label("Foto (opcional):");
        Button btnFoto = new Button("Seleccionar foto..."); Label lblFoto = new Label("Sin archivo");
        Button btn = new Button("Registrar"); Label status = new Label();
        status.setStyle("-fx-text-fill:#080;");

        form.addRow(0, lUser, fUser);
        form.addRow(1, lEmail, fEmail);
        form.addRow(2, lPass, fPass);
        form.addRow(3, lIP, fIP);
        form.addRow(4, lFoto, new HBox(8, btnFoto, lblFoto));
        form.add(btn, 1, 5);
        form.add(status, 1, 6);

        btnFoto.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Seleccionar foto de usuario");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                    new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
            );
            var file = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
            if (file != null) {
                try {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    fotoBase64Sel = Base64.getEncoder().encodeToString(bytes);
                    lblFoto.setText(file.getName() + " (" + bytes.length + " bytes)");
                } catch (Exception ex) {
                    lblFoto.setText("Error leyendo archivo");
                    fotoBase64Sel = null;
                }
            }
        });

        btn.setOnAction(e -> {
            String u = fUser.getText().trim();
            String em = fEmail.getText().trim();
            String pw = fPass.getText();
            String ip = fIP.getText().trim();
            if (u.isEmpty() || em.isEmpty() || pw.isEmpty()) {
                status.setText("Completa usuario, email y contraseña");
                status.setStyle("-fx-text-fill:#c00;");
                return;
            }
            status.setText("Registrando...");
            status.setStyle("-fx-text-fill:#555;");
            new Thread(() -> {
                int id = wrapper.registrarUsuario(u, em, pw, fotoBase64Sel, ip.isEmpty()?"127.0.0.1":ip);
                Platform.runLater(() -> {
                    if (id > 0) {
                        status.setText("Registrado OK, id=" + id);
                        status.setStyle("-fx-text-fill:#080;");
                        refreshUsuarios();
                    } else {
                        status.setText("Error al registrar (posible duplicado)");
                        status.setStyle("-fx-text-fill:#c00;");
                    }
                });
            }, "registro-bg").start();
        });

        t.setContent(form);
        return t;
    }

    private Tab crearTabBroadcast() {
        Tab t = new Tab("Broadcast");
        VBox cont = new VBox(8);
        cont.setPadding(new Insets(12));
        Label lbl = new Label("Mensaje a enviar:");
        TextArea ta = new TextArea();
        ta.setPromptText("Escribe el mensaje de difusión...");
        ta.setPrefRowCount(5);
        HBox botones = new HBox(8);
        Button btnUsuarios = new Button("A todos los usuarios");
        Button btnCanales = new Button("A todos los canales");
        botones.getChildren().addAll(btnUsuarios, btnCanales);

        Label status = new Label();
        status.setStyle("-fx-text-fill:#555;");

        btnUsuarios.setOnAction(e -> {
            String txt = ta.getText() != null ? ta.getText().trim() : "";
            if (txt.isEmpty()) {
                status.setText("Escribe un mensaje antes de enviar");
                status.setStyle("-fx-text-fill:#c00;");
                return;
            }
            status.setText("Enviando...");
            status.setStyle("-fx-text-fill:#555;");
            new Thread(() -> {
                wrapper.broadcastATodosUsuarios(txt);
                Platform.runLater(() -> {
                    appendLog("Broadcast a usuarios enviado");
                    status.setText("Enviado a usuarios");
                    status.setStyle("-fx-text-fill:#080;");
                });
            }, "broadcast-users-bg").start();
        });

        btnCanales.setOnAction(e -> {
            String txt = ta.getText() != null ? ta.getText().trim() : "";
            if (txt.isEmpty()) {
                status.setText("Escribe un mensaje antes de enviar");
                status.setStyle("-fx-text-fill:#c00;");
                return;
            }
            status.setText("Enviando...");
            status.setStyle("-fx-text-fill:#555;");
            new Thread(() -> {
                wrapper.broadcastATodosCanales(txt);
                Platform.runLater(() -> {
                    appendLog("Broadcast a canales enviado");
                    status.setText("Enviado a canales");
                    status.setStyle("-fx-text-fill:#080;");
                });
            }, "broadcast-channels-bg").start();
        });

        cont.getChildren().addAll(lbl, ta, botones, status);
        t.setContent(cont);
        return t;
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> logArea.appendText("[" + LocalDateTime.now() + "] " + msg + "\n"));
    }

    private void refreshUsuarios() {
        new Thread(() -> {
            var conectadosList = wrapper.listarUsuariosConectados();
            Platform.runLater(() -> {
                usuariosTable.setItems(FXCollections.observableArrayList(conectadosList));
                conectados.set(conectadosList.size());
                conectadosLabel.setText("Conectados: " + conectados.get());
            });
        }, "refresh-usuarios-bg").start();
    }

    private Tab crearTabInformes() {
        Tab t = new Tab("Informes");
        TabPane subTabs = new TabPane();
        subTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    // Usuarios Registrados
        TableColumn<com.universidad.chat.servidor.model.Usuario, Number> uId = new TableColumn<>("ID");
        uId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getId()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, String> uNom = new TableColumn<>("Usuario");
        uNom.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, String> uEmail = new TableColumn<>("Email");
        uEmail.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getEmail()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, Boolean> uCon = new TableColumn<>("Conectado");
        uCon.setCellValueFactory(c -> new javafx.beans.property.SimpleBooleanProperty(c.getValue().isConectado()));
        tvUsuariosInf = new TableView<>();
    tvUsuariosInf.getColumns().add(uId);
    tvUsuariosInf.getColumns().add(uNom);
    tvUsuariosInf.getColumns().add(uEmail);
    tvUsuariosInf.getColumns().add(uCon);
        Button refUsuarios = new Button("Refrescar");
        Button expUsuarios = new Button("Exportar PDF");
        refUsuarios.setOnAction(e -> refreshInformesUsuarios());
        VBox paneUsuarios = new VBox(8, new HBox(8, refUsuarios, expUsuarios), tvUsuariosInf);
        expUsuarios.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar informe de usuarios");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            var f = fc.showSaveDialog(getScene()!=null?getScene().getWindow():null);
            if (f != null) {
                new Thread(() -> {
                    try {
                        var datos = wrapper.listarUsuarios();
                        PDFExporter.exportUsuarios(datos, f.toPath());
                        Platform.runLater(() -> appendLog("PDF usuarios exportado: " + f.getAbsolutePath()));
                    } catch (Exception ex) {
                        Platform.runLater(() -> appendLog("Error exportando PDF usuarios: " + ex.getMessage()));
                    }
                }, "exp-usuarios-bg").start();
            }
        });
    paneUsuarios.setPadding(new Insets(8));
        Tab stUsuarios = new Tab("Usuarios"); stUsuarios.setContent(paneUsuarios);

    // Usuarios Conectados
        TableColumn<com.universidad.chat.servidor.model.Usuario, Number> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getId()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, String> cNom = new TableColumn<>("Usuario");
        cNom.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, String> cIP = new TableColumn<>("IP");
        cIP.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDireccionIP()));
        tvConInf = new TableView<>();
    tvConInf.getColumns().add(cId);
    tvConInf.getColumns().add(cNom);
    tvConInf.getColumns().add(cIP);
        Button refCon = new Button("Refrescar");
        Button expCon = new Button("Exportar PDF");
        refCon.setOnAction(e -> refreshInformesConectados());
        VBox paneCon = new VBox(8, new HBox(8, refCon, expCon), tvConInf);
        expCon.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar informe de conectados");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            var f = fc.showSaveDialog(getScene()!=null?getScene().getWindow():null);
            if (f != null) {
                new Thread(() -> {
                    try {
                        var datos = wrapper.listarUsuariosConectados();
                        PDFExporter.exportConectados(datos, f.toPath());
                        Platform.runLater(() -> appendLog("PDF conectados exportado: " + f.getAbsolutePath()));
                    } catch (Exception ex) {
                        Platform.runLater(() -> appendLog("Error exportando PDF conectados: " + ex.getMessage()));
                    }
                }, "exp-conectados-bg").start();
            }
        });
    paneCon.setPadding(new Insets(8));
        Tab stCon = new Tab("Conectados"); stCon.setContent(paneCon);

        // Canales y miembros
        SplitPane canalesSplit = new SplitPane();
        canalesSplit.setDividerPositions(0.4);
        
        TableColumn<com.universidad.chat.servidor.model.Canal, Number> caId = new TableColumn<>("ID");
        caId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getId()));
        TableColumn<com.universidad.chat.servidor.model.Canal, String> caNom = new TableColumn<>("Canal");
        caNom.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNombre()));
        TableColumn<com.universidad.chat.servidor.model.Canal, Boolean> caPriv = new TableColumn<>("Privado");
        caPriv.setCellValueFactory(c -> new javafx.beans.property.SimpleBooleanProperty(c.getValue().isEsPrivado()));
        tvCanalesInf = new TableView<>();
    tvCanalesInf.getColumns().add(caId);
    tvCanalesInf.getColumns().add(caNom);
    tvCanalesInf.getColumns().add(caPriv);
        tvMiembrosInf = new TableView<>();
        TableColumn<com.universidad.chat.servidor.model.Usuario, Number> mId = new TableColumn<>("ID");
        mId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getId()));
        TableColumn<com.universidad.chat.servidor.model.Usuario, String> mNom = new TableColumn<>("Usuario");
        mNom.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNombreUsuario()));
    tvMiembrosInf.getColumns().add(mId);
    tvMiembrosInf.getColumns().add(mNom);
        tvCanalesInf.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                new Thread(() -> {
                    var miembros = wrapper.listarMiembrosCanal(sel.getId());
                    Platform.runLater(() -> tvMiembrosInf.setItems(FXCollections.observableArrayList(miembros)));
                }, "ref-miembros-bg").start();
            } else {
                tvMiembrosInf.getItems().clear();
            }
        });
        Button refCan = new Button("Refrescar");
        Button expCan = new Button("Exportar PDF");
        refCan.setOnAction(e -> refreshInformesCanales());
        expCan.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar informe de canales y miembros");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            var f = fc.showSaveDialog(getScene()!=null?getScene().getWindow():null);
            if (f != null) {
                new Thread(() -> {
                    try {
                        var canales = wrapper.listarCanales();
                        PDFExporter.exportCanalesYMiembros(canales, id -> wrapper.listarMiembrosCanal(id), f.toPath());
                        Platform.runLater(() -> appendLog("PDF canales exportado: " + f.getAbsolutePath()));
                    } catch (Exception ex) {
                        Platform.runLater(() -> appendLog("Error exportando PDF canales: " + ex.getMessage()));
                    }
                }, "exp-canales-bg").start();
            }
        });
        VBox left = new VBox(8, new HBox(8, refCan, expCan), tvCanalesInf); left.setPadding(new Insets(8));
        VBox right = new VBox(8, new Label("Miembros"), tvMiembrosInf); right.setPadding(new Insets(8));
        canalesSplit.getItems().addAll(left, right);
        Tab stCanales = new Tab("Canales"); stCanales.setContent(canalesSplit);

        // Mensajes de audio (con transcripción)
        tvAudioInf = new TableView<>();
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, Number> aId = new TableColumn<>("ID");
        aId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getId()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, Number> aEm = new TableColumn<>("Emisor");
        aEm.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getIdEmisor()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, Number> aRec = new TableColumn<>("Receptor");
        aRec.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getIdReceptor()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, String> aRuta = new TableColumn<>("Archivo");
        aRuta.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getArchivoAudio()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, String> aTrans = new TableColumn<>("Transcripción");
        aTrans.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTranscripcion()));
    tvAudioInf.getColumns().add(aId);
    tvAudioInf.getColumns().add(aEm);
    tvAudioInf.getColumns().add(aRec);
    tvAudioInf.getColumns().add(aRuta);
    tvAudioInf.getColumns().add(aTrans);
        Button refAudio = new Button("Refrescar");
        Button expAudio = new Button("Exportar PDF");
        refAudio.setOnAction(e -> refreshInformesAudio());
        VBox paneAudio = new VBox(8, new HBox(8, refAudio, expAudio), tvAudioInf);
        expAudio.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar informe de audio");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            var f = fc.showSaveDialog(getScene()!=null?getScene().getWindow():null);
            if (f != null) {
                new Thread(() -> {
                    try {
                        var datos = wrapper.listarLogsAudio();
                        PDFExporter.exportAudioLogs(datos, f.toPath());
                        Platform.runLater(() -> appendLog("PDF audio exportado: " + f.getAbsolutePath()));
                    } catch (Exception ex) {
                        Platform.runLater(() -> appendLog("Error exportando PDF audio: " + ex.getMessage()));
                    }
                }, "exp-audio-bg").start();
            }
        });
    paneAudio.setPadding(new Insets(8));
        Tab stAudio = new Tab("Audio"); stAudio.setContent(paneAudio);

        // Logs generales
        tvLogsInf = new TableView<>();
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, Number> lId = new TableColumn<>("ID");
        lId.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getId()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, String> lTipo = new TableColumn<>("Tipo");
        lTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTipoMensaje()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, String> lCont = new TableColumn<>("Contenido");
        lCont.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getContenido()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, Number> lEm = new TableColumn<>("Emisor");
        lEm.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getIdEmisor()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, Number> lRec = new TableColumn<>("Receptor");
        lRec.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getIdReceptor()));
        TableColumn<com.universidad.chat.servidor.model.MensajeLog, Number> lCan = new TableColumn<>("Canal");
        lCan.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getIdCanal() == null ? 0 : c.getValue().getIdCanal()));
    tvLogsInf.getColumns().add(lId);
    tvLogsInf.getColumns().add(lTipo);
    tvLogsInf.getColumns().add(lCont);
    tvLogsInf.getColumns().add(lEm);
    tvLogsInf.getColumns().add(lRec);
    tvLogsInf.getColumns().add(lCan);
        Button refLogs = new Button("Refrescar");
        Button expLogs = new Button("Exportar PDF");
        refLogs.setOnAction(e -> refreshInformesLogs());
        VBox paneLogs = new VBox(8, new HBox(8, refLogs, expLogs), tvLogsInf);
        expLogs.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar informe de logs");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            var f = fc.showSaveDialog(getScene()!=null?getScene().getWindow():null);
            if (f != null) {
                new Thread(() -> {
                    try {
                        var datos = wrapper.listarLogs();
                        PDFExporter.exportLogs(datos, f.toPath());
                        Platform.runLater(() -> appendLog("PDF logs exportado: " + f.getAbsolutePath()));
                    } catch (Exception ex) {
                        Platform.runLater(() -> appendLog("Error exportando PDF logs: " + ex.getMessage()));
                    }
                }, "exp-logs-bg").start();
            }
        });
    paneLogs.setPadding(new Insets(8));
        Tab stLogs = new Tab("Logs"); stLogs.setContent(paneLogs);

        subTabs.getTabs().addAll(stUsuarios, stCon, stCanales, stAudio, stLogs);
        t.setContent(subTabs);

        // Auto-refresco al entrar a la sección de Informes
        // Auto-refresco al entrar a la sección de Informes
        t.setOnSelectionChanged(ev -> {
            if (t.isSelected()) {
                refreshInformesUsuarios();
                refreshInformesConectados();
                refreshInformesCanales();
                refreshInformesAudio();
                refreshInformesLogs();
            }
        });
        return t;
    }

    // Métodos de refresco en vivo para Informes
    private void refreshInformesUsuarios() {
        if (tvUsuariosInf == null) return;
        new Thread(() -> {
            var datos = wrapper.listarUsuarios();
            Platform.runLater(() -> tvUsuariosInf.setItems(FXCollections.observableArrayList(datos)));
        }, "ref-usuarios-live").start();
    }
    private void refreshInformesConectados() {
        if (tvConInf == null) return;
        new Thread(() -> {
            var datos = wrapper.listarUsuariosConectados();
            Platform.runLater(() -> tvConInf.setItems(FXCollections.observableArrayList(datos)));
        }, "ref-conectados-live").start();
    }
    private void refreshInformesCanales() {
        if (tvCanalesInf == null) return;
        new Thread(() -> {
            var datos = wrapper.listarCanales();
            Platform.runLater(() -> tvCanalesInf.setItems(FXCollections.observableArrayList(datos)));
            // Si hay selección, refrescar miembros
            var sel = tvCanalesInf.getSelectionModel().getSelectedItem();
            if (sel != null) {
                new Thread(() -> {
                    var miembros = wrapper.listarMiembrosCanal(sel.getId());
                    Platform.runLater(() -> tvMiembrosInf.setItems(FXCollections.observableArrayList(miembros)));
                }, "ref-miembros-live").start();
            }
        }, "ref-canales-live").start();
    }
    private void refreshInformesAudio() {
        if (tvAudioInf == null) return;
        new Thread(() -> {
            var datos = wrapper.listarLogsAudio();
            Platform.runLater(() -> tvAudioInf.setItems(FXCollections.observableArrayList(datos)));
        }, "ref-audio-live").start();
    }
    private void refreshInformesLogs() {
        if (tvLogsInf == null) return;
        new Thread(() -> {
            var datos = wrapper.listarLogs();
            Platform.runLater(() -> tvLogsInf.setItems(FXCollections.observableArrayList(datos)));
        }, "ref-logs-live").start();
    }
}
