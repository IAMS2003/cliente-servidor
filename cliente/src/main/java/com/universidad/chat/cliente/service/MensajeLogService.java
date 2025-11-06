package com.universidad.chat.cliente.service;

import com.universidad.chat.cliente.dao.MensajeLogDAO;
import com.universidad.chat.cliente.model.MensajeLog;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class MensajeLogService {
    private final MensajeLogDAO mensajeLogDAO;

    public MensajeLogService(Connection conn) {
        this.mensajeLogDAO = new MensajeLogDAO(conn);
    }

    public void registrarMensaje(MensajeLog mensaje) throws SQLException {
        mensajeLogDAO.crear(mensaje);
    }

    public List<MensajeLog> listarMensajesPorCanal(int canalId) throws SQLException {
        return mensajeLogDAO.listarPorCanal(canalId);
    }
}
