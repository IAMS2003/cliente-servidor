package com.universidad.chat.servidor.service;

import com.universidad.chat.servidor.model.Usuario;
import com.universidad.chat.servidor.model.UsuarioDAO;
import com.universidad.chat.servidor.config.ServidorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;

/**
 * Servicio para gestión de usuarios en el servidor.
 * Aplica principio SRP (Single Responsibility Principle).
 */
public class UsuarioService {
    private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);
    private final UsuarioDAO usuarioDAO;
    private final ServidorConfig servidorConfig;

    public UsuarioService() {
    this.usuarioDAO = new UsuarioDAO();
    this.servidorConfig = new ServidorConfig();
    }

    /**
     * Registrar un nuevo usuario.
     */
    public int registrarUsuario(String nombreUsuario, String email, String contrasena, 
                                String foto, String direccionIP) {
        try {
            // Verificar si el usuario ya existe
            Usuario existente = usuarioDAO.obtenerPorNombre(nombreUsuario);
            if (existente != null) {
                logger.warn("Intento de registro de usuario existente: {}", nombreUsuario);
                // -2: nombre de usuario duplicado
                return -2;
            }

            // Guardar foto en disco si viene en Base64
            String fotoPath = null;
            if (foto != null && !foto.isBlank()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(foto);
                    Path dir = Path.of("uploads", "fotos");
                    if (!Files.exists(dir)) Files.createDirectories(dir);
                    String safeName = nombreUsuario.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String fileName = System.currentTimeMillis() + "_" + safeName + ".img";
                    Path destino = dir.resolve(fileName);
                    Files.write(destino, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    fotoPath = destino.toString();
                } catch (Exception ex) {
                    logger.warn("No se pudo guardar la foto para {}: {}", nombreUsuario, ex.getMessage());
                    fotoPath = null; // continuar sin foto
                }
            }

            // Crear nuevo usuario
            Usuario nuevoUsuario = new Usuario(0, nombreUsuario, email, contrasena, fotoPath, direccionIP);
            // Afinidad de servidor: asignar en registro
            nuevoUsuario.setServidorHost(servidorConfig.getServerHost());
            nuevoUsuario.setServidorPuerto(servidorConfig.getServerPort());
            int id = usuarioDAO.crear(nuevoUsuario);
            
            if (id > 0) {
                logger.info("Usuario registrado exitosamente: {} (ID: {})", nombreUsuario, id);
            }
            
            return id;
        } catch (SQLException e) {
            // Diferenciar errores comunes (p. ej., duplicado de email por UNIQUE)
            String sqlState = safeSqlState(e);
            int vendorCode = safeVendorCode(e);
            // MySQL: SQLState 23000 y errorCode 1062 = Duplicate entry
            if ("23000".equals(sqlState) && vendorCode == 1062) {
                logger.warn("Registro falló por restricción UNIQUE (posible email duplicado)");
                return -3; // -3: email duplicado (u otra clave única)
            }
            logger.error("Error registrando usuario (SQLState={}, code={}): {}", sqlState, vendorCode, e.getMessage(), e);
            return -4; // -4: error general de BD
        }
    }

    private String safeSqlState(SQLException e) {
        try { return e.getSQLState(); } catch (Exception ex) { return null; }
    }
    private int safeVendorCode(SQLException e) {
        try { return e.getErrorCode(); } catch (Exception ex) { return -1; }
    }

    /**
     * Autenticar usuario.
     */
    public Usuario autenticar(String nombreUsuario, String contrasena) {
        try {
            Usuario usuario = usuarioDAO.autenticar(nombreUsuario, contrasena);
            if (usuario != null) {
                // Verificar afinidad con este servidor
                if (usuario.getServidorHost() == null || usuario.getServidorPuerto() == null) {
                    try {
                        usuarioDAO.asignarServidorSiNulo(usuario.getId(), servidorConfig.getServerHost(), servidorConfig.getServerPort());
                        usuario.setServidorHost(servidorConfig.getServerHost());
                        usuario.setServidorPuerto(servidorConfig.getServerPort());
                        logger.info("Afinidad de servidor asignada a {}:{} para usuario {}", servidorConfig.getServerHost(), servidorConfig.getServerPort(), nombreUsuario);
                    } catch (Exception ex) {
                        logger.warn("No se pudo asignar afinidad de servidor para {}: {}", nombreUsuario, ex.getMessage());
                    }
                } else {
                    boolean ok = servidorConfig.getServerHost().equals(usuario.getServidorHost()) && servidorConfig.getServerPort() == usuario.getServidorPuerto();
                    if (!ok) {
                        logger.warn("Inicio de sesión rechazado: usuario {} pertenece a servidor {}:{}", nombreUsuario, usuario.getServidorHost(), usuario.getServidorPuerto());
                        return null;
                    }
                }
                logger.info("Usuario autenticado: {}", nombreUsuario);
            } else {
                logger.warn("Intento de autenticación fallido: {}", nombreUsuario);
            }
            return usuario;
        } catch (SQLException e) {
            logger.error("Error autenticando usuario", e);
            return null;
        }
    }

    /**
     * Marcar usuario como conectado.
     */
    public boolean conectarUsuario(int idUsuario, String direccionIP) {
        try {
            usuarioDAO.actualizarEstadoConexion(idUsuario, true, direccionIP);
            logger.info("Usuario conectado ID: {} desde {}", idUsuario, direccionIP);
            return true;
        } catch (SQLException e) {
            logger.error("Error conectando usuario", e);
            return false;
        }
    }

    /**
     * Marcar usuario como desconectado.
     */
    public boolean desconectarUsuario(int idUsuario) {
        try {
            usuarioDAO.actualizarEstadoConexion(idUsuario, false, null);
            logger.info("Usuario desconectado ID: {}", idUsuario);
            return true;
        } catch (SQLException e) {
            logger.error("Error desconectando usuario", e);
            return false;
        }
    }

    /**
     * Obtener todos los usuarios registrados.
     */
    public List<Usuario> obtenerTodosLosUsuarios() {
        try {
            return usuarioDAO.obtenerTodos();
        } catch (SQLException e) {
            logger.error("Error obteniendo usuarios", e);
            return List.of();
        }
    }

    /**
     * Obtener usuarios conectados.
     */
    public List<Usuario> obtenerUsuariosConectados() {
        try {
            return usuarioDAO.obtenerConectados();
        } catch (SQLException e) {
            logger.error("Error obteniendo usuarios conectados", e);
            return List.of();
        }
    }

    /**
     * Obtener usuario por ID.
     */
    public Usuario obtenerUsuarioPorId(int id) {
        try {
            return usuarioDAO.obtenerPorId(id);
        } catch (SQLException e) {
            logger.error("Error obteniendo usuario por ID", e);
            return null;
        }
    }
}
