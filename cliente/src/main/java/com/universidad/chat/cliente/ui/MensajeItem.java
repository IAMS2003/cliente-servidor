package com.universidad.chat.cliente.ui;

/**
 * Representa un mensaje en el chat con su tipo y datos asociados.
 */
public class MensajeItem {
    public enum TipoMensaje {
        TEXTO,
        AUDIO,
        SISTEMA
    }

    private final TipoMensaje tipo;
    private final String remitente;
    private final String contenido;
    private final String audioId;
    private final byte[] audioData;

    // Constructor para mensajes de texto
    public MensajeItem(String remitente, String contenido) {
        this.tipo = TipoMensaje.TEXTO;
        this.remitente = remitente;
        this.contenido = contenido;
        this.audioId = null;
        this.audioData = null;
    }

    // Constructor para mensajes de audio
    public MensajeItem(String remitente, String audioId, byte[] audioData, String transcripcion) {
        this.tipo = TipoMensaje.AUDIO;
        this.remitente = remitente;
        this.contenido = transcripcion != null && !transcripcion.isEmpty() ? transcripcion : "";
        this.audioId = audioId;
        this.audioData = audioData;
    }

    // Constructor para mensajes del sistema
    public static MensajeItem sistema(String contenido) {
        MensajeItem item = new MensajeItem("[Sistema]", contenido);
        return item;
    }

    public TipoMensaje getTipo() {
        return tipo;
    }

    public String getRemitente() {
        return remitente;
    }

    public String getContenido() {
        return contenido;
    }

    public String getAudioId() {
        return audioId;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    @Override
    public String toString() {
        if (tipo == TipoMensaje.AUDIO) {
            String msg = remitente + " [Audio]";
            if (contenido != null && !contenido.isEmpty()) {
                msg += ": " + contenido;
            }
            return msg;
        }
        return remitente + ": " + contenido;
    }
}
