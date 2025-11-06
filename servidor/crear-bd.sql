-- Script para crear la base de datos del servidor
CREATE DATABASE IF NOT EXISTS chat_servidor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chat_servidor;

-- Las tablas se crearán automáticamente al ejecutar ServidorAppIntegrado
-- gracias al método inicializarEsquema() de ConexionBD
