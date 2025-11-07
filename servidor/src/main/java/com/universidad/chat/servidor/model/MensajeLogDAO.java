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
            
            // id_emisor: NULL para eventos del sistema (id_emisor <= 0) o usuarios remotos no registrados localmente
            if (mensaje.getIdEmisor() > 0) {
                // Verificar si el emisor existe localmente; si no, usar NULL
                if (usuarioExiste(conn, mensaje.getIdEmisor())) {
                    stmt.setInt(1, mensaje.getIdEmisor());
                } else {
                    stmt.setNull(1, Types.INTEGER);
                }
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            // id_receptor: NULL si no existe localmente (usuario remoto)
            if (mensaje.getIdReceptor() > 0) {
                if (usuarioExiste(conn, mensaje.getIdReceptor())) {
                    stmt.setInt(2, mensaje.getIdReceptor());
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
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
        String sql = "SELECT ml.*, u.servidor_host, u.servidor_puerto " +
                     "FROM mensajes_log ml " +
                     "LEFT JOIN usuarios u ON ml.id_emisor = u.id " +
                     "ORDER BY ml.fecha DESC";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                mensajes.add(mapearMensajeConServidor(rs));
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
     * Obtener mensajes donde un usuario específico es emisor o receptor.
     * Útil para sincronización cross-server.
     */
    public List<MensajeLog> obtenerMensajesDeUsuario(int idUsuario) throws SQLException {
        String sql = "SELECT * FROM mensajes_log " +
                     "WHERE id_emisor = ? OR id_receptor = ? " +
                     "ORDER BY fecha DESC";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idUsuario);
            stmt.setInt(2, idUsuario);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                mensajes.add(mapearMensaje(rs));
            }
        }
        
        return mensajes;
    }

    /**
     * Obtener mensajes recientes de un usuario (últimos N días).
     * Útil para sincronización incremental.
     */
    public List<MensajeLog> obtenerMensajesRecientesDeUsuario(int idUsuario, int dias) throws SQLException {
        String sql = "SELECT * FROM mensajes_log " +
                     "WHERE (id_emisor = ? OR id_receptor = ?) " +
                     "AND fecha >= DATE_SUB(NOW(), INTERVAL ? DAY) " +
                     "ORDER BY fecha DESC";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idUsuario);
            stmt.setInt(2, idUsuario);
            stmt.setInt(3, dias);
            ResultSet rs = stmt.executeQuery();
            
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

    /**
     * Mapear ResultSet a objeto MensajeLog incluyendo información del servidor.
     */
    private MensajeLog mapearMensajeConServidor(ResultSet rs) throws SQLException {
        MensajeLog mensaje = mapearMensaje(rs);
        try {
            String host = rs.getString("servidor_host");
            if (host != null && !rs.wasNull()) {
                mensaje.setServidorHost(host);
            }
        } catch (SQLException ignored) {}
        try {
            int puerto = rs.getInt("servidor_puerto");
            if (!rs.wasNull()) {
                mensaje.setServidorPuerto(puerto);
            }
        } catch (SQLException ignored) {}
        return mensaje;
    }

    /**
     * Verifica si un usuario existe en la tabla usuarios (para FK validation).
     */
    private boolean usuarioExiste(Connection conn, int idUsuario) {
        String sql = "SELECT 1 FROM usuarios WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Verifica si un mensaje ya existe en la base de datos basándose en sus características únicas.
     * Útil para evitar duplicados al recibir mensajes de servidores remotos.
     */
    public boolean mensajeExiste(int idEmisor, int idReceptor, Integer idCanal, String contenido) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM mensajes_log WHERE ");
        List<Object> params = new ArrayList<>();
        
        // Construir query dinámicamente según los parámetros
        sql.append("id_emisor = ? AND ");
        params.add(idEmisor);
        
        if (idReceptor > 0) {
            sql.append("id_receptor = ? AND ");
            params.add(idReceptor);
        } else {
            sql.append("id_receptor IS NULL AND ");
        }
        
        if (idCanal != null) {
            sql.append("id_canal = ? AND ");
            params.add(idCanal);
        } else {
            sql.append("id_canal IS NULL AND ");
        }
        
        sql.append("contenido = ? AND ");
        params.add(contenido);
        
        // Verificar en las últimas 24 horas para evitar duplicados recientes
        sql.append("fecha >= DATE_SUB(NOW(), INTERVAL 24 HOUR) LIMIT 1");
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Integer) {
                    ps.setInt(i + 1, (Integer) param);
                } else if (param instanceof String) {
                    ps.setString(i + 1, (String) param);
                }
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
