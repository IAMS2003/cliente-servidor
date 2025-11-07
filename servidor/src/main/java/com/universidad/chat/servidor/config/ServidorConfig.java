package com.universidad.chat.servidor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuración del servidor y del subsistema P2P (entre servidores).
 */
public class ServidorConfig {
    private static final Logger logger = LoggerFactory.getLogger(ServidorConfig.class);

    private final String serverHost;
    private final int serverPort;
    private final boolean p2pEnabled;
    private final int p2pPort;
    private final List<String> p2pPeers; // lista de "host:puerto" de otros servidores

    public ServidorConfig() {
        String hostPredeterminado;
        try {
            hostPredeterminado = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            hostPredeterminado = "127.0.0.1";
        }

        Properties props = new Properties();
        // Permitir un archivo externo vía -Dconfig.file o variable de entorno CONFIG_FILE
        String externalConfig = System.getProperty("config.file", System.getenv("CONFIG_FILE"));
        boolean loaded = false;
        if (externalConfig != null && !externalConfig.isBlank()) {
            try (InputStream in = Files.newInputStream(Path.of(externalConfig))) {
                props.load(in);
                loaded = true;
                logger.info("ServidorConfig: usando archivo externo de configuración: {}", externalConfig);
            } catch (Exception e) {
                logger.warn("No se pudo leer config externo {}: {}", externalConfig, e.getMessage());
            }
        }
        if (!loaded) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (in != null) {
                    props.load(in);
                    logger.info("ServidorConfig: usando config.properties del classpath");
                } else {
                    logger.warn("No se encontró config.properties en el classpath");
                }
            } catch (Exception e) {
                logger.warn("No se pudo leer config.properties: {}", e.getMessage());
            }
        }

        // System properties tienen prioridad sobre config.properties
        this.serverHost = System.getProperty("server.host", props.getProperty("server.host", hostPredeterminado));
        this.serverPort = parseIntSafe(System.getProperty("server.port", props.getProperty("server.port", "8080")), 8080);
        this.p2pEnabled = Boolean.parseBoolean(System.getProperty("p2p.enabled", props.getProperty("p2p.enabled", "true")));
        this.p2pPort = parseIntSafe(System.getProperty("p2p.port", props.getProperty("p2p.port", "9090")), 9090);

        String peers = System.getProperty("p2p.peers", props.getProperty("p2p.peers", ""));
        List<String> tmp = new ArrayList<>();
        if (peers != null && !peers.isBlank()) {
            for (String p : peers.split(",")) {
                String v = p.trim();
                if (!v.isEmpty() && v.contains(":")) tmp.add(v);
            }
        }
        this.p2pPeers = java.util.Collections.unmodifiableList(tmp);

        logger.info("Config servidor: host={}, port={}, p2pEnabled={}, p2pPort={}, peers={}",
                serverHost, serverPort, p2pEnabled, p2pPort, p2pPeers);
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    public String getServerHost() { return serverHost; }
    public int getServerPort() { return serverPort; }
    public boolean isP2pEnabled() { return p2pEnabled; }
    public int getP2pPort() { return p2pPort; }
    public List<String> getP2pPeers() { return p2pPeers; }
}
