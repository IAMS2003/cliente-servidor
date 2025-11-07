package com.universidad.chat.servidor.model;

/**
 * Representa un miembro de un canal con información de su servidor.
 * Usado para identificar usuarios en arquitectura P2P.
 */
public class MiembroCanal {
    private int idUsuario;
    private String servidorHost;  // null si es usuario local
    private Integer servidorPuerto; // null si es usuario local
    private boolean aceptado;

    public MiembroCanal() {
    }

    public MiembroCanal(int idUsuario, String servidorHost, Integer servidorPuerto, boolean aceptado) {
        this.idUsuario = idUsuario;
        this.servidorHost = servidorHost;
        this.servidorPuerto = servidorPuerto;
        this.aceptado = aceptado;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
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

    public boolean isAceptado() {
        return aceptado;
    }

    public void setAceptado(boolean aceptado) {
        this.aceptado = aceptado;
    }

    /**
     * Verifica si este miembro es un usuario local (sin información de servidor).
     */
    public boolean esLocal() {
        return servidorHost == null || servidorHost.isEmpty();
    }

    /**
     * Verifica si este miembro es un usuario remoto (con información de servidor).
     */
    public boolean esRemoto() {
        return !esLocal();
    }

    @Override
    public String toString() {
        if (esLocal()) {
            return "MiembroCanal{idUsuario=" + idUsuario + ", local, aceptado=" + aceptado + "}";
        } else {
            return "MiembroCanal{idUsuario=" + idUsuario + ", servidor=" + servidorHost + ":" + servidorPuerto + ", aceptado=" + aceptado + "}";
        }
    }
}
