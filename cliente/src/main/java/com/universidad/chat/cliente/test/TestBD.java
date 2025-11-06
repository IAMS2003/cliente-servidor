package com.universidad.chat.cliente.test;

import com.universidad.chat.cliente.config.ConexionBD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * Clase de prueba para verificar la creación de la base de datos H2.
 * Ejecutar: mvn exec:java -Dexec.mainClass="com.universidad.chat.cliente.test.TestBD"
 */
public class TestBD {
    private static final Logger logger = LoggerFactory.getLogger(TestBD.class);

    public static void main(String[] args) {
        logger.info("=== Test de Base de Datos H2 ===");
        
        try {
            // Obtener instancia de conexión
            ConexionBD conexionBD = ConexionBD.getInstancia();
            logger.info("✓ Instancia de ConexionBD obtenida");
            
            // Inicializar esquema
            conexionBD.inicializarEsquema();
            logger.info("✓ Esquema inicializado");
            
            // Obtener conexión
            Connection conn = conexionBD.getConexion();
            logger.info("✓ Conexión obtenida");
            
            // Verificar tablas creadas
            var metadata = conn.getMetaData();
            ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"});
            
            logger.info("Tablas encontradas:");
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                logger.info("  - {}", tableName);
            }
            
            // Verificar ubicación del archivo
            var rs = conn.createStatement().executeQuery("CALL DATABASE_PATH()");
            if (rs.next()) {
                logger.info("Ubicación de la BD: {}", rs.getString(1));
            }
            
            logger.info("=== Test completado exitosamente ===");
            
            // Cerrar
            conexionBD.cerrarConexion();
            
        } catch (Exception e) {
            logger.error("Error en test de BD", e);
            System.exit(1);
        }
    }
}
