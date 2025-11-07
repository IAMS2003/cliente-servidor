package com.universidad.chat.servidor.model;

import java.time.LocalDateTime;

/**
 * Entidad Usuario para la base de datos del servidor.
 */
public class Usuario {
    private int id;
    private String nombreUsuario;
    private String email;
    private String contrasena;
    private String foto;
    private String direccionIP;
    private Integer puertoConexion; // Puerto del socket remoto de la última conexión
    private String servidorHost; // servidor propietario del usuario
    private Integer servidorPuerto; // puerto del servidor propietario
    private boolean conectado;
    private LocalDateTime fechaRegistro;

    public Usuario() {
    }

    public Usuario(int id, String nombreUsuario, String email, String contrasena, 
                   String foto, String direccionIP) {
        this.id = id;
        this.nombreUsuario = nombreUsuario;
        this.email = email;
        this.contrasena = contrasena;
        this.foto = foto;
        this.direccionIP = direccionIP;
        this.conectado = false;
        this.fechaRegistro = LocalDateTime.now();
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public String getDireccionIP() {
        return direccionIP;
    }

    public void setDireccionIP(String direccionIP) {
        this.direccionIP = direccionIP;
    }

    public Integer getPuertoConexion() { return puertoConexion; }
    public void setPuertoConexion(Integer puertoConexion) { this.puertoConexion = puertoConexion; }

    public String getServidorHost() { return servidorHost; }
    public void setServidorHost(String servidorHost) { this.servidorHost = servidorHost; }
    public Integer getServidorPuerto() { return servidorPuerto; }
    public void setServidorPuerto(Integer servidorPuerto) { this.servidorPuerto = servidorPuerto; }

    public boolean isConectado() {
        return conectado;
    }

    public void setConectado(boolean conectado) {
        this.conectado = conectado;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + id +
                ", nombreUsuario='" + nombreUsuario + '\'' +
                ", email='" + email + '\'' +
    ", ip='" + direccionIP + ':' + (puertoConexion!=null?puertoConexion:"-") + '\'' +
        ", servidor=" + (servidorHost!=null?servidorHost:"-") + ':' + (servidorPuerto!=null?servidorPuerto:"-") +
                ", conectado=" + conectado +
                '}';
    }
}
