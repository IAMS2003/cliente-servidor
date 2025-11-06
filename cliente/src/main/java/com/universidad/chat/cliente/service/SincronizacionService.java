package com.universidad.chat.cliente.service;

import com.google.gson.JsonObject;
import com.universidad.chat.cliente.model.MensajeLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para sincronizar mensajes del servidor con la BD local del cliente.
 * Se ejecuta al iniciar sesión para obtener todos los mensajes faltantes.
 */
public class SincronizacionService {
    private static final Logger logger = LoggerFactory.getLogger(SincronizacionService.class);
    
    private final ClienteProtocoloService protocoloService;
    private final MensajePersistenciaService persistenciaService;
    private final int miIdUsuario;

    public SincronizacionService(ClienteProtocoloService protocoloService, 
                                 MensajePersistenciaService persistenciaService,
                                 int miIdUsuario) {
        this.protocoloService = protocoloService;
        this.persistenciaService = persistenciaService;
        this.miIdUsuario = miIdUsuario;
    }

    /**
     * Sincronizar TODOS los mensajes del servidor con la BD local.
     * Se ejecuta cada vez que el usuario inicia sesión para mantener la BD actualizada.
     * 
     * @return CompletableFuture con el resultado de la sincronización
     */
    public CompletableFuture<ResultadoSincronizacion> sincronizarTodoAlIniciarSesion() {
        logger.info("Iniciando sincronización completa de mensajes al login...");
        
        return CompletableFuture.supplyAsync(() -> {
            ResultadoSincronizacion resultado = new ResultadoSincronizacion();
            
            try {
                int mensajesExistentes = persistenciaService.contarMensajesTotales();
                logger.info("BD contiene actualmente {} mensajes. Sincronizando con servidor...", mensajesExistentes);
                
                // 1. Obtener lista de TODOS los usuarios registrados (no solo conectados)
                //    Esto permite sincronizar historiales con usuarios que aún no se han conectado en esta sesión.
                var usuariosFuture = protocoloService.listarUsuarios();
                List<JsonObject> usuarios = usuariosFuture.get();
                
                logger.debug("Encontrados {} usuarios para sincronizar", usuarios.size());
                
                // 2. Sincronizar mensajes directos con cada usuario
                for (JsonObject usuario : usuarios) {
                    try {
                        int idUsuario = usuario.get("id").getAsInt();
                        if (idUsuario == miIdUsuario) {
                            continue; // No sincronizar conmigo mismo
                        }
                        
                        int nuevos = sincronizarMensajesUsuario(idUsuario);
                        resultado.mensajesUsuarios += nuevos;
                        
                    } catch (Exception e) {
                        logger.warn("Error sincronizando mensajes de usuario: " + e.getMessage());
                    }
                }
                
                // 3. Obtener lista de canales
                var canalesFuture = protocoloService.listarCanales();
                List<JsonObject> canales = canalesFuture.get();
                
                logger.debug("Encontrados {} canales para sincronizar", canales.size());
                
                // 4. Sincronizar mensajes de cada canal
                for (JsonObject canalJson : canales) {
                    try {
                        // El JSON de canal puede ser un objeto con estructura: {"id": 1, "nombre": "General"}
                        // o un string con formato "#1 General"
                        int idCanal = -1;
                        
                        if (canalJson.has("id")) {
                            idCanal = canalJson.get("id").getAsInt();
                        } else if (canalJson.has("canal")) {
                            String canalStr = canalJson.get("canal").getAsString();
                            idCanal = extraerIdCanal(canalStr);
                        }
                        
                        if (idCanal > 0) {
                            int nuevos = sincronizarMensajesCanal(idCanal);
                            resultado.mensajesCanales += nuevos;
                        }
                        
                    } catch (Exception e) {
                        logger.warn("Error sincronizando mensajes de canal: " + e.getMessage(), e);
                    }
                }
                
                resultado.exitoso = true;
                logger.info("✓ Sincronización completada: {} mensajes de usuarios, {} mensajes de canales",
                    resultado.mensajesUsuarios, resultado.mensajesCanales);
                
            } catch (Exception e) {
                logger.error("Error en sincronización completa: " + e.getMessage(), e);
                resultado.exitoso = false;
                resultado.error = e.getMessage();
            }
            
            return resultado;
        });
    }

    /**
     * Sincronizar mensajes de un usuario específico.
     */
    private int sincronizarMensajesUsuario(int idOtroUsuario) throws Exception {
        logger.debug("Sincronizando mensajes con usuario {}", idOtroUsuario);
        
        var mensajesFuture = protocoloService.solicitarHistorialUsuario(idOtroUsuario);
        List<JsonObject> mensajesServidor = mensajesFuture.get();
        
        int nuevos = 0;
        for (JsonObject jsonMsg : mensajesServidor) {
            MensajeLog mensaje = convertirJsonAMensaje(jsonMsg, idOtroUsuario, null);
            if (persistenciaService.guardarMensajeSiNoExiste(mensaje)) {
                nuevos++;
            }
        }
        
        if (nuevos > 0) {
            logger.debug("→ Sincronizados {} mensajes nuevos de usuario {}", nuevos, idOtroUsuario);
        }
        
        return nuevos;
    }

