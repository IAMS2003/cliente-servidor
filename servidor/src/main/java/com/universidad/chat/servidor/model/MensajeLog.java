package com.universidad.chat.servidor.model;

import java.time.LocalDateTime;

/**
 * Entidad MensajeLog para registro de mensajes en el servidor.
 */
public class MensajeLog {
    private int id;
    private int idEmisor;
    private int idReceptor;
    private Integer idCanal;
    private String contenido;
    private String tipoMensaje; // TEXT, AUDIO
    private String archivoAudio;
    private String transcripcion;
    private LocalDateTime fecha;
    private String servidorHost;
    private Integer servidorPuerto;

    public MensajeLog() {
    }

    public MensajeLog(int idEmisor, int idReceptor, Integer idCanal, 
                      String contenido, String tipoMensaje) {
        this.idEmisor = idEmisor;
        this.idReceptor = idReceptor;
        this.idCanal = idCanal;
        this.contenido = contenido;
        this.tipoMensaje = tipoMensaje;
        this.fecha = LocalDateTime.now();
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdEmisor() {
        return idEmisor;
    }

    public void setIdEmisor(int idEmisor) {
        this.idEmisor = idEmisor;
    }

    public int getIdReceptor() {
        return idReceptor;
    }

    public void setIdReceptor(int idReceptor) {
        this.idReceptor = idReceptor;
    }

    public Integer getIdCanal() {
        return idCanal;
    }

    public void setIdCanal(Integer idCanal) {
        this.idCanal = idCanal;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public String getTipoMensaje() {
        return tipoMensaje;
    }

    public void setTipoMensaje(String tipoMensaje) {
        this.tipoMensaje = tipoMensaje;
    }

    public String getArchivoAudio() {
        return archivoAudio;
    }

    public void setArchivoAudio(String archivoAudio) {
        this.archivoAudio = archivoAudio;
    }

    public String getTranscripcion() {
        return transcripcion;
    }

    public void setTranscripcion(String transcripcion) {
        this.transcripcion = transcripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getServidorHost() {
        return servidorHost;
    }

    public void setServidorHost(String servidorHost) {
        this.servidorHost = servidorHost;
    }

    public Integer getServidorPuerto() {
        return servidorPuerto;
    }

    public void setServidorPuerto(Integer servidorPuerto) {
        this.servidorPuerto = servidorPuerto;
    }

    @Override
    public String toString() {
        return "MensajeLog{" +
                "id=" + id +
                ", idEmisor=" + idEmisor +
                ", idReceptor=" + idReceptor +
                ", tipoMensaje='" + tipoMensaje + '\'' +
                ", fecha=" + fecha +
                '}';
    }
}
