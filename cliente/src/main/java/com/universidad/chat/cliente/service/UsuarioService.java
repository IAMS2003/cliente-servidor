package com.universidad.chat.cliente.service;

import com.universidad.chat.cliente.dao.UsuarioDAO;
import com.universidad.chat.cliente.model.Usuario;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class UsuarioService {
    private final UsuarioDAO usuarioDAO;

    public UsuarioService(Connection conn) {
        this.usuarioDAO = new UsuarioDAO(conn);
    }

    public void registrarUsuario(Usuario usuario) throws SQLException {
        usuarioDAO.crear(usuario);
    }

    public Usuario obtenerUsuario(int id) throws SQLException {
        return usuarioDAO.obtenerPorId(id);
    }

    public List<Usuario> listarUsuarios() throws SQLException {
        return usuarioDAO.listar();
    }
}
