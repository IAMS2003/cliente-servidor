package com.universidad.chat.cliente.service;

import com.universidad.chat.cliente.config.ConexionBD;
import com.universidad.chat.cliente.dao.MensajeLogDAO;
import com.universidad.chat.cliente.model.MensajeLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Base64;
import java.util.List;

/**
 * Servicio para persistencia local de mensajes en el cliente.
 */
public class MensajePersistenciaService {
    private static final Logger logger = LoggerFactory.getLogger(MensajePersistenciaService.class);
    private final MensajeLogDAO mensajeDAO;
    private final Integer miIdUsuario;

    public MensajePersistenciaService(Integer miIdUsuario) {
        this.miIdUsuario = miIdUsuario;
        try {
            Connection conn = ConexionBD.getInstancia().getConexion();
            this.mensajeDAO = new MensajeLogDAO(conn);
        } catch (Exception e) {
            logger.error("Error al inicializar servicio de persistencia", e);
            throw new RuntimeException("No se pudo inicializar la persistencia de mensajes", e);
        }
    }

    /**
     * Guardar mensaje de texto en BD local (con ID del servidor para evitar duplicados).
     */
    public void guardarMensajeTexto(int idEmisor, Integer idReceptor, Integer idCanal, String contenido, Integer idServidor) {
        try {
            logger.debug("Intentando guardar mensaje: emisor={}, receptor={}, canal={}, idServidor={}, contenido='{}'", 
                idEmisor, idReceptor, idCanal, idServidor, contenido.substring(0, Math.min(30, contenido.length())));
            
            // Si tenemos ID del servidor, verificar por ese ID (más confiable)
            if (idServidor != null) {
                boolean existe = mensajeDAO.existePorIdServidor(idServidor);
                if (existe) {
                    logger.debug("Mensaje ya existe (idServidor={}), omitido", idServidor);
                    return;
                }
            }
            
            MensajeLog mensaje = new MensajeLog(idEmisor, idReceptor, idCanal, contenido, "TEXTO");
            mensaje.setIdServidor(idServidor);
            int id = mensajeDAO.crear(mensaje);
            logger.info("✓ Mensaje de texto guardado con ID local: {} (idServidor:{}, emisor:{}, receptor:{}, canal:{})", 
                id, idServidor, idEmisor, idReceptor, idCanal);
        } catch (Exception e) {
            logger.error("Error guardando mensaje de texto en BD local", e);
        }
    }
    
    /**
     * Guardar mensaje de texto en BD local (sin ID del servidor, para mensajes propios).
     */
    public void guardarMensajeTexto(int idEmisor, Integer idReceptor, Integer idCanal, String contenido) {
        guardarMensajeTexto(idEmisor, idReceptor, idCanal, contenido, null);
    }

    /**
     * Guardar mensaje de audio en BD local (con ID del servidor para evitar duplicados).
     */
    public void guardarMensajeAudio(int idEmisor, Integer idReceptor, Integer idCanal, 
                                    String transcripcion, String audioDataBase64, Integer idServidor) {
        try {
            // Si tenemos ID del servidor, verificar por ese ID (más confiable)
            if (idServidor != null) {
                boolean existe = mensajeDAO.existePorIdServidor(idServidor);
                if (existe) {
                    logger.debug("Mensaje de audio ya existe (idServidor={}), omitido", idServidor);
                    return;
                }
            }
            
            MensajeLog mensaje = new MensajeLog(idEmisor, idReceptor, idCanal, "", "AUDIO");
            mensaje.setIdServidor(idServidor);
            mensaje.setTranscripcion(transcripcion != null ? transcripcion : "");
            
            // Decodificar audio de Base64 a bytes
            if (audioDataBase64 != null && !audioDataBase64.isEmpty()) {
                byte[] audioBytes = Base64.getDecoder().decode(audioDataBase64);
                mensaje.setAudioData(audioBytes);
            }
            
            int id = mensajeDAO.crear(mensaje);
            logger.debug("Mensaje de audio guardado localmente con ID: {} (idServidor:{})", id, idServidor);
        } catch (Exception e) {
            logger.error("Error guardando mensaje de audio en BD local", e);
        }
    }
    
    /**
     * Guardar mensaje de audio en BD local (sin ID del servidor, para mensajes propios).
     */
    public void guardarMensajeAudio(int idEmisor, Integer idReceptor, Integer idCanal, 
                                    String transcripcion, String audioDataBase64) {
        guardarMensajeAudio(idEmisor, idReceptor, idCanal, transcripcion, audioDataBase64, null);
    }

