package com.universidad.chat.cliente.model;

import java.time.LocalDateTime;

public class MensajeLog {
    private int id;                  // ID en BD local (H2)
    private Integer idServidor;      // ID en BD del servidor (MySQL) - para evitar duplicados
    private int idEmisor;
    private Integer idReceptor;  // Nullable para mensajes de canal
    private Integer idCanal;     // Nullable para mensajes directos
    private String contenido;
    private String tipoMensaje;  // "TEXTO", "AUDIO", etc.
    private byte[] audioData;    // Bytes del audio
    private String transcripcion;
    private LocalDateTime fecha;

    public MensajeLog() {}

    public MensajeLog(int idEmisor, Integer idReceptor, Integer idCanal, String contenido, String tipoMensaje) {
        this.idEmisor = idEmisor;
        this.idReceptor = idReceptor;
        this.idCanal = idCanal;
        this.contenido = contenido;
        this.tipoMensaje = tipoMensaje;
    }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public Integer getIdServidor() { return idServidor; }
    public void setIdServidor(Integer idServidor) { this.idServidor = idServidor; }
    
    public int getIdEmisor() { return idEmisor; }
    public void setIdEmisor(int idEmisor) { this.idEmisor = idEmisor; }
    
    public Integer getIdReceptor() { return idReceptor; }
    public void setIdReceptor(Integer idReceptor) { this.idReceptor = idReceptor; }
    
    public Integer getIdCanal() { return idCanal; }
    public void setIdCanal(Integer idCanal) { this.idCanal = idCanal; }
    
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    
    public String getTipoMensaje() { return tipoMensaje; }
    public void setTipoMensaje(String tipoMensaje) { this.tipoMensaje = tipoMensaje; }
    
    public byte[] getAudioData() { return audioData; }
    public void setAudioData(byte[] audioData) { this.audioData = audioData; }
    
    public String getTranscripcion() { return transcripcion; }
    public void setTranscripcion(String transcripcion) { this.transcripcion = transcripcion; }
    
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
