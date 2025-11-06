package com.universidad.chat.cliente.app;

import com.google.gson.JsonObject;
import com.universidad.chat.cliente.service.ClienteTCP;
import com.universidad.chat.cliente.util.Mensaje;
import com.universidad.chat.cliente.util.TipoMensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

public class PruebaProtocolo {
    private static final Logger logger = LoggerFactory.getLogger(PruebaProtocolo.class);
    private static AtomicInteger idUsuarioAutenticado = new AtomicInteger(0);

    public static void main(String[] args) {
        logger.info("=== Prueba Completa de Protocolo TCP-IP ===");

        try {
            ClienteTCP cliente = new ClienteTCP();
            cliente.addListener(new ClienteTCP.Listener() {
                @Override public void onMensaje(Mensaje mensaje) {
                    String cuerpo = mensaje.getCuerpoTexto();
                    logger.info("<< {} idUsuario={} cuerpo={}", mensaje.getTipo(), mensaje.getIdUsuario(), 
                        cuerpo.length() > 200 ? cuerpo.substring(0, 200) + "..." : cuerpo);
                    
                    // Capturar ID de usuario tras autenticación
                    if (mensaje.getTipo() == TipoMensaje.NOTIFICACION && cuerpo.contains("\"exito\":true") && cuerpo.contains("\"id\":")) {
                        try {
                            JsonObject obj = new com.google.gson.Gson().fromJson(cuerpo, JsonObject.class);
                            if (obj.has("id")) {
                                idUsuarioAutenticado.set(obj.get("id").getAsInt());
                                logger.info(">> ID autenticado: {}", idUsuarioAutenticado.get());
                            }
                        } catch (Exception ignored) {}
                    }
                }
                @Override public void onDesconexion() { logger.info("[cliente] desconectado"); }
            });
            
            cliente.conectar();
            Thread.sleep(500);

            logger.info(">> 1) Registro usuario alice");
            cliente.enviarRegistro("alice", "alice@uni.com", "1234", null);
            Thread.sleep(400);

            logger.info(">> 2) Autenticación alice");
            cliente.enviarAutenticacion("alice", "1234");
            Thread.sleep(400);

            int idAlice = idUsuarioAutenticado.get();
            logger.info(">> 3) Crear canal privado");
            cliente.crearCanal("Canal-Test", true);
            Thread.sleep(400);

            logger.info(">> 4) Solicitar lista de usuarios");
            cliente.solicitarLista("usuarios");
            Thread.sleep(400);

            logger.info(">> 5) Solicitar lista de conectados");
            cliente.solicitarLista("conectados");
            Thread.sleep(400);

            logger.info(">> 6) Solicitar lista de canales");
            cliente.solicitarLista("canales");
            Thread.sleep(400);

            logger.info(">> 7) Enviar mensaje directo a usuario 2 (puede no existir)");
            cliente.enviarMensajeTextoAUsuario(idAlice, 2, "Hola usuario 2!");
            Thread.sleep(400);

            logger.info(">> 8) Enviar mensaje a canal 1");
            cliente.enviarMensajeCanal(idAlice, 1, "Hola canal 1!");
            Thread.sleep(400);

            logger.info(">> 9) Enviar archivo a usuario 2");
            String archivoBase64 = Base64.getEncoder().encodeToString("Este es el contenido del archivo de prueba".getBytes());
            cliente.enviarArchivoAUsuario(idAlice, 2, "nota.txt", archivoBase64);
            Thread.sleep(400);

            logger.info(">> 10) Enviar archivo a canal 1");
            cliente.enviarArchivoACanal(idAlice, 1, "documento.txt", archivoBase64);
            Thread.sleep(400);

            logger.info(">> 11) Solicitar unirse a canal 1");
            cliente.solicitarUnirseACanal(1);
            Thread.sleep(400);

            logger.info(">> 12) Cerrar sesión");
            cliente.cerrarSesion(idAlice);
            Thread.sleep(500);

            cliente.desconectar();
            logger.info("=== Prueba Completa Finalizada ===");
        } catch (Exception e) {
            logger.error("Error en la prueba", e);
        }
        System.exit(0);
    }
}
