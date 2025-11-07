package com.universidad.chat.servidor.model;

import com.universidad.chat.servidor.config.ConexionBD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestión de canales en el servidor.
 */
public class CanalDAO {
    private static final Logger logger = LoggerFactory.getLogger(CanalDAO.class);
    private final ConexionBD conexionBD;

    public CanalDAO() {
        this.conexionBD = ConexionBD.getInstancia();
    }

    /**
     * Crear un nuevo canal.
     */
    public int crear(Canal canal) throws SQLException {
        String sql = "INSERT INTO canales (nombre, id_creador, es_privado) VALUES (?, ?, ?)";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, canal.getNombre());
            stmt.setInt(2, canal.getIdCreador());
            stmt.setBoolean(3, canal.isEsPrivado());
            
            int filasAfectadas = stmt.executeUpdate();
            
            if (filasAfectadas > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        canal.setId(id);
                        logger.info("Canal creado: {}", canal.getNombre());
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Obtener canal por ID.
     */
    public Canal obtenerPorId(int id) throws SQLException {
        String sql = "SELECT * FROM canales WHERE id = ?";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapearCanal(rs);
            }
        }
        return null;
    }

    /**
     * Obtener todos los canales.
     */
    public List<Canal> obtenerTodos() throws SQLException {
        String sql = "SELECT * FROM canales ORDER BY nombre";
        List<Canal> canales = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                canales.add(mapearCanal(rs));
            }
        }
        
        return canales;
    }

    /**
     * Obtener canales donde el usuario es miembro aceptado.
     */
    public List<Canal> obtenerCanalesDeUsuario(int idUsuario) throws SQLException {
        String sql = """
            SELECT c.* FROM canales c
            INNER JOIN canal_usuarios cu ON c.id = cu.id_canal
            WHERE cu.id_usuario = ? AND cu.aceptado = TRUE
            ORDER BY c.nombre
            """;
        List<Canal> canales = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                canales.add(mapearCanal(rs));
            }
        }
        
        return canales;
    }

    /**
     * Agregar usuario a canal.
     */
    public void agregarUsuario(int idCanal, int idUsuario) throws SQLException {
        String sql = "INSERT INTO canal_usuarios (id_canal, id_usuario, aceptado) VALUES (?, ?, FALSE)";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            stmt.executeUpdate();
            
            logger.info("Usuario {} agregado al canal {}", idUsuario, idCanal);
        }
    }

    /**
     * Aceptar solicitud de usuario a canal.
     */
    public void aceptarUsuario(int idCanal, int idUsuario) throws SQLException {
        String sql = "UPDATE canal_usuarios SET aceptado = TRUE WHERE id_canal = ? AND id_usuario = ?";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            stmt.executeUpdate();
            
            logger.info("Usuario {} aceptado en canal {}", idUsuario, idCanal);
        }
    }

    /**
     * Rechazar invitación: eliminar relación pendiente.
     */
    public void rechazarInvitacion(int idCanal, int idUsuario) throws SQLException {
        String sql = "DELETE FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND aceptado = FALSE";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            stmt.executeUpdate();
            
            logger.info("Usuario {} rechazó invitación al canal {}", idUsuario, idCanal);
        }
    }

    /**
     * Verificar si un usuario es miembro aceptado de un canal.
     */
    public boolean esMiembroAceptado(int idCanal, int idUsuario) throws SQLException {
        String sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND aceptado = TRUE";
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Verificar si existe relación (aunque no aceptada) entre usuario y canal.
     */
    public boolean existeRelacion(int idCanal, int idUsuario) throws SQLException {
        String sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ?";
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Obtener usuarios de un canal.
     */
    public List<Integer> obtenerUsuariosCanal(int idCanal) throws SQLException {
        String sql = "SELECT id_usuario FROM canal_usuarios WHERE id_canal = ? AND aceptado = TRUE";
        List<Integer> usuarios = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                usuarios.add(rs.getInt("id_usuario"));
            }
        }
        
        return usuarios;
    }

    /**
     * Obtener usuarios con invitación pendiente (no aceptados) de un canal.
     */
    public List<Integer> obtenerPendientesCanal(int idCanal) throws SQLException {
        String sql = "SELECT id_usuario FROM canal_usuarios WHERE id_canal = ? AND aceptado = FALSE";
        List<Integer> usuarios = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                usuarios.add(rs.getInt("id_usuario"));
            }
        }
        
        return usuarios;
    }

    /**
     * Mapear ResultSet a objeto Canal.
     */
    private Canal mapearCanal(ResultSet rs) throws SQLException {
        Canal canal = new Canal();
        canal.setId(rs.getInt("id"));
        canal.setNombre(rs.getString("nombre"));
        canal.setIdCreador(rs.getInt("id_creador"));
        canal.setEsPrivado(rs.getBoolean("es_privado"));
        canal.setFechaCreacion(rs.getTimestamp("fecha_creacion").toLocalDateTime());
        return canal;
    }
}
