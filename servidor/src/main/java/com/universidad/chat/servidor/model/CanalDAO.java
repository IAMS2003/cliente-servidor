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
     * Agregar usuario a canal (usuarios locales).
     * Para usuarios remotos, usar agregarUsuarioAceptado con parámetros de servidor.
     */
    public void agregarUsuario(int idCanal, int idUsuario) throws SQLException {
        String sql = "INSERT INTO canal_usuarios (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado) VALUES (?, ?, NULL, NULL, FALSE)";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            stmt.executeUpdate();
            
            logger.info("Usuario local {} agregado al canal {} (pendiente de aceptación)", idUsuario, idCanal);
        }
    }

    /**
     * Aceptar solicitud de usuario a canal (usuarios locales).
     */
    public void aceptarUsuario(int idCanal, int idUsuario) throws SQLException {
        String sql = "UPDATE canal_usuarios SET aceptado = TRUE WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL";
        
        logger.info("Intentando aceptar usuario local {} en canal {}...", idUsuario, idCanal);
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            int filasActualizadas = stmt.executeUpdate();
            
            if (filasActualizadas > 0) {
                logger.info("✓ Usuario local {} aceptado en canal {} - {} fila(s) actualizada(s)", idUsuario, idCanal, filasActualizadas);
                
                // Verificar el estado actual de la tabla
                String verificarSql = "SELECT id_usuario, aceptado, usuario_servidor_host FROM canal_usuarios WHERE id_canal = ?";
                try (PreparedStatement verStmt = conn.prepareStatement(verificarSql)) {
                    verStmt.setInt(1, idCanal);
                    ResultSet rs = verStmt.executeQuery();
                    logger.info("Estado actual de canal_usuarios para canal {}:", idCanal);
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        logger.info("  [{}] id_usuario={}, aceptado={}, servidor_host={}", 
                            count,
                            rs.getInt("id_usuario"),
                            rs.getBoolean("aceptado"),
                            rs.getString("usuario_servidor_host") != null ? rs.getString("usuario_servidor_host") : "NULL");
                    }
                }
            } else {
                logger.warn("✗ No se pudo aceptar usuario local {} en canal {} - UPDATE afectó 0 filas", idUsuario, idCanal);
                
                // Verificar si existe la relación
                String verificarSql = "SELECT id_usuario, aceptado, usuario_servidor_host, usuario_servidor_puerto FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ?";
                try (PreparedStatement verStmt = conn.prepareStatement(verificarSql)) {
                    verStmt.setInt(1, idCanal);
                    verStmt.setInt(2, idUsuario);
                    ResultSet rs = verStmt.executeQuery();
                    if (rs.next()) {
                        logger.warn("  La relación SÍ existe: aceptado={}, servidor_host={}, servidor_puerto={}", 
                            rs.getBoolean("aceptado"),
                            rs.getString("usuario_servidor_host"),
                            rs.getObject("usuario_servidor_puerto"));
                    } else {
                        logger.error("  La relación NO existe en la BD");
                    }
                }
            }
        }
    }

    /**
     * Rechazar invitación: eliminar relación pendiente.
     */
    /**
     * Rechazar invitación a un canal (para usuarios locales).
     */
    public void rechazarInvitacion(int idCanal, int idUsuario) throws SQLException {
        String sql = "DELETE FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL AND aceptado = FALSE";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            stmt.executeUpdate();
            
            logger.info("Usuario {} rechazó invitación al canal {}", idUsuario, idCanal);
        }
    }

    /**
     * Rechazar invitación a un canal (considera servidor para usuarios locales o remotos).
     * 
     * @param idCanal ID del canal
     * @param idUsuario ID del usuario
     * @param servidorHost Host del servidor (null para usuario local)
     * @param servidorPuerto Puerto del servidor (null para usuario local)
     */
    public void rechazarInvitacionConServidor(int idCanal, int idUsuario, String servidorHost, Integer servidorPuerto) throws SQLException {
        String sql;
        if (servidorHost == null) {
            // Usuario local
            sql = "DELETE FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL AND aceptado = FALSE";
        } else {
            // Usuario remoto
            sql = "DELETE FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host = ? AND usuario_servidor_puerto = ? AND aceptado = FALSE";
        }
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            if (servidorHost != null) {
                stmt.setString(3, servidorHost);
                stmt.setInt(4, servidorPuerto);
            }
            stmt.executeUpdate();
            
            logger.info("Usuario {} (servidor: {}:{}) rechazó invitación al canal {}", 
                idUsuario, 
                servidorHost != null ? servidorHost : "local", 
                servidorPuerto != null ? servidorPuerto : "N/A", 
                idCanal);
        }
    }

    /**
     * Verificar si un usuario es miembro aceptado de un canal.
     * Para usuarios locales únicamente (sin considerar servidor).
     */
    public boolean esMiembroAceptado(int idCanal, int idUsuario) throws SQLException {
        String sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL AND aceptado = TRUE";
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
     * Verificar si un usuario (local o remoto) es miembro aceptado de un canal.
     * Considera el servidor para diferenciar usuarios con mismo ID en diferentes servidores.
     * 
     * @param idCanal ID del canal
     * @param idUsuario ID del usuario
     * @param servidorHost Host del servidor (null para usuario local)
     * @param servidorPuerto Puerto del servidor (null para usuario local)
     */
    public boolean esMiembroAceptadoConServidor(int idCanal, int idUsuario, String servidorHost, Integer servidorPuerto) throws SQLException {
        String sql;
        if (servidorHost == null) {
            // Usuario local
            sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL AND aceptado = TRUE";
        } else {
            // Usuario remoto
            sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host = ? AND usuario_servidor_puerto = ? AND aceptado = TRUE";
        }
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            if (servidorHost != null) {
                stmt.setString(3, servidorHost);
                stmt.setInt(4, servidorPuerto);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Verificar si existe relación (aunque no aceptada) entre usuario y canal.
     * Para usuarios locales únicamente (sin considerar servidor).
     */
    public boolean existeRelacion(int idCanal, int idUsuario) throws SQLException {
        String sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL";
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
     * Verificar si existe relación (aunque no aceptada) entre usuario (local o remoto) y canal.
     * Considera el servidor para diferenciar usuarios con mismo ID en diferentes servidores.
     * 
     * @param idCanal ID del canal
     * @param idUsuario ID del usuario
     * @param servidorHost Host del servidor (null para usuario local)
     * @param servidorPuerto Puerto del servidor (null para usuario local)
     */
    public boolean existeRelacionConServidorPublic(int idCanal, int idUsuario, String servidorHost, Integer servidorPuerto) throws SQLException {
        return existeRelacionConServidor(idCanal, idUsuario, servidorHost, servidorPuerto);
    }

    /**
     * Obtener usuarios de un canal (solo IDs).
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
     * Obtener miembros de un canal con información completa del servidor.
     * Incluye tanto usuarios locales como remotos.
     */
    public List<MiembroCanal> obtenerMiembrosConServidor(int idCanal) throws SQLException {
        String sql = "SELECT id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado FROM canal_usuarios WHERE id_canal = ?";
        List<MiembroCanal> miembros = new ArrayList<>();
        
        logger.debug("Consultando miembros del canal {} en BD...", idCanal);
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            ResultSet rs = stmt.executeQuery();
            
            int contador = 0;
            while (rs.next()) {
                contador++;
                MiembroCanal miembro = new MiembroCanal();
                miembro.setIdUsuario(rs.getInt("id_usuario"));
                miembro.setServidorHost(rs.getString("usuario_servidor_host"));
                Integer puerto = rs.getObject("usuario_servidor_puerto", Integer.class);
                miembro.setServidorPuerto(puerto);
                miembro.setAceptado(rs.getBoolean("aceptado"));
                miembros.add(miembro);
                
                logger.debug("  [{}] Usuario ID={}, servidor={}:{}, aceptado={}", 
                    contador, 
                    miembro.getIdUsuario(),
                    miembro.getServidorHost() != null ? miembro.getServidorHost() : "NULL",
                    miembro.getServidorPuerto() != null ? miembro.getServidorPuerto() : "NULL",
                    miembro.isAceptado());
            }
            
            logger.debug("Total de {} miembros encontrados para canal {}", miembros.size(), idCanal);
        }
        
        return miembros;
    }

    /**
     * Obtener solo miembros aceptados de un canal con información del servidor.
     */
    public List<MiembroCanal> obtenerMiembrosAceptadosConServidor(int idCanal) throws SQLException {
        String sql = "SELECT id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado FROM canal_usuarios WHERE id_canal = ? AND aceptado = TRUE";
        List<MiembroCanal> miembros = new ArrayList<>();
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                MiembroCanal miembro = new MiembroCanal();
                miembro.setIdUsuario(rs.getInt("id_usuario"));
                miembro.setServidorHost(rs.getString("usuario_servidor_host"));
                Integer puerto = rs.getObject("usuario_servidor_puerto", Integer.class);
                miembro.setServidorPuerto(puerto);
                miembro.setAceptado(true);
                miembros.add(miembro);
            }
        }
        
        return miembros;
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

    /**
     * Registrar o actualizar un canal remoto localmente.
     * Si el canal ya existe con ese ID, no hace nada.
     * Si no existe, lo crea incluyendo información del servidor del creador.
     * 
     * @param idCanal ID del canal en el servidor remoto
     * @param nombreCanal Nombre del canal
     * @param idCreador ID del usuario creador en su servidor
     * @param creadorServidorHost Host del servidor del creador
     * @param creadorServidorPuerto Puerto del servidor del creador
     * @param esPrivado Si el canal es privado
     * @return true si se registró correctamente, false si ya existía
     */
    public boolean registrarCanalRemoto(int idCanal, String nombreCanal, int idCreador, 
                                       String creadorServidorHost, int creadorServidorPuerto, 
                                       boolean esPrivado) throws SQLException {
        // Verificar si el canal ya existe
        Canal canalExistente = obtenerPorId(idCanal);
        if (canalExistente != null) {
            logger.debug("Canal remoto {} ya existe localmente", idCanal);
            return false;
        }
        
        // Insertar el canal con el ID específico e información del servidor
        String sql = "INSERT INTO canales (id, nombre, id_creador, creador_servidor_host, creador_servidor_puerto, es_privado) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setString(2, nombreCanal);
            stmt.setInt(3, idCreador);
            stmt.setString(4, creadorServidorHost);
            stmt.setInt(5, creadorServidorPuerto);
            stmt.setBoolean(6, esPrivado);
            
            stmt.executeUpdate();
            logger.info("Canal remoto {} ('{}') registrado localmente con creador {}:{}:{}", 
                       idCanal, nombreCanal, idCreador, creadorServidorHost, creadorServidorPuerto);
            return true;
        } catch (SQLException e) {
            // Si falla por duplicado, ignorar
            if (e.getMessage().contains("Duplicate entry")) {
                logger.debug("Canal remoto {} ya existe (duplicate key)", idCanal);
                return false;
            }
            throw e;
        }
    }

    /**
     * Agregar usuario a canal como miembro aceptado (sin invitación previa).
     * Útil para canales remotos donde la aceptación ya ocurrió en otro servidor.
     * 
     * @param idCanal ID del canal
     * @param idUsuario ID del usuario en su servidor
     * @param usuarioServidorHost Host del servidor del usuario (null para usuarios locales)
     * @param usuarioServidorPuerto Puerto del servidor del usuario (null para usuarios locales)
     */
    public void agregarUsuarioAceptado(int idCanal, int idUsuario, String usuarioServidorHost, Integer usuarioServidorPuerto) throws SQLException {
        // Verificar si ya existe la relación (considerando el servidor)
        if (existeRelacionConServidor(idCanal, idUsuario, usuarioServidorHost, usuarioServidorPuerto)) {
            // Actualizar a aceptado
            aceptarUsuarioConServidor(idCanal, idUsuario, usuarioServidorHost, usuarioServidorPuerto);
            return;
        }
        
        // Insertar directamente como aceptado
        String sql = "INSERT INTO canal_usuarios (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado) VALUES (?, ?, ?, ?, TRUE)";
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            if (usuarioServidorHost != null) {
                stmt.setString(3, usuarioServidorHost);
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            if (usuarioServidorPuerto != null) {
                stmt.setInt(4, usuarioServidorPuerto);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            
            stmt.executeUpdate();
            
            if (usuarioServidorHost != null) {
                logger.info("Usuario remoto {}:{}:{} agregado como miembro aceptado del canal {}", 
                           idUsuario, usuarioServidorHost, usuarioServidorPuerto, idCanal);
            } else {
                logger.info("Usuario local {} agregado como miembro aceptado del canal {}", idUsuario, idCanal);
            }
        }
    }

    /**
     * Verificar si existe relación entre usuario y canal, considerando el servidor.
     */
    private boolean existeRelacionConServidor(int idCanal, int idUsuario, String usuarioServidorHost, Integer usuarioServidorPuerto) throws SQLException {
        String sql;
        if (usuarioServidorHost != null) {
            sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host = ? AND usuario_servidor_puerto = ?";
        } else {
            sql = "SELECT 1 FROM canal_usuarios WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL";
        }
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            if (usuarioServidorHost != null) {
                stmt.setString(3, usuarioServidorHost);
                stmt.setInt(4, usuarioServidorPuerto);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Aceptar usuario en canal, considerando el servidor.
     */
    private void aceptarUsuarioConServidor(int idCanal, int idUsuario, String usuarioServidorHost, Integer usuarioServidorPuerto) throws SQLException {
        String sql;
        if (usuarioServidorHost != null) {
            sql = "UPDATE canal_usuarios SET aceptado = TRUE WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host = ? AND usuario_servidor_puerto = ?";
        } else {
            sql = "UPDATE canal_usuarios SET aceptado = TRUE WHERE id_canal = ? AND id_usuario = ? AND usuario_servidor_host IS NULL";
        }
        
        try (Connection conn = conexionBD.getConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, idCanal);
            stmt.setInt(2, idUsuario);
            if (usuarioServidorHost != null) {
                stmt.setString(3, usuarioServidorHost);
                stmt.setInt(4, usuarioServidorPuerto);
            }
            stmt.executeUpdate();
            
            if (usuarioServidorHost != null) {
                logger.info("Usuario remoto {}:{}:{} aceptado en canal {}", 
                           idUsuario, usuarioServidorHost, usuarioServidorPuerto, idCanal);
            } else {
                logger.info("Usuario local {} aceptado en canal {}", idUsuario, idCanal);
            }
        }
    }
}
