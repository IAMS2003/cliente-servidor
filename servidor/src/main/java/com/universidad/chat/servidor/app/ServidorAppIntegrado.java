package com.universidad.chat.servidor.app;

import com.universidad.chat.servidor.config.ConexionBD;
import com.universidad.chat.servidor.service.ServidorTCPIntegrado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal del servidor de chat.
 * Inicia el servidor TCP-IP y gestiona las conexiones de clientes.
 */
public class ServidorAppIntegrado {
    private static final Logger logger = LoggerFactory.getLogger(ServidorAppIntegrado.class);

    public static void main(String[] args) {
        logger.info("Iniciando Servidor de Chat Universidad...");
        
        try {
            // Inicializar conexión a base de datos
            ConexionBD conexionBD = ConexionBD.getInstancia();
            conexionBD.inicializarEsquema();
            logger.info("Base de datos inicializada correctamente");
            
            // Iniciar servidor TCP-IP (integrado)
            ServidorTCPIntegrado servidor = new ServidorTCPIntegrado();
            
            // Agregar shutdown hook para cierre limpio
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Cerrando servidor...");
                servidor.detener();
                conexionBD.cerrarConexion();
            }));
            
            logger.info("Servidor iniciado correctamente");
            servidor.iniciar();
            
        } catch (Exception e) {
            logger.error("Error al iniciar el servidor", e);
            System.exit(1);
        }
    }
}
