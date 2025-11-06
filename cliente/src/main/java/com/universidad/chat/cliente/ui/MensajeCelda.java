package com.universidad.chat.cliente.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.function.BiConsumer;

/**
 * Celda personalizada para mostrar mensajes en el chat.
 * Incluye botones de reproducción para mensajes de audio.
 */
public class MensajeCelda extends ListCell<MensajeItem> {
    private final BiConsumer<String, Button> onPlayAudio;

    public MensajeCelda(BiConsumer<String, Button> onPlayAudio) {
        this.onPlayAudio = onPlayAudio;
    }

    @Override
    protected void updateItem(MensajeItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        if (item.getTipo() == MensajeItem.TipoMensaje.AUDIO) {
            // Crear UI para mensaje de audio
            VBox container = new VBox(5);
            container.setPadding(new Insets(5));

            // Etiqueta del remitente
            Label remitenteLabel = new Label(item.getRemitente() + " [Audio]");
            remitenteLabel.setStyle("-fx-font-weight: bold;");

            // Transcripción si existe
            if (item.getContenido() != null && !item.getContenido().isEmpty()) {
                Text transcripcion = new Text(item.getContenido());
                transcripcion.setWrappingWidth(300);
                container.getChildren().add(transcripcion);
            }

            HBox buttonBox;
            if (item.getAudioData() == null || item.getAudioData().length == 0) {
                Button disabledBtn = new Button("Audio no disponible");
                disabledBtn.setDisable(true);
                disabledBtn.setStyle("-fx-opacity: 0.7;");
                buttonBox = new HBox(disabledBtn);
            } else {
                Button playBtn = new Button("▶️ Reproducir");
                playBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                playBtn.setOnAction(e -> {
                    if (onPlayAudio != null) {
                        onPlayAudio.accept(item.getAudioId(), playBtn);
                    }
                });
                buttonBox = new HBox(playBtn);
            }
            buttonBox.setPadding(new Insets(5, 0, 0, 0));

            container.getChildren().addAll(remitenteLabel, buttonBox);
            setText(null);
            setGraphic(container);

        } else {
            // Mensaje de texto normal
            Text text = new Text(item.toString());
            text.setWrappingWidth(350);
            VBox container = new VBox(text);
            container.setPadding(new Insets(2, 5, 2, 5));
            setText(null);
            setGraphic(container);
        }
    }
}
