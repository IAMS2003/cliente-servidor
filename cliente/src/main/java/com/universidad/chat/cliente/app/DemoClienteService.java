package com.universidad.chat.cliente.app;

import com.google.gson.JsonObject;
import com.universidad.chat.cliente.service.ClienteProtocoloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

public class DemoClienteService {
    private static final Logger logger = LoggerFactory.getLogger(DemoClienteService.class);

    public static void main(String[] args) throws Exception {
        try (ClienteProtocoloService svc = new ClienteProtocoloService()) {
            svc.conectar();
            JsonObject login = svc.autenticar("alice", "1234").join();
            logger.info("Login -> {}", login);

            JsonObject canales = svc.solicitarLista("canales").join();
            logger.info("Canales -> {}", canales);

            if (svc.getIdUsuario() != null) {
                String b64 = Base64.getEncoder().encodeToString("hola desde service".getBytes());
                JsonObject up = svc.enviarArchivoACanal(1, "saludo.txt", b64).join();
                logger.info("Upload canal -> {}", up);
            }

            svc.cerrarSesion().join();
        }
    }
}
