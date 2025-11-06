package com.universidad.chat.servidor.model;

import java.time.LocalDateTime;

/**
 * Entidad Canal para la base de datos del servidor.
 */
public class Canal {
    private int id;
    private String nombre;
    private int idCreador;
    private boolean esPrivado;
    private LocalDateTime fechaCreacion;

    public Canal() {
    }

    public Canal(int id, String nombre, int idCreador, boolean esPrivado) {
        this.id = id;
        this.nombre = nombre;
        this.idCreador = idCreador;
        this.esPrivado = esPrivado;
        this.fechaCreacion = LocalDateTime.now();
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getIdCreador() {
        return idCreador;
    }

    public void setIdCreador(int idCreador) {
        this.idCreador = idCreador;
    }

    public boolean isEsPrivado() {
        return esPrivado;
    }

    public void setEsPrivado(boolean esPrivado) {
        this.esPrivado = esPrivado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    @Override
    public String toString() {
        return "Canal{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", esPrivado=" + esPrivado +
                '}';
    }
}
