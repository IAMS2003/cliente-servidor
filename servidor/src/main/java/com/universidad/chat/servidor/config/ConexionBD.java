package com.universidad.chat.servidor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        // Permitir un archivo externo vía -Dconfig.file o variable de entorno CONFIG_FILE
        String externalConfig = System.getProperty("config.file", System.getenv("CONFIG_FILE"));
        boolean loaded = false;
        if (externalConfig != null && !externalConfig.isBlank()) {
            try (InputStream in = Files.newInputStream(Path.of(externalConfig))) {
                props.load(in);
                loaded = true;
                logger.info("ConexionBD: usando archivo externo de configuración: {}", externalConfig);
            } catch (Exception e) {
                logger.warn("No se pudo leer config externo {}: {}", externalConfig, e.getMessage());
            }
        }
        if (!loaded) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (input == null) {
                    logger.error("No se encontró config.properties");
                    return;
                }
                props.load(input);
                logger.info("ConexionBD: usando config.properties del classpath");
            } catch (IOException e) {
                logger.error("Error cargando configuración", e);
            }
        }

        // System properties tienen prioridad sobre el archivo usado
        this.url = System.getProperty("jdbc.url", props.getProperty("jdbc.url"));
        this.usuario = System.getProperty("jdbc.user", props.getProperty("jdbc.user"));
        this.contrasena = System.getProperty("jdbc.password", props.getProperty("jdbc.password"));
        int maxPool = Integer.parseInt(System.getProperty("db.maxPoolSize", props.getProperty("db.maxPoolSize", "10")));
        int minIdle = Integer.parseInt(System.getProperty("db.minIdle", props.getProperty("db.minIdle", "2")));
        long connTimeout = Long.parseLong(System.getProperty("db.connectionTimeoutMs", props.getProperty("db.connectionTimeoutMs", "5000")));
        long idleTimeout = Long.parseLong(System.getProperty("db.idleTimeoutMs", props.getProperty("db.idleTimeoutMs", "600000")));
        long maxLifetime = Long.parseLong(System.getProperty("db.maxLifetimeMs", props.getProperty("db.maxLifetimeMs", "1800000")));

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
        // NOTA: Usar un ID autoincremental como PK para soportar usuarios con mismo ID de diferentes servidores
        String crearCanalUsuarios = """
            CREATE TABLE IF NOT EXISTS canal_usuarios (
                id INT PRIMARY KEY AUTO_INCREMENT,
                id_canal INT NOT NULL,
                id_usuario INT NOT NULL,
                usuario_servidor_host VARCHAR(255) DEFAULT NULL,
                usuario_servidor_puerto INT DEFAULT NULL,
                aceptado BOOLEAN DEFAULT FALSE,
                fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_canal_usuario_servidor (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto),
                FOREIGN KEY (id_canal) REFERENCES canales(id),
                FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
            )
            """;
        
        // Crear tabla mensajes_log
        String crearMensajes = """
            CREATE TABLE IF NOT EXISTS mensajes_log (
                id INT PRIMARY KEY AUTO_INCREMENT,
                id_emisor INT,
                id_receptor INT,
                id_canal INT,
                contenido TEXT,
                tipo_mensaje VARCHAR(50) NOT NULL,
                archivo_audio VARCHAR(255),
                transcripcion TEXT,
                fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_emisor) REFERENCES usuarios(id),
                FOREIGN KEY (id_receptor) REFERENCES usuarios(id),
                FOREIGN KEY (id_canal) REFERENCES canales(id)
            )
            """;
        
        // Tabla de servidores pares (P2P)
        String crearServidores = """
            CREATE TABLE IF NOT EXISTS servidores (
                id INT PRIMARY KEY AUTO_INCREMENT,
                host VARCHAR(100) NOT NULL,
                puerto INT NOT NULL,
                estado VARCHAR(20) DEFAULT 'UNKNOWN',
                ultimo_heartbeat TIMESTAMP NULL,
                UNIQUE KEY uk_host_puerto (host, puerto)
            )
            """;

        try (var stmt = conn.createStatement()) {
            stmt.execute(crearUsuarios);
            stmt.execute(crearCanales);
            stmt.execute(crearCanalUsuarios);
            stmt.execute(crearMensajes);
            try { stmt.execute(crearServidores); } catch (Exception ex) { logger.warn("No se pudo crear tabla servidores: {}", ex.getMessage()); }
            // Asegurar columnas nuevas en 'usuarios' (compatible con MySQL 5.7/8.0)
            ensureColumn(conn, "usuarios", "servidor_host", "ALTER TABLE usuarios ADD COLUMN servidor_host VARCHAR(100) NULL");
            ensureColumn(conn, "usuarios", "servidor_puerto", "ALTER TABLE usuarios ADD COLUMN servidor_puerto INT NULL");
            // Asegurar que id_emisor en mensajes_log permita NULL (para eventos del sistema P2P)
            try {
                stmt.execute("ALTER TABLE mensajes_log MODIFY COLUMN id_emisor INT NULL");
                logger.info("Columna id_emisor actualizada para permitir NULL");
            } catch (Exception ex) {
                logger.debug("No se pudo modificar id_emisor (puede que ya permita NULL): {}", ex.getMessage());
            }
                // Asegurar que tipo_mensaje tenga suficiente espacio para eventos P2P largos
                try {
                    stmt.execute("ALTER TABLE mensajes_log MODIFY COLUMN tipo_mensaje VARCHAR(50) NOT NULL");
                    logger.info("Columna tipo_mensaje actualizada a VARCHAR(50)");
                } catch (Exception ex) {
                    logger.debug("No se pudo modificar tipo_mensaje: {}", ex.getMessage());
                }
                // Migración: permitir id_receptor NULL para mensajes cross-server
            try {
                ensureColumnNullable(conn, "mensajes_log", "id_receptor");
            } catch (Exception e) {
                logger.warn("No se pudo hacer nullable id_receptor: {}", e.getMessage());
            }

            // Migraciones para soporte P2P en canales
            try {
                // Eliminar todas las FKs problemáticas de canales
                dropAllForeignKeysForTable(conn, "canales");
                logger.info("FKs de tabla canales eliminadas para soporte P2P");
            } catch (Exception e) {
                logger.warn("No se pudieron eliminar FKs de canales: {}", e.getMessage());
            }

            try {
                // Eliminar FK de id_usuario en canal_usuarios (mantener la de id_canal)
                dropSpecificForeignKey(conn, "canal_usuarios", "id_usuario");
                logger.info("FK de id_usuario en canal_usuarios eliminada para soporte P2P");
            } catch (Exception e) {
                logger.warn("No se pudo eliminar FK de id_usuario en canal_usuarios: {}", e.getMessage());
            }

            // Agregar columnas de servidor para identificación única en P2P
            try {
                // Agregar servidor_host y servidor_puerto al creador del canal
                ensureColumn(conn, "canales", "creador_servidor_host", 
                    "ALTER TABLE canales ADD COLUMN creador_servidor_host VARCHAR(255) DEFAULT NULL");
                ensureColumn(conn, "canales", "creador_servidor_puerto", 
                    "ALTER TABLE canales ADD COLUMN creador_servidor_puerto INT DEFAULT NULL");
                logger.info("Columnas de servidor agregadas a tabla canales");
            } catch (Exception e) {
                logger.warn("No se pudieron agregar columnas de servidor a canales: {}", e.getMessage());
            }

            try {
                // Agregar servidor_host y servidor_puerto a miembros del canal
                ensureColumn(conn, "canal_usuarios", "usuario_servidor_host", 
                    "ALTER TABLE canal_usuarios ADD COLUMN usuario_servidor_host VARCHAR(255) DEFAULT NULL");
                ensureColumn(conn, "canal_usuarios", "usuario_servidor_puerto", 
                    "ALTER TABLE canal_usuarios ADD COLUMN usuario_servidor_puerto INT DEFAULT NULL");
                logger.info("Columnas de servidor agregadas a tabla canal_usuarios");
            } catch (Exception e) {
                logger.warn("No se pudieron agregar columnas de servidor a canal_usuarios: {}", e.getMessage());
            }

            try {
                // Migración: Modificar PK de canal_usuarios para soportar usuarios multi-servidor
                migrarPrimaryKeyCanalUsuarios(conn);
                logger.info("Migración de PK de canal_usuarios completada");
            } catch (Exception e) {
                logger.warn("No se pudo migrar PK de canal_usuarios: {}", e.getMessage());
            }

            logger.info("Esquema de base de datos verificado exitosamente");
        } catch (SQLException e) {
            logger.error("Error verificando esquema de base de datos", e);
            throw new RuntimeException("No se pudo verificar el esquema de base de datos", e);
        }
    }

    /**
     * Eliminar una foreign key si existe.
     */
    private void dropForeignKeyIfExists(Connection conn, String table, String fkName) {
        // Verificar si la FK existe
        final String checkQuery = """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
            WHERE TABLE_SCHEMA = DATABASE() 
            AND TABLE_NAME = ? 
            AND CONSTRAINT_NAME = ?
            AND CONSTRAINT_TYPE = 'FOREIGN KEY'
            """;
        
        try (var ps = conn.prepareStatement(checkQuery)) {
            ps.setString(1, table);
            ps.setString(2, fkName);
            try (var rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    // La FK existe, eliminarla
                    String dropSql = "ALTER TABLE " + table + " DROP FOREIGN KEY " + fkName;
                    try (var st = conn.createStatement()) {
                        st.execute(dropSql);
                        logger.info("Foreign key {} eliminada de {}", fkName, table);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("No se pudo verificar/eliminar FK {}.{}: {}", table, fkName, e.getMessage());
        }
    }

    /**
     * Eliminar todas las foreign keys de una tabla.
     */
    private void dropAllForeignKeysForTable(Connection conn, String table) {
        final String query = """
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
            WHERE TABLE_SCHEMA = DATABASE() 
            AND TABLE_NAME = ? 
            AND CONSTRAINT_TYPE = 'FOREIGN KEY'
            """;
        
        try (var ps = conn.prepareStatement(query)) {
            ps.setString(1, table);
            java.util.List<String> fkNames = new java.util.ArrayList<>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    fkNames.add(rs.getString("CONSTRAINT_NAME"));
                }
            }
            
            // Eliminar cada FK encontrada
            for (String fkName : fkNames) {
                try {
                    String dropSql = "ALTER TABLE " + table + " DROP FOREIGN KEY " + fkName;
                    try (var st = conn.createStatement()) {
                        st.execute(dropSql);
                        logger.info("Foreign key {} eliminada de {}", fkName, table);
                    }
                } catch (Exception e) {
                    logger.warn("No se pudo eliminar FK {} de {}: {}", fkName, table, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.debug("No se pudieron listar FKs de {}: {}", table, e.getMessage());
        }
    }

    /**
     * Eliminar foreign keys relacionadas con una columna específica.
     */
    private void dropSpecificForeignKey(Connection conn, String table, String column) {
        final String query = """
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE 
            WHERE TABLE_SCHEMA = DATABASE() 
            AND TABLE_NAME = ? 
            AND COLUMN_NAME = ?
            AND REFERENCED_TABLE_NAME IS NOT NULL
            """;
        
        try (var ps = conn.prepareStatement(query)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fkName = rs.getString("CONSTRAINT_NAME");
                    try {
                        String dropSql = "ALTER TABLE " + table + " DROP FOREIGN KEY " + fkName;
                        try (var st = conn.createStatement()) {
                            st.execute(dropSql);
                            logger.info("Foreign key {} (columna {}) eliminada de {}", fkName, column, table);
                        }
                    } catch (Exception e) {
                        logger.warn("No se pudo eliminar FK {} de {}: {}", fkName, table, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("No se pudieron listar FKs de {}.{}: {}", table, column, e.getMessage());
        }
    }

    /**
     * Asegurar que una columna exista en una tabla; si no existe, ejecuta el ALTER.
     * Compatible con versiones de MySQL que no soportan 'ADD COLUMN IF NOT EXISTS'.
     */
    private void ensureColumn(Connection conn, String table, String column, String alterSql) {
        final String q = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (var ps = conn.prepareStatement(q)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (var rs = ps.executeQuery()) {
                int count = 0;
                if (rs.next()) count = rs.getInt(1);
                if (count == 0) {
                    try (var st = conn.createStatement()) {
                        st.execute(alterSql);
                        logger.info("Columna {}.{} agregada", table, column);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo verificar/agregar columna {}.{}: {}", table, column, e.getMessage());
        }
    }

    /**
     * Verificar si una columna existe en una tabla.
     */
    private boolean hasColumn(Connection conn, String tableName, String columnName) {
        String query = """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = DATABASE() 
            AND TABLE_NAME = ? 
            AND COLUMN_NAME = ?
            """;
        
        try (var ps = conn.prepareStatement(query)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            logger.debug("Error verificando columna {}.{}: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    /**
     * Migrar la clave primaria de canal_usuarios para soportar usuarios multi-servidor.
     * Este cambio permite que usuarios con el mismo ID de diferentes servidores
     * puedan coexistir en el mismo canal.
     */
    private void migrarPrimaryKeyCanalUsuarios(Connection conn) throws SQLException {
        // Verificar si ya tiene la estructura nueva (columna 'id')
        if (hasColumn(conn, "canal_usuarios", "id")) {
            logger.debug("La tabla canal_usuarios ya tiene la columna 'id', migración no necesaria");
            return;
        }
        
        logger.info("╔═══════════════════════════════════════════════════════════════");
        logger.info("║ INICIANDO MIGRACIÓN DE TABLA canal_usuarios");
        logger.info("║ Objetivo: Permitir usuarios con mismo ID de diferentes servidores");
        logger.info("╚═══════════════════════════════════════════════════════════════");
        
        try (var stmt = conn.createStatement()) {
            // 1. Eliminar FKs existentes
            logger.info("→ Paso 1: Eliminando Foreign Keys existentes...");
            dropAllForeignKeysForTable(conn, "canal_usuarios");
            
            // 2. Eliminar PK actual
            logger.info("→ Paso 2: Eliminando Primary Key actual...");
            stmt.execute("ALTER TABLE canal_usuarios DROP PRIMARY KEY");
            
            // 3. Agregar columna ID
            logger.info("→ Paso 3: Agregando columna ID autoincremental...");
            stmt.execute("ALTER TABLE canal_usuarios ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST");
            
            // 4. Crear índice único con servidor
            logger.info("→ Paso 4: Creando índice único que incluye servidor...");
            stmt.execute("""
                ALTER TABLE canal_usuarios ADD UNIQUE KEY uk_canal_usuario_servidor (
                    id_canal, 
                    id_usuario, 
                    usuario_servidor_host, 
                    usuario_servidor_puerto
                )
                """);
            
            // 5. Restaurar FKs
            logger.info("→ Paso 5: Restaurando Foreign Keys...");
            stmt.execute("ALTER TABLE canal_usuarios ADD FOREIGN KEY (id_canal) REFERENCES canales(id)");
            stmt.execute("ALTER TABLE canal_usuarios ADD FOREIGN KEY (id_usuario) REFERENCES usuarios(id)");
            
            logger.info("╔═══════════════════════════════════════════════════════════════");
            logger.info("║ ✓ MIGRACIÓN COMPLETADA EXITOSAMENTE");
            logger.info("║ La tabla canal_usuarios ahora soporta usuarios multi-servidor");
            logger.info("╚═══════════════════════════════════════════════════════════════");
        }
    }

    /**
     * Asegurar que una columna permita valores NULL.
     * Verifica el estado actual y solo aplica ALTER si es necesario.
     */
    private void ensureColumnNullable(Connection conn, String table, String column) {
        final String q = "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (var ps = conn.prepareStatement(q)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    String isNullable = rs.getString("IS_NULLABLE");
                    if ("NO".equals(isNullable)) {
                        // Columna existe pero no permite NULL, modificar
                        String alterSql = "ALTER TABLE " + table + " MODIFY COLUMN " + column + " INT NULL";
                        try (var st = conn.createStatement()) {
                            st.execute(alterSql);
                            logger.info("Columna {}.{} actualizada para permitir NULL", table, column);
                        }
                    } else {
                        logger.debug("Columna {}.{} ya permite NULL", table, column);
                    }
                } else {
                    logger.warn("Columna {}.{} no encontrada en la tabla", table, column);
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo verificar/modificar nullabilidad de {}.{}: {}", table, column, e.getMessage());
        }
    }
}
