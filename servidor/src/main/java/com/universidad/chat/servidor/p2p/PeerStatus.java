package com.universidad.chat.servidor.p2p;

import java.time.LocalDateTime;

/**
 * Estado de un servidor par (peer) en la red P2P.
 */
public class PeerStatus {
    private String host;
    private int port;
    private LocalDateTime lastSeen;
    private String estado; // ONLINE / OFFLINE / UNKNOWN

    public PeerStatus() {}

    public PeerStatus(String host, int port, LocalDateTime lastSeen, String estado) {
        this.host = host; this.port = port; this.lastSeen = lastSeen; this.estado = estado;
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
