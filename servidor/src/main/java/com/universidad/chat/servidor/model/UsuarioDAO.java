package com.universidad.chat.servidor.model;

import com.universidad.chat.servidor.config.ConexionBD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestión de usuarios en el servidor.
 * Aplica patrón DAO (Data Access Object).
 */
public class UsuarioDAO {
    private static final Logger logger = LoggerFactory.getLogger(UsuarioDAO.class);
    private final ConexionBD conexionBD;

    public UsuarioDAO() {
        this.conexionBD = ConexionBD.getInstancia();
    }

    /**
     * Crear un nuevo usuario.
     */
    public int crear(Usuario usuario) throws SQLException {
        String sql = "INSERT INTO usuarios (nombre_usuario, email, contrasena, foto, direccion_ip) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, usuario.getNombreUsuario());
            stmt.setString(2, usuario.getEmail());
            stmt.setString(3, usuario.getContrasena());
            stmt.setString(4, usuario.getFoto());
            stmt.setString(5, usuario.getDireccionIP());
            
            int filasAfectadas = stmt.executeUpdate();
            
            if (filasAfectadas > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        usuario.setId(id);
                        logger.info("Usuario creado: {}", usuario.getNombreUsuario());
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Obtener usuario por ID.
     */
    public Usuario obtenerPorId(int id) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE id = ?";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapearUsuario(rs);
            }
        }
        return null;
    }

    /**
     * Obtener usuario por nombre de usuario.
     */
    public Usuario obtenerPorNombre(String nombreUsuario) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE nombre_usuario = ?";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, nombreUsuario);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapearUsuario(rs);
            }
        }
        return null;
    }

    /**
     * Obtener todos los usuarios registrados.
     */
    public List<Usuario> obtenerTodos() throws SQLException {
        String sql = "SELECT * FROM usuarios ORDER BY nombre_usuario";
        List<Usuario> usuarios = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                usuarios.add(mapearUsuario(rs));
            }
        }
        
        return usuarios;
    }

    /**
     * Obtener usuarios conectados.
     */
    public List<Usuario> obtenerConectados() throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE conectado = TRUE ORDER BY nombre_usuario";
        List<Usuario> usuarios = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                usuarios.add(mapearUsuario(rs));
            }
        }
        
        return usuarios;
    }

    /**
     * Actualizar estado de conexión del usuario.
     */
    public void actualizarEstadoConexion(int id, boolean conectado, String direccionIP) throws SQLException {
        String sql = "UPDATE usuarios SET conectado = ?, direccion_ip = ? WHERE id = ?";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBoolean(1, conectado);
            stmt.setString(2, direccionIP);
            stmt.setInt(3, id);
            stmt.executeUpdate();
            
            logger.info("Estado de conexión actualizado para usuario ID: {}", id);
        }
    }

    /**
     * Autenticar usuario.
     */
    public Usuario autenticar(String nombreUsuario, String contrasena) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE nombre_usuario = ? AND contrasena = ?";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, nombreUsuario);
            stmt.setString(2, contrasena);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapearUsuario(rs);
            }
        }
        return null;
    }

    /**
     * Eliminar usuario.
     */
    public boolean eliminar(int id) throws SQLException {
        String sql = "DELETE FROM usuarios WHERE id = ?";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            int filasAfectadas = stmt.executeUpdate();
            
            if (filasAfectadas > 0) {
                logger.info("Usuario eliminado ID: {}", id);
                return true;
            }
        }
        return false;
    }

    /**
     * Mapear ResultSet a objeto Usuario.
     */
    private Usuario mapearUsuario(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setId(rs.getInt("id"));
        usuario.setNombreUsuario(rs.getString("nombre_usuario"));
        usuario.setEmail(rs.getString("email"));
        usuario.setContrasena(rs.getString("contrasena"));
        usuario.setFoto(rs.getString("foto"));
        usuario.setDireccionIP(rs.getString("direccion_ip"));
        usuario.setConectado(rs.getBoolean("conectado"));
        usuario.setFechaRegistro(rs.getTimestamp("fecha_registro").toLocalDateTime());
        return usuario;
    }
}
