package com.universidad.chat.cliente.dao;

import com.universidad.chat.cliente.model.MensajeLog;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MensajeLogDAO {
    private final Connection conn;

    public MensajeLogDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Crear un nuevo mensaje en la BD local.
     */
    public int crear(MensajeLog mensaje) throws SQLException {
        String sql = "INSERT INTO mensaje_log (id_servidor, id_emisor, id_receptor, id_canal, contenido, tipo_mensaje, audio_data, transcripcion) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // id_servidor puede ser null para mensajes creados localmente
            if (mensaje.getIdServidor() != null) {
                stmt.setInt(1, mensaje.getIdServidor());
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            
            stmt.setInt(2, mensaje.getIdEmisor());
            
            if (mensaje.getIdReceptor() != null) {
                stmt.setInt(3, mensaje.getIdReceptor());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            
            if (mensaje.getIdCanal() != null) {
                stmt.setInt(4, mensaje.getIdCanal());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            
            stmt.setString(5, mensaje.getContenido());
            stmt.setString(6, mensaje.getTipoMensaje());
            
            if (mensaje.getAudioData() != null) {
                stmt.setBytes(7, mensaje.getAudioData());
            } else {
                stmt.setNull(7, Types.BLOB);
            }
            
            stmt.setString(8, mensaje.getTranscripcion());
            
            int filasAfectadas = stmt.executeUpdate();
            
            if (filasAfectadas > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        mensaje.setId(id);
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Obtener mensajes entre dos usuarios (chat directo).
     */
    public List<MensajeLog> obtenerEntreUsuarios(int idUsuario1, int idUsuario2) throws SQLException {
        String sql = "SELECT * FROM mensaje_log " +
                     "WHERE id_canal IS NULL AND (" +
                     "  (id_emisor = ? AND id_receptor = ?) OR " +
                     "  (id_emisor = ? AND id_receptor = ?)" +
                     ") ORDER BY fecha ASC";
        
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public List<MensajeLog> listarPorCanal(int idCanal) throws SQLException {
        String sql = "SELECT * FROM mensaje_log WHERE id_canal = ? ORDER BY fecha ASC";
        List<MensajeLog> mensajes = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCanal);
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
        
        int idReceptor = rs.getInt("id_receptor");
        if (!rs.wasNull()) {
            mensaje.setIdReceptor(idReceptor);
        }
        
        int idCanal = rs.getInt("id_canal");
        if (!rs.wasNull()) {
            mensaje.setIdCanal(idCanal);
        }
        
        mensaje.setContenido(rs.getString("contenido"));
        mensaje.setTipoMensaje(rs.getString("tipo_mensaje"));
        mensaje.setAudioData(rs.getBytes("audio_data"));
        mensaje.setTranscripcion(rs.getString("transcripcion"));
        mensaje.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
        
        return mensaje;
    }
    
    /**
     * Obtener la fecha del último mensaje guardado (para sincronización).
     * @param idUsuario ID del usuario actual (para filtrar solo sus mensajes)
     * @return Timestamp del último mensaje, o null si no hay mensajes
     */
    public Timestamp obtenerFechaUltimoMensaje(int idUsuario) throws SQLException {
        String sql = "SELECT MAX(fecha) as ultima_fecha FROM mensaje_log " +
                     "WHERE id_emisor = ? OR id_receptor = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            stmt.setInt(2, idUsuario);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getTimestamp("ultima_fecha");
            }
        }
        
        return null;
    }
    
    /**
     * Verificar si un mensaje ya existe en la BD por su ID del servidor.
     * Esta es la forma más confiable de evitar duplicados.
     */
    public boolean existePorIdServidor(int idServidor) throws SQLException {
        String sql = "SELECT COUNT(*) FROM mensaje_log WHERE id_servidor = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idServidor);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }
    
    /**
     * Verificar si un mensaje ya existe en la BD (para evitar duplicados en sincronización).
     * Compara por emisor, receptor/canal, tipo y contenido exacto.
     */
    public boolean existeMensaje(int idEmisor, Integer idReceptor, Integer idCanal, 
                                  String tipoMensaje, String contenido, Timestamp fechaReferencia) throws SQLException {
        // Buscar mensaje idéntico por emisor, tipo, destino y contenido
        // NO usamos fecha para evitar problemas con sincronización
        String sql = "SELECT COUNT(*) FROM mensaje_log " +
                     "WHERE id_emisor = ? AND tipo_mensaje = ?";
        
        // Agregar condición de receptor o canal
        if (idCanal != null) {
            sql += " AND id_canal = ?";
        } else if (idReceptor != null) {
            sql += " AND id_receptor = ?";
        }
        
        // Comparar contenido (para mensajes de texto) o transcripción (para audio)
        // El parámetro 'contenido' puede contener la transcripción si es un mensaje de audio
        if (contenido != null && !contenido.isEmpty()) {
            if ("TEXTO".equals(tipoMensaje)) {
                sql += " AND contenido = ?";
            } else if ("AUDIO".equals(tipoMensaje)) {
                sql += " AND transcripcion = ?";
            }
        }
        
        // NOTA: No usamos rango de fecha para hacer la verificación más estricta
        // Si existe un mensaje con exactamente el mismo contenido del mismo emisor
        // al mismo destino, lo consideramos duplicado
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            stmt.setInt(paramIndex++, idEmisor);
            stmt.setString(paramIndex++, tipoMensaje);
            
            if (idCanal != null) {
                stmt.setInt(paramIndex++, idCanal);
            } else if (idReceptor != null) {
                stmt.setInt(paramIndex++, idReceptor);
            }
            
            // Establecer parámetro de contenido/transcripción si se agregó a la consulta
            if (contenido != null && !contenido.isEmpty()) {
                if ("TEXTO".equals(tipoMensaje) || "AUDIO".equals(tipoMensaje)) {
                    stmt.setString(paramIndex++, contenido);
                }
            }
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                return count > 0;
            }
        }
        
        return false;
    }
    
    /**
     * Contar total de mensajes en la BD local.
     */
    public int contarTodos() throws SQLException {
        String sql = "SELECT COUNT(*) FROM mensaje_log";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
}
