package com.universidad.chat.servidor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Gestor de conexión a base de datos MySQL del servidor.
 * Aplica patrón Singleton para mantener una única instancia.
 */
public class ConexionBD {
    private static final Logger logger = LoggerFactory.getLogger(ConexionBD.class);
    private static ConexionBD instancia;
    
    private String url;
    private String usuario;
    private String contrasena;
    private HikariDataSource dataSource;

    private ConexionBD() {
        cargarConfiguracion();
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
     * Cargar configuración desde config.properties.
     */
    private void cargarConfiguracion() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.error("No se encontró config.properties");
                return;
            }
            props.load(input);
            this.url = props.getProperty("jdbc.url");
            this.usuario = props.getProperty("jdbc.user");
            this.contrasena = props.getProperty("jdbc.password");
            int maxPool = Integer.parseInt(props.getProperty("db.maxPoolSize", "10"));
            int minIdle = Integer.parseInt(props.getProperty("db.minIdle", "2"));
            long connTimeout = Long.parseLong(props.getProperty("db.connectionTimeoutMs", "5000"));
            long idleTimeout = Long.parseLong(props.getProperty("db.idleTimeoutMs", "600000"));
            long maxLifetime = Long.parseLong(props.getProperty("db.maxLifetimeMs", "1800000"));

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername(usuario);
            cfg.setPassword(contrasena);
            cfg.setMaximumPoolSize(maxPool);
            cfg.setMinimumIdle(minIdle);
            cfg.setConnectionTimeout(connTimeout);
            cfg.setIdleTimeout(idleTimeout);
            cfg.setMaxLifetime(maxLifetime);
            cfg.setPoolName("ChatServidorHikariPool");
            this.dataSource = new HikariDataSource(cfg);
            logger.info("Pool de conexiones inicializado ({}), url={}", maxPool, url);
        } catch (IOException e) {
            logger.error("Error cargando configuración", e);
        }
    }

    /**
     * Obtener conexión activa a la base de datos.
     */
    public Connection getConexion() throws SQLException {
        if (dataSource == null) throw new SQLException("DataSource no inicializado");
        return dataSource.getConnection();
    }

    /**
     * Cerrar conexión.
     */
    public void cerrarConexion() {
        if (dataSource != null) {
            try { dataSource.close(); } catch (Exception ignored) {}
            logger.info("Pool de conexiones cerrado");
        }
    }

    /**
     * Inicializar esquema de base de datos.
     */
    public void inicializarEsquema() throws SQLException {
        Connection conn = getConexion();
        
        // Crear tabla usuarios
        String crearUsuarios = """
            CREATE TABLE IF NOT EXISTS usuarios (
                id INT PRIMARY KEY AUTO_INCREMENT,
                nombre_usuario VARCHAR(100) UNIQUE NOT NULL,
                email VARCHAR(150) UNIQUE NOT NULL,
                contrasena VARCHAR(255) NOT NULL,
                foto VARCHAR(255),
                direccion_ip VARCHAR(45),
                conectado BOOLEAN DEFAULT FALSE,
                fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        // Crear tabla canales
        String crearCanales = """
            CREATE TABLE IF NOT EXISTS canales (
                id INT PRIMARY KEY AUTO_INCREMENT,
                nombre VARCHAR(100) NOT NULL,
                id_creador INT NOT NULL,
                es_privado BOOLEAN DEFAULT TRUE,
                fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_creador) REFERENCES usuarios(id)
            )
            """;
        
        // Crear tabla canal_usuarios
        String crearCanalUsuarios = """
            CREATE TABLE IF NOT EXISTS canal_usuarios (
                id_canal INT NOT NULL,
                id_usuario INT NOT NULL,
                aceptado BOOLEAN DEFAULT FALSE,
                fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id_canal, id_usuario),
                FOREIGN KEY (id_canal) REFERENCES canales(id),
                FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
            )
            """;
        
        // Crear tabla mensajes_log
        String crearMensajes = """
            CREATE TABLE IF NOT EXISTS mensajes_log (
                id INT PRIMARY KEY AUTO_INCREMENT,
                id_emisor INT NOT NULL,
                id_receptor INT,
                id_canal INT,
                contenido TEXT,
                tipo_mensaje VARCHAR(20) NOT NULL,
                archivo_audio VARCHAR(255),
                transcripcion TEXT,
                fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_emisor) REFERENCES usuarios(id),
                FOREIGN KEY (id_receptor) REFERENCES usuarios(id),
                FOREIGN KEY (id_canal) REFERENCES canales(id)
            )
            """;
        
        try (var stmt = conn.createStatement()) {
            stmt.execute(crearUsuarios);
            stmt.execute(crearCanales);
            stmt.execute(crearCanalUsuarios);
            stmt.execute(crearMensajes);
            logger.info("Esquema de base de datos inicializado");
        }
    }
}
