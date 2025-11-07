package com.universidad.chat.servidor.model;

import com.universidad.chat.servidor.config.ConexionBD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * DAO para tabla servidores (pares P2P).
 */
public class ServidorDAO {
    private static final Logger logger = LoggerFactory.getLogger(ServidorDAO.class);
    private final ConexionBD conexionBD;

    public ServidorDAO() { this.conexionBD = ConexionBD.getInstancia(); }

    public void upsert(String host, int puerto, String estado, java.time.LocalDateTime lastSeen) {
        String sqlInsert = "INSERT INTO servidores (host, puerto, estado, ultimo_heartbeat) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE estado = VALUES(estado), ultimo_heartbeat = VALUES(ultimo_heartbeat)";
        try (Connection c = conexionBD.getConexion(); PreparedStatement ps = c.prepareStatement(sqlInsert)) {
            ps.setString(1, host);
            ps.setInt(2, puerto);
            ps.setString(3, estado);
            if (lastSeen != null) ps.setTimestamp(4, Timestamp.valueOf(lastSeen)); else ps.setNull(4, Types.TIMESTAMP);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warn("No se pudo upsert servidor {}:{} -> {}", host, puerto, e.getMessage());
        }
    }

    /**
     * Listar todos los pares conocidos en BD como "host:puerto" para bootstrap.
     */
    public java.util.List<String> listarPeersHostPort() {
        String sql = "SELECT host, puerto FROM servidores";
        java.util.List<String> out = new java.util.ArrayList<>();
        try (Connection c = conexionBD.getConexion(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String h = rs.getString("host");
                int p = rs.getInt("puerto");
                if (h != null && !h.isBlank() && p > 0) out.add(h + ":" + p);
            }
        } catch (Exception e) {
            logger.warn("No se pudo listar pares desde BD: {}", e.getMessage());
        }
        return out;
    }
}
