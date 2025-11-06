package com.universidad.chat.cliente.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gestor de conexión a base de datos H2 del cliente.
 * Cada usuario tiene su propia base de datos separada.
 */
public class ConexionBD {
    private static final Logger logger = LoggerFactory.getLogger(ConexionBD.class);
    private static ConexionBD instancia;
    
    private String urlBase;           // URL base del properties (sin nombre de BD)
    private String usuario;           // Usuario de BD del properties
    private String contrasena;        // Contraseña de BD del properties
    private String url;               // URL completa para el usuario actual
    private Connection conexion;
    private int idUsuario;            // ID del usuario conectado

    private ConexionBD() {
        // Constructor privado - carga config base y se completa con inicializar()
        cargarDriver();
        cargarConfiguracionBase();
    }
    
    /**
     * Cargar driver JDBC de H2.
     */
    private void cargarDriver() {
        try {
            Class.forName("org.h2.Driver");
            logger.debug("Driver H2 cargado correctamente");
        } catch (ClassNotFoundException e) {
            logger.error("No se pudo cargar el driver H2", e);
        }
    }
    
    /**
     * Cargar configuración base desde config.properties.
     */
    private void cargarConfiguracionBase() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.error("No se encontró config.properties");
                // Valores por defecto
                this.urlBase = "jdbc:h2:./data/";
                this.usuario = "sa";
                this.contrasena = "";
                return;
            }
            props.load(input);
            
            // Obtener URL base del properties
            String jdbcUrlCompleta = props.getProperty("jdbc.url", "jdbc:h2:./data/chat_cliente");
            
            // Extraer la ruta base (sin el nombre del archivo de BD)
            // Ejemplo: "jdbc:h2:./data/chat_cliente" → "jdbc:h2:./data/"
            int lastSlash = Math.max(jdbcUrlCompleta.lastIndexOf('/'), jdbcUrlCompleta.lastIndexOf('\\'));
            if (lastSlash > 0) {
                this.urlBase = jdbcUrlCompleta.substring(0, lastSlash + 1);
            } else {
                this.urlBase = "jdbc:h2:./data/";
            }
            
            this.usuario = props.getProperty("jdbc.user", "sa");
            this.contrasena = props.getProperty("jdbc.password", "");
            
            logger.debug("Configuración base cargada - URL base: {}, usuario: {}", urlBase, usuario);
        } catch (IOException e) {
            logger.error("Error cargando configuración", e);
            // Valores por defecto
            this.urlBase = "jdbc:h2:./data/";
            this.usuario = "sa";
            this.contrasena = "";
        }
    }

    /**
     * Singleton: Obtener instancia única.
     */
    public static synchronized ConexionBD getInstancia() {
        if (instancia == null) {
            instancia = new ConexionBD();
        }
        return instancia;
    }
    
    /**
     * Inicializar BD para un usuario específico.
     * Cada usuario tiene su propia base de datos H2.
     * Usa la configuración base de config.properties y completa con el ID del usuario.
     * @param idUsuario ID del usuario conectado
     * @param nombreUsuario Nombre del usuario (para logs)
     */
    public void inicializar(int idUsuario, String nombreUsuario) {
        this.idUsuario = idUsuario;
        
        // Completar URL base con el nombre de BD específico del usuario
        String dbName = "chat_usuario" + idUsuario;
        this.url = urlBase + dbName;
        
        logger.info("BD inicializada para usuario '{}' (ID: {}): {}", nombreUsuario, idUsuario, url);
        
        // Crear directorio si no existe
        crearDirectorioBD();
    }
    
    /**
     * Obtener el ID del usuario actual.
     */
    public int getIdUsuario() {
        return idUsuario;
    }
    
    /**
     * Crear directorio para la base de datos si es una BD basada en archivo.
     */
    private void crearDirectorioBD() {
        try {
            // Extraer ruta del directorio de la URL JDBC
            // Formato esperado: jdbc:h2:./data/chat_cliente
            if (url != null && url.startsWith("jdbc:h2:") && !url.contains("mem:")) {
                String dbPath = url.substring("jdbc:h2:".length());
                // Remover nombre del archivo de BD para obtener solo el directorio
                int lastSlash = Math.max(dbPath.lastIndexOf('/'), dbPath.lastIndexOf('\\'));
                if (lastSlash > 0) {
                    String dirPath = dbPath.substring(0, lastSlash);
                    java.io.File dir = new java.io.File(dirPath);
                    if (!dir.exists()) {
                        if (dir.mkdirs()) {
                            logger.info("Directorio de BD creado: {}", dir.getAbsolutePath());
                        }
                    } else {
                        logger.debug("Directorio de BD ya existe: {}", dir.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo crear directorio de BD (puede ya existir)", e);
        }
    }

    /**
     * Obtener conexión activa a la base de datos.
     */
    public Connection getConexion() throws SQLException {
        if (conexion == null || conexion.isClosed()) {
            logger.info("Estableciendo conexión a BD: {}", url);
            conexion = DriverManager.getConnection(url, usuario, contrasena);
            logger.info("✓ Conexión a BD establecida exitosamente");
        }
        return conexion;
    }

    /**
     * Cerrar conexión.
     */
    public void cerrarConexion() {
        try {
            if (conexion != null && !conexion.isClosed()) {
                conexion.close();
                logger.info("Conexión a BD cerrada");
            }
        } catch (SQLException e) {
            logger.error("Error cerrando conexión", e);
        }
    }

    /**
     * Inicializar esquema de base de datos.
     */
    public void inicializarEsquema() throws SQLException {
        logger.info("Inicializando esquema de base de datos...");
        Connection conn = getConexion();
        
        // Crear tabla usuario (singular, como espera el DAO)
        String crearUsuarios = """
            CREATE TABLE IF NOT EXISTS usuario (
                id INT PRIMARY KEY AUTO_INCREMENT,
                nombre VARCHAR(100) NOT NULL,
                correo VARCHAR(150) NOT NULL,
                contrasena VARCHAR(255),
                fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        // Crear tabla canal (singular, como espera el DAO)
        String crearCanales = """
            CREATE TABLE IF NOT EXISTS canal (
                id INT PRIMARY KEY AUTO_INCREMENT,
                nombre VARCHAR(100) NOT NULL,
                descripcion TEXT,
                fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        // Crear tabla mensaje_log (como espera el DAO, ampliada para audio)
        String crearMensajes = """
            CREATE TABLE IF NOT EXISTS mensaje_log (
                id INT PRIMARY KEY AUTO_INCREMENT,
                id_servidor INT UNIQUE,
                id_emisor INT NOT NULL,
                id_receptor INT,
                id_canal INT,
                contenido TEXT,
                tipo_mensaje VARCHAR(20) NOT NULL DEFAULT 'TEXTO',
                audio_data BLOB,
                transcripcion TEXT,
                fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        try (var stmt = conn.createStatement()) {
            stmt.execute(crearUsuarios);
            logger.debug("Tabla 'usuario' verificada/creada");
            stmt.execute(crearCanales);
            logger.debug("Tabla 'canal' verificada/creada");
            stmt.execute(crearMensajes);
            logger.debug("Tabla 'mensaje_log' verificada/creada");
            logger.info("✓ Esquema de base de datos inicializado correctamente");
        }
    }
}
