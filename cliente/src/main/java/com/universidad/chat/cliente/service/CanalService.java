package com.universidad.chat.cliente.service;

import com.universidad.chat.cliente.dao.CanalDAO;
import com.universidad.chat.cliente.model.Canal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class CanalService {
    private final CanalDAO canalDAO;

    public CanalService(Connection conn) {
        this.canalDAO = new CanalDAO(conn);
    }

    public void crearCanal(Canal canal) throws SQLException {
        canalDAO.crear(canal);
    }

    public Canal obtenerCanal(int id) throws SQLException {
        return canalDAO.obtenerPorId(id);
    }

    public List<Canal> listarCanales() throws SQLException {
        return canalDAO.listar();
    }
}
