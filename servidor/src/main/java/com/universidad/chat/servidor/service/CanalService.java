package com.universidad.chat.servidor.service;

import com.universidad.chat.servidor.model.Canal;
import com.universidad.chat.servidor.model.CanalDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Servicio para gestión de canales en el servidor.
 * Aplica principio SRP (Single Responsibility Principle).
 */
public class CanalService {
    private static final Logger logger = LoggerFactory.getLogger(CanalService.class);
    private final CanalDAO canalDAO;

    public CanalService() {
        this.canalDAO = new CanalDAO();
    }

    /**
     * Crear un nuevo canal.
     */
    public int crearCanal(String nombre, int idCreador, boolean esPrivado) {
        try {
            Canal nuevoCanal = new Canal(0, nombre, idCreador, esPrivado);
            int id = canalDAO.crear(nuevoCanal);
            
            if (id > 0) {
                // Agregar al creador como miembro y aceptarlo automáticamente
                canalDAO.agregarUsuario(id, idCreador);
                canalDAO.aceptarUsuario(id, idCreador);
                logger.info("Canal creado: {} (ID: {}) por usuario {}", nombre, id, idCreador);
            }
            
            return id;
        } catch (SQLException e) {
            logger.error("Error creando canal", e);
            return -1;
        }
    }

    /**
     * Solicitar unirse a un canal.
     */
    public boolean solicitarUnirse(int idCanal, int idUsuario) {
        try {
            canalDAO.agregarUsuario(idCanal, idUsuario);
            logger.info("Usuario {} solicitó unirse al canal {}", idUsuario, idCanal);
            return true;
        } catch (SQLException e) {
            logger.error("Error solicitando unirse a canal", e);
            return false;
        }
    }

    /**
     * Aceptar solicitud de usuario a canal.
     */
    public boolean aceptarSolicitud(int idCanal, int idUsuario) {
        try {
            canalDAO.aceptarUsuario(idCanal, idUsuario);
            logger.info("Usuario {} aceptado en canal {}", idUsuario, idCanal);
            return true;
        } catch (SQLException e) {
            logger.error("Error aceptando solicitud", e);
            return false;
        }
    }

    /**
     * Rechazar invitación a canal.
     */
    public void rechazarInvitacion(int idCanal, int idUsuario) throws SQLException {
        canalDAO.rechazarInvitacion(idCanal, idUsuario);
        logger.info("Usuario {} rechazó invitación al canal {}", idUsuario, idCanal);
    }

    /**
     * Invitar a un usuario a un canal (por un miembro actual del canal).
     * Agrega al usuario pero lo deja pendiente (aceptado=FALSE) hasta que acepte.
     */
    public boolean invitarUsuario(int idCanal, int idInvitador, int idInvitado) {
        try {
            // Verificar que el invitador es miembro aceptado
            if (!canalDAO.esMiembroAceptado(idCanal, idInvitador)) {
                logger.warn("Invitador {} no es miembro aceptado del canal {}", idInvitador, idCanal);
                return false;
            }
            // Agregar relación si no existe, pero NO aceptar automáticamente
            if (!canalDAO.existeRelacion(idCanal, idInvitado)) {
                canalDAO.agregarUsuario(idCanal, idInvitado);
            }
            // NO se acepta automáticamente - el usuario debe decidir
            logger.info("Usuario {} invitado al canal {} por {} (pendiente de aceptación)", idInvitado, idCanal, idInvitador);
            return true;
        } catch (SQLException e) {
            logger.error("Error invitando usuario al canal", e);
            return false;
        }
    }

    /**
     * Obtener todos los canales.
     */
    public List<Canal> obtenerTodosLosCanales() {
        try {
            return canalDAO.obtenerTodos();
        } catch (SQLException e) {
            logger.error("Error obteniendo canales", e);
            return List.of();
        }
    }

    /**
     * Obtener canales donde el usuario es miembro aceptado.
     */
    public List<Canal> obtenerCanalesDeUsuario(int idUsuario) {
        try {
            return canalDAO.obtenerCanalesDeUsuario(idUsuario);
        } catch (SQLException e) {
            logger.error("Error obteniendo canales del usuario {}", idUsuario, e);
            return List.of();
        }
    }

    /**
     * Obtener usuarios de un canal (solo IDs).
     */
    public List<Integer> obtenerMiembrosCanal(int idCanal) {
        try {
            return canalDAO.obtenerUsuariosCanal(idCanal);
        } catch (SQLException e) {
            logger.error("Error obteniendo miembros del canal", e);
            return List.of();
        }
    }

    /**
     * Alias para obtenerMiembrosCanal (compatibilidad).
     */
    public List<Integer> obtenerUsuariosCanal(int idCanal) {
        return obtenerMiembrosCanal(idCanal);
    }

