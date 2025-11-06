package com.universidad.chat.servidor.model;

import com.universidad.chat.servidor.config.ConexionBD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestión de logs de mensajes en el servidor.
 */
public class MensajeLogDAO {
    private static final Logger logger = LoggerFactory.getLogger(MensajeLogDAO.class);
    private final ConexionBD conexionBD;

    public MensajeLogDAO() {
        this.conexionBD = ConexionBD.getInstancia();
    }

    /**
     * Registrar un nuevo mensaje en el log.
     */
    public int registrar(MensajeLog mensaje) throws SQLException {
        String sql = "INSERT INTO mensajes_log (id_emisor, id_receptor, id_canal, contenido, tipo_mensaje, archivo_audio, transcripcion) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, mensaje.getIdEmisor());
            if (mensaje.getIdReceptor() > 0) {
                stmt.setInt(2, mensaje.getIdReceptor());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            if (mensaje.getIdCanal() != null) {
                stmt.setInt(3, mensaje.getIdCanal());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, mensaje.getContenido());
            stmt.setString(5, mensaje.getTipoMensaje());
            stmt.setString(6, mensaje.getArchivoAudio());
            stmt.setString(7, mensaje.getTranscripcion());
            
            logger.debug("Insertando mensaje - Tipo: {}, Archivo: {}, Transcripción: '{}'", 
                        mensaje.getTipoMensaje(), mensaje.getArchivoAudio(), mensaje.getTranscripcion());
            
            int filasAfectadas = stmt.executeUpdate();
            
            if (filasAfectadas > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        mensaje.setId(id);
                        logger.info("Mensaje registrado en log ID: {}", id);
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Obtener mensajes entre dos usuarios.
     */
    public List<MensajeLog> obtenerEntreUsuarios(int idUsuario1, int idUsuario2) throws SQLException {
        String sql = "SELECT * FROM mensajes_log " +
                     "WHERE (id_emisor = ? AND id_receptor = ?) OR (id_emisor = ? AND id_receptor = ?) " +
                     "ORDER BY fecha";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idUsuario1);
            stmt.setInt(2, idUsuario2);
            stmt.setInt(3, idUsuario2);
            stmt.setInt(4, idUsuario1);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                mensajes.add(mapearMensaje(rs));
            }
        }
        
        return mensajes;
    }

    /**
     * Obtener mensajes de un canal.
     */
    public List<MensajeLog> obtenerPorCanal(int idCanal) throws SQLException {
        String sql = "SELECT * FROM mensajes_log WHERE id_canal = ? ORDER BY fecha";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                mensajes.add(mapearMensaje(rs));
            }
        }
        
        return mensajes;
    }

    /**
     * Obtener todos los logs.
     */
    public List<MensajeLog> obtenerTodos() throws SQLException {
        String sql = "SELECT * FROM mensajes_log ORDER BY fecha DESC";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                mensajes.add(mapearMensaje(rs));
            }
        }
        
        return mensajes;
    }

    /**
     * Obtener mensajes de audio con transcripción.
     */
    public List<MensajeLog> obtenerMensajesAudio() throws SQLException {
        String sql = "SELECT * FROM mensajes_log WHERE tipo_mensaje = 'AUDIO' ORDER BY fecha DESC";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                mensajes.add(mapearMensaje(rs));
            }
        }
        
        return mensajes;
    }

    /**
     * Mapear ResultSet a objeto MensajeLog.
     */
    private MensajeLog mapearMensaje(ResultSet rs) throws SQLException {
        MensajeLog mensaje = new MensajeLog();
        mensaje.setId(rs.getInt("id"));
        mensaje.setIdEmisor(rs.getInt("id_emisor"));
        mensaje.setIdReceptor(rs.getInt("id_receptor"));
        
        Integer idCanal = rs.getInt("id_canal");
        if (!rs.wasNull()) {
            mensaje.setIdCanal(idCanal);
        }
        
        mensaje.setContenido(rs.getString("contenido"));
        mensaje.setTipoMensaje(rs.getString("tipo_mensaje"));
        mensaje.setArchivoAudio(rs.getString("archivo_audio"));
        mensaje.setTranscripcion(rs.getString("transcripcion"));
        mensaje.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
        return mensaje;
    }
}
