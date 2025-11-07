-- Script SQL para crear bases de datos para múltiples servidores

-- Crear las 3 bases de datos
CREATE DATABASE IF NOT EXISTS chat_servidor_1;
CREATE DATABASE IF NOT EXISTS chat_servidor_2;
CREATE DATABASE IF NOT EXISTS chat_servidor_3;

-- Verificar que se crearon correctamente
SHOW DATABASES LIKE 'chat_servidor_%';

-- Las tablas se crearán automáticamente cuando inicie cada servidor
-- gracias al código de ConexionBD.java que ejecuta el DDL al iniciar