    /**
     * Obtener miembros de un canal con información completa del servidor.
     * Incluye tanto usuarios locales como remotos.
     */
    public List<com.universidad.chat.servidor.model.MiembroCanal> obtenerMiembrosConServidor(int idCanal) {
        try {
            return canalDAO.obtenerMiembrosConServidor(idCanal);
        } catch (SQLException e) {
            logger.error("Error obteniendo miembros con servidor del canal", e);
            return List.of();
        }
    }

    /**
     * Obtener solo miembros aceptados con información del servidor.
     */
    public List<com.universidad.chat.servidor.model.MiembroCanal> obtenerMiembrosAceptadosConServidor(int idCanal) {
        try {
            return canalDAO.obtenerMiembrosAceptadosConServidor(idCanal);
        } catch (SQLException e) {
            logger.error("Error obteniendo miembros aceptados con servidor del canal", e);
            return List.of();
        }
    }

    /**
     * Obtener usuarios con invitación pendiente de un canal.
     */
    public List<Integer> obtenerPendientesCanal(int idCanal) {
        try {
            return canalDAO.obtenerPendientesCanal(idCanal);
        } catch (SQLException e) {
            logger.error("Error obteniendo pendientes del canal", e);
            return List.of();
        }
    }

    /**
     * Obtener canal por ID.
     */
    public Canal obtenerCanalPorId(int id) {
        try {
            return canalDAO.obtenerPorId(id);
        } catch (SQLException e) {
            logger.error("Error obteniendo canal por ID", e);
            return null;
        }
    }

    /**
     * Registrar un canal remoto localmente y agregar al usuario como miembro aceptado.
     * Útil cuando un usuario acepta una invitación a un canal de otro servidor.
     * 
     * @param idCanal ID del canal en el servidor remoto
     * @param nombreCanal Nombre del canal
     * @param idCreador ID del usuario creador en su servidor
     * @param creadorServidorHost Host del servidor del creador
     * @param creadorServidorPuerto Puerto del servidor del creador
     * @param esPrivado Si el canal es privado
     * @param idUsuario ID del usuario que se une al canal
     * @param usuarioServidorHost Host del servidor del usuario (null si es local)
     * @param usuarioServidorPuerto Puerto del servidor del usuario (null si es local)
     */
    public boolean registrarCanalRemotoYAgregarUsuario(int idCanal, String nombreCanal, 
                                                       int idCreador, String creadorServidorHost, int creadorServidorPuerto,
                                                       boolean esPrivado, 
                                                       int idUsuario, String usuarioServidorHost, Integer usuarioServidorPuerto) {
        try {
            // Registrar el canal si no existe
            canalDAO.registrarCanalRemoto(idCanal, nombreCanal, idCreador, creadorServidorHost, creadorServidorPuerto, esPrivado);
            
            // Agregar al usuario como miembro aceptado
            canalDAO.agregarUsuarioAceptado(idCanal, idUsuario, usuarioServidorHost, usuarioServidorPuerto);
            
            if (usuarioServidorHost != null) {
                logger.info("Canal remoto {} registrado y usuario remoto {}:{}:{} agregado como miembro", 
                           idCanal, idUsuario, usuarioServidorHost, usuarioServidorPuerto);
            } else {
                logger.info("Canal remoto {} registrado y usuario local {} agregado como miembro", idCanal, idUsuario);
            }
            return true;
        } catch (SQLException e) {
            logger.error("Error registrando canal remoto", e);
            return false;
        }
    }

    /**
     * Agregar un usuario como miembro aceptado directamente.
     * Útil para cuando un usuario remoto acepta unirse a un canal local.
     * 
     * @param idCanal ID del canal
     * @param idUsuario ID del usuario en su servidor
     * @param usuarioServidorHost Host del servidor del usuario (null si es local)
     * @param usuarioServidorPuerto Puerto del servidor del usuario (null si es local)
     */
    public void agregarUsuarioAceptadoDirecto(int idCanal, int idUsuario, String usuarioServidorHost, Integer usuarioServidorPuerto) throws SQLException {
        canalDAO.agregarUsuarioAceptado(idCanal, idUsuario, usuarioServidorHost, usuarioServidorPuerto);
        if (usuarioServidorHost != null) {
            logger.info("Usuario remoto {}:{}:{} agregado como miembro aceptado del canal {}", 
                       idUsuario, usuarioServidorHost, usuarioServidorPuerto, idCanal);
        } else {
            logger.info("Usuario local {} agregado como miembro aceptado del canal {}", idUsuario, idCanal);
        }
    }
}