    /**
     * Cargar mensajes de un chat directo con un usuario.
     */
    public List<MensajeLog> cargarMensajesUsuario(int idOtroUsuario) {
        if (miIdUsuario == null) {
            logger.warn("No se puede cargar mensajes sin ID de usuario");
            return List.of();
        }
        
        try {
            logger.debug("Cargando mensajes entre usuario {} y usuario {}", miIdUsuario, idOtroUsuario);
            List<MensajeLog> mensajes = mensajeDAO.obtenerEntreUsuarios(miIdUsuario, idOtroUsuario);
            logger.info("Cargados {} mensajes del chat con usuario {} (de {} a {})", 
                mensajes.size(), idOtroUsuario, miIdUsuario, idOtroUsuario);
            
            // Log de tipos de mensajes
            long textos = mensajes.stream().filter(m -> "TEXTO".equals(m.getTipoMensaje())).count();
            long audios = mensajes.stream().filter(m -> "AUDIO".equals(m.getTipoMensaje())).count();
            logger.debug("  → {} mensajes de texto, {} de audio", textos, audios);
            
            return mensajes;
        } catch (Exception e) {
            logger.error("Error cargando mensajes de usuario desde BD local", e);
            return List.of();
        }
    }

    /**
     * Cargar mensajes de un canal.
     */
    public List<MensajeLog> cargarMensajesCanal(int idCanal) {
        try {
            logger.debug("Cargando mensajes del canal {}", idCanal);
            List<MensajeLog> mensajes = mensajeDAO.listarPorCanal(idCanal);
            logger.info("Cargados {} mensajes del canal {}", mensajes.size(), idCanal);
            
            // Log de tipos de mensajes
            long textos = mensajes.stream().filter(m -> "TEXTO".equals(m.getTipoMensaje())).count();
            long audios = mensajes.stream().filter(m -> "AUDIO".equals(m.getTipoMensaje())).count();
            logger.debug("  → {} mensajes de texto, {} de audio", textos, audios);
            
            return mensajes;
        } catch (Exception e) {
            logger.error("Error cargando mensajes de canal desde BD local", e);
            return List.of();
        }
    }
    
    /**
     * Guardar mensaje solo si no existe (evita duplicados en sincronización).
     */
    public boolean guardarMensajeSiNoExiste(MensajeLog mensaje) {
        if (miIdUsuario == null) {
            logger.warn("No se puede guardar mensaje sin ID de usuario");
            return false;
        }
        
        try {
            // Verificar si el mensaje ya existe
            java.sql.Timestamp fechaTs = mensaje.getFecha() != null ? 
                java.sql.Timestamp.valueOf(mensaje.getFecha()) : 
                new java.sql.Timestamp(System.currentTimeMillis());
            
            // Para mensajes de audio, usar transcripción como contenido para verificación
            String contenidoVerificacion = mensaje.getContenido();
            if ("AUDIO".equals(mensaje.getTipoMensaje()) && mensaje.getTranscripcion() != null) {
                contenidoVerificacion = mensaje.getTranscripcion();
            } else if (contenidoVerificacion == null) {
                contenidoVerificacion = "";
            }
                
            boolean existe = mensajeDAO.existeMensaje(
                mensaje.getIdEmisor(),
                mensaje.getIdReceptor(),
                mensaje.getIdCanal(),
                mensaje.getTipoMensaje(),
                contenidoVerificacion,
                fechaTs
            );
            
            if (!existe) {
                int id = mensajeDAO.crear(mensaje);
                logger.debug("Mensaje sincronizado guardado con ID: {}", id);
                return true;
            } else {
                logger.debug("Mensaje ya existe en BD local, omitido");
                return false;
            }
        } catch (Exception e) {
            logger.error("Error guardando mensaje en sincronización", e);
            return false;
        }
    }
    
    /**
     * Contar total de mensajes en la BD local.
     * Usado para determinar si ya hay datos (evitar re-sincronizar).
     */
    public int contarMensajesTotales() {
        try {
            return mensajeDAO.contarTodos();
        } catch (Exception e) {
            logger.error("Error contando mensajes totales", e);
            return 0;
        }
    }
    
    /**
     * Obtener la fecha del último mensaje del usuario actual (para sincronización).
     */
    public java.sql.Timestamp obtenerFechaUltimoMensaje() {
        if (miIdUsuario == null) {
            return null;
        }
        
        try {
            return mensajeDAO.obtenerFechaUltimoMensaje(miIdUsuario);
        } catch (Exception e) {
            logger.error("Error obteniendo fecha del último mensaje", e);
            return null;
        }
    }
}
