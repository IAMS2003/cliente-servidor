package com.universidad.chat.servidor.service;

import com.universidad.chat.servidor.model.MensajeLog;
import com.universidad.chat.servidor.model.MensajeLogDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Servicio para gestión de mensajes y logs en el servidor.
 * Aplica principio SRP (Single Responsibility Principle).
 */
public class MensajeService {
    private static final Logger logger = LoggerFactory.getLogger(MensajeService.class);
    private final MensajeLogDAO mensajeLogDAO;

    public MensajeService() {
        this.mensajeLogDAO = new MensajeLogDAO();
    }

    /**
     * Registrar mensaje de texto.
     */
    public int registrarMensajeTexto(int idEmisor, int idReceptor, Integer idCanal, String contenido) {
        try {
            MensajeLog mensaje = new MensajeLog(idEmisor, idReceptor, idCanal, contenido, "TEXT");
            int id = mensajeLogDAO.registrar(mensaje);
            
            if (id > 0) {
                logger.info("Mensaje de texto registrado ID: {}", id);
            }
            
            return id;
        } catch (SQLException e) {
            logger.error("Error registrando mensaje de texto", e);
            return -1;
        }
    }

    /**
     * Registrar mensaje de audio.
     */
    public int registrarMensajeAudio(int idEmisor, int idReceptor, Integer idCanal, 
                                      String archivoAudio, String transcripcion) {
        try {
            MensajeLog mensaje = new MensajeLog(idEmisor, idReceptor, idCanal, "", "AUDIO");
            mensaje.setArchivoAudio(archivoAudio);
            mensaje.setTranscripcion(transcripcion);
            
            int id = mensajeLogDAO.registrar(mensaje);
            
            if (id > 0) {
                logger.info("Mensaje de audio registrado ID: {} con transcripción", id);
            }
            
            return id;
        } catch (SQLException e) {
            logger.error("Error registrando mensaje de audio", e);
            return -1;
        }
    }

    /**
     * Obtener historial de mensajes entre dos usuarios.
     */
    public List<MensajeLog> obtenerHistorialUsuarios(int idUsuario1, int idUsuario2) {
        try {
            return mensajeLogDAO.obtenerEntreUsuarios(idUsuario1, idUsuario2);
        } catch (SQLException e) {
            logger.error("Error obteniendo historial de mensajes", e);
            return List.of();
        }
    }

    /**
     * Obtener mensajes de un canal.
     */
    public List<MensajeLog> obtenerMensajesCanal(int idCanal) {
        try {
            return mensajeLogDAO.obtenerPorCanal(idCanal);
        } catch (SQLException e) {
            logger.error("Error obteniendo mensajes del canal", e);
            return List.of();
        }
    }

    /**
     * Obtener todos los logs de mensajes.
     */
    public List<MensajeLog> obtenerTodosLosLogs() {
        try {
            return mensajeLogDAO.obtenerTodos();
        } catch (SQLException e) {
            logger.error("Error obteniendo logs", e);
            return List.of();
        }
    }

    /**
     * Obtener mensajes de audio con transcripción.
     */
    public List<MensajeLog> obtenerMensajesAudio() {
        try {
            return mensajeLogDAO.obtenerMensajesAudio();
        } catch (SQLException e) {
            logger.error("Error obteniendo mensajes de audio", e);
            return List.of();
        }
    }

    /**
     * Obtener mensajes donde un usuario es emisor o receptor.
     * Útil para sincronización cross-server.
     */
    public List<MensajeLog> obtenerMensajesDeUsuario(int idUsuario) {
        try {
            return mensajeLogDAO.obtenerMensajesDeUsuario(idUsuario);
        } catch (SQLException e) {
            logger.error("Error obteniendo mensajes del usuario {}", idUsuario, e);
            return List.of();
        }
    }

    /**
     * Obtener mensajes recientes de un usuario (últimos N días).
     */
    public List<MensajeLog> obtenerMensajesRecientesDeUsuario(int idUsuario, int dias) {
        try {
            return mensajeLogDAO.obtenerMensajesRecientesDeUsuario(idUsuario, dias);
        } catch (SQLException e) {
            logger.error("Error obteniendo mensajes recientes del usuario {}", idUsuario, e);
            return List.of();
        }
    }

    /**
     * Registrar evento de control/negocio (registro, login, logout, broadcast, etc.).
     * Se persiste en la misma tabla de logs usando un tipo distinguible.
     * @param tipo p.ej. EVENTO, REGISTRO, LOGIN, LOGOUT, BROADCAST_USUARIOS, BROADCAST_CANALES
     * @param contenido descripción del evento
     * @param idUsuario id del usuario que origina el evento; usar 0 para sistema
     * @param idCanal opcional, para eventos asociados a un canal
     */
    public int registrarEvento(String tipo, String contenido, int idUsuario, Integer idCanal) {
        try {
            MensajeLog ev = new MensajeLog(idUsuario, 0, idCanal, contenido, tipo);
            int id = mensajeLogDAO.registrar(ev);
            if (id > 0) {
                logger.info("Evento registrado [{}] ID: {} - {}", tipo, id, contenido);
            }
            return id;
        } catch (SQLException e) {
            logger.error("Error registrando evento {}", tipo, e);
            return -1;
        }
    }

    /**
     * Verifica si un mensaje ya existe en la base de datos.
     * Útil para evitar duplicados al recibir mensajes de servidores remotos.
     */
    public boolean mensajeExiste(int idEmisor, int idReceptor, Integer idCanal, String contenido) {
        try {
            return mensajeLogDAO.mensajeExiste(idEmisor, idReceptor, idCanal, contenido);
        } catch (SQLException e) {
            logger.error("Error verificando existencia de mensaje", e);
            return false; // En caso de error, asumimos que no existe para no perder el mensaje
        }
    }
}
