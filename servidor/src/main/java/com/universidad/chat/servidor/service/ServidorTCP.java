package com.universidad.chat.servidor.service;

import com.universidad.chat.servidor.model.Usuario;
import com.universidad.chat.servidor.util.Mensaje;
import com.universidad.chat.servidor.util.TipoMensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor TCP que maneja conexiones de clientes.
 * Aplica patrón Object Pool para gestionar hilos.
 */
public class ServidorTCP {
    private static final Logger logger = LoggerFactory.getLogger(ServidorTCP.class);
    private static final int PUERTO = 8080;
    private static final int MAX_CLIENTES = 100;

    private ServerSocket serverSocket;
    private ExecutorService poolHilos;
    private ConcurrentHashMap<Integer, ManejadorCliente> clientesConectados;
    private boolean ejecutando;
    
    // Servicios de negocio
    private UsuarioService usuarioService;
    private CanalService canalService;
    private MensajeService mensajeService;

    public ServidorTCP() {
        this.poolHilos = Executors.newFixedThreadPool(MAX_CLIENTES); // Object Pool pattern
        this.clientesConectados = new ConcurrentHashMap<>();
        this.ejecutando = false;
        
        // Inicializar servicios
        this.usuarioService = new UsuarioService();
        this.canalService = new CanalService();
        this.mensajeService = new MensajeService();
    }

    public void iniciar() throws IOException {
        serverSocket = new ServerSocket(PUERTO);
        ejecutando = true;
        logger.info("Servidor TCP iniciado en puerto {}", PUERTO);

        while (ejecutando) {
            try {
                Socket clienteSocket = serverSocket.accept();
                String remoteIp = clienteSocket.getInetAddress().getHostAddress();
                int remotePort = clienteSocket.getPort();
                String localIp = clienteSocket.getLocalAddress().getHostAddress();
                int localPort = clienteSocket.getLocalPort();
                logger.info("Nueva conexión desde {}:{} hacia {}:{}", remoteIp, remotePort, localIp, localPort);
                
                poolHilos.execute(new ManejadorCliente(clienteSocket));
            } catch (IOException e) {
                if (ejecutando) {
                    logger.error("Error aceptando conexión", e);
                }
            }
        }
    }

    public void detener() {
        ejecutando = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            poolHilos.shutdown();
        } catch (IOException e) {
            logger.error("Error cerrando servidor", e);
        }
    }

    /**
     * Manejador de cliente individual.
     */
    private class ManejadorCliente implements Runnable {
        private final Socket socket;
        private DataInputStream entrada;
        private DataOutputStream salida;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                entrada = new DataInputStream(socket.getInputStream());
                salida = new DataOutputStream(socket.getOutputStream());

                while (ejecutando) {
                    // Leer cabecera del mensaje (10 bytes)
                    byte version = entrada.readByte();
                    byte tipoCodigo = entrada.readByte();
                    int longitudCuerpo = entrada.readInt();
                    int idUsuario = entrada.readInt();

                    // Leer cuerpo
                    byte[] cuerpo = new byte[longitudCuerpo];
                    entrada.readFully(cuerpo);

                    // Construir mensaje
                    Mensaje mensaje = new Mensaje(TipoMensaje.fromCodigo(tipoCodigo), idUsuario, cuerpo);
                    logger.info("Mensaje recibido: {}", mensaje);

                    // Procesar mensaje
                    procesarMensaje(mensaje);
                }
            } catch (EOFException e) {
                logger.info("Cliente desconectado: {}:{}", socket.getInetAddress().getHostAddress(), socket.getPort());
            } catch (IOException e) {
                logger.error("Error procesando cliente", e);
            } finally {
                cerrarConexion();
            }
        }

        private void procesarMensaje(Mensaje mensaje) {
            // TODO: Implementar lógica según tipo de mensaje
            try {
                // Respuesta de prueba
                Mensaje respuesta = new Mensaje(TipoMensaje.NOTIFICACION, 0, "Mensaje recibido");
                enviarMensaje(respuesta);
            } catch (IOException e) {
                logger.error("Error enviando respuesta", e);
            }
        }

        private void enviarMensaje(Mensaje mensaje) throws IOException {
            byte[] datos = mensaje.serializar();
            salida.write(datos);
            salida.flush();
        }

        private void cerrarConexion() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                logger.error("Error cerrando socket", e);
            }
        }
    }
}