    /**
     * Sincronizar mensajes de un canal específico.
     */
    private int sincronizarMensajesCanal(int idCanal) throws Exception {
        logger.debug("Sincronizando mensajes del canal {}", idCanal);
        
        var mensajesFuture = protocoloService.solicitarHistorialCanal(idCanal);
        List<JsonObject> mensajesServidor = mensajesFuture.get();
        
        int nuevos = 0;
        for (JsonObject jsonMsg : mensajesServidor) {
            MensajeLog mensaje = convertirJsonAMensaje(jsonMsg, null, idCanal);
            if (persistenciaService.guardarMensajeSiNoExiste(mensaje)) {
                nuevos++;
            }
        }
        
        if (nuevos > 0) {
            logger.debug("→ Sincronizados {} mensajes nuevos del canal {}", nuevos, idCanal);
        }
        
        return nuevos;
    }

    /**
     * Convertir mensaje JSON del servidor a objeto MensajeLog.
     */
    private MensajeLog convertirJsonAMensaje(JsonObject jsonMsg, Integer idOtroUsuario, Integer idCanal) {
        int idEmisor = jsonMsg.has("idEmisor") ? jsonMsg.get("idEmisor").getAsInt() : 0;
        String tipo = jsonMsg.has("tipoMensaje") ? jsonMsg.get("tipoMensaje").getAsString() : "TEXTO";
        // Normalizar tipo "TEXT" (servidor) a "TEXTO" (cliente) para consistencia en renderizado
        if ("TEXT".equalsIgnoreCase(tipo)) {
            tipo = "TEXTO";
        }
        String contenido = jsonMsg.has("contenido") ? jsonMsg.get("contenido").getAsString() : "";
        
        // Determinar receptor según el contexto (y preferir valor explícito del JSON si existe)
        Integer idReceptor = null;
        if (jsonMsg.has("idReceptor")) {
            idReceptor = jsonMsg.get("idReceptor").getAsInt();
        } else if (idCanal == null && idOtroUsuario != null) {
            // Es un mensaje directo
            idReceptor = (idEmisor == miIdUsuario) ? idOtroUsuario : miIdUsuario;
        }
        
        MensajeLog mensaje = new MensajeLog(idEmisor, idReceptor, idCanal, contenido, tipo);
        
        // Extraer ID del servidor (clave única para evitar duplicados)
        if (jsonMsg.has("id")) {
            mensaje.setIdServidor(jsonMsg.get("id").getAsInt());
        }
        
        // Agregar transcripción si es audio
        if ("AUDIO".equals(tipo) && jsonMsg.has("transcripcion")) {
            mensaje.setTranscripcion(jsonMsg.get("transcripcion").getAsString());
        }
        
        // Agregar audio data si está presente
        if (jsonMsg.has("audioData")) {
            String audioBase64 = jsonMsg.get("audioData").getAsString();
            if (audioBase64 != null && !audioBase64.isEmpty()) {
                try {
                    byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
                    mensaje.setAudioData(audioBytes);
                } catch (Exception e) {
                    logger.warn("Error decodificando audio: " + e.getMessage());
                }
            }
        }
        
        // Parsear fecha si está disponible
        if (jsonMsg.has("fecha")) {
            try {
                String fechaStr = jsonMsg.get("fecha").getAsString();
                java.time.LocalDateTime fecha = java.time.LocalDateTime.parse(fechaStr);
                mensaje.setFecha(fecha);
            } catch (Exception e) {
                // Usar fecha actual si hay error
                mensaje.setFecha(java.time.LocalDateTime.now());
            }
        }
        
        return mensaje;
    }

    /**
     * Extraer ID de canal de string con formato "#<id> <nombre>"
     */
    private int extraerIdCanal(String canalStr) {
        try {
            if (canalStr.startsWith("#")) {
                int sp = canalStr.indexOf(' ');
                String id = sp > 1 ? canalStr.substring(1, sp) : canalStr.substring(1);
                return Integer.parseInt(id);
            }
        } catch (Exception e) {
            logger.warn("Error extrayendo ID de canal de: " + canalStr);
        }
        return -1;
    }

    /**
     * Clase para retornar el resultado de la sincronización.
     */
    public static class ResultadoSincronizacion {
        public boolean exitoso = false;
        public int mensajesUsuarios = 0;
        public int mensajesCanales = 0;
        public String error = null;
        
        public int getTotalMensajes() {
            return mensajesUsuarios + mensajesCanales;
        }
        
        @Override
        public String toString() {
            if (!exitoso) {
                return "Sincronización fallida: " + error;
            }
            return String.format("Sincronizados %d mensajes (%d usuarios, %d canales)", 
                getTotalMensajes(), mensajesUsuarios, mensajesCanales);
        }
    }
}
