package com.universidad.chat.cliente.dao;

import com.universidad.chat.cliente.model.Canal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CanalDAO {
    private final Connection conn;

    public CanalDAO(Connection conn) {
        this.conn = conn;
    }

    public void crear(Canal canal) throws SQLException {
        String sql = "INSERT INTO canal (nombre, descripcion) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, canal.getNombre());
            stmt.setString(2, canal.getDescripcion());
            stmt.executeUpdate();
        }
    }

    public Canal obtenerPorId(int id) throws SQLException {
        String sql = "SELECT * FROM canal WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Canal(
                    rs.getInt("id"),
                    rs.getString("nombre"),
                    rs.getString("descripcion")
                );
            }
        }
        return null;
    }

    public List<Canal> listar() throws SQLException {
        List<Canal> canales = new ArrayList<>();
        String sql = "SELECT * FROM canal";
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                canales.add(new Canal(
                    rs.getInt("id"),
                    rs.getString("nombre"),
                    rs.getString("descripcion")
                ));
            }
        }
        return canales;
    }
}
