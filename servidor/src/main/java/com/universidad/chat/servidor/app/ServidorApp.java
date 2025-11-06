package com.universidad.chat.servidor.app;

import com.universidad.chat.servidor.service.ServidorTCP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal del servidor de chat.
 * Inicia el servidor TCP-IP y gestiona las conexiones de clientes.
 */
public class ServidorApp {
    private static final Logger logger = LoggerFactory.getLogger(ServidorApp.class);

    public static void main(String[] args) {
        logger.info("Iniciando Servidor de Chat Universidad...");
        
        try {
            // TODO: Inicializar configuración
            // TODO: Conectar a base de datos MySQL
            
            // Iniciar servidor TCP-IP
            ServidorTCP servidor = new ServidorTCP();
            
            // Agregar shutdown hook para cierre limpio
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Cerrando servidor...");
                servidor.detener();
            }));
            
            logger.info("Servidor iniciado correctamente");
            servidor.iniciar();
            
        } catch (Exception e) {
            logger.error("Error al iniciar el servidor", e);
            System.exit(1);
        }
    }
}
