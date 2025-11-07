-- Migración: Permitir NULL en id_receptor para mensajes cross-server
-- Fecha: 2024
-- Descripción: Cuando se envían mensajes entre servidores, el receptor puede
--              no estar registrado en la base de datos local. Esta migración
--              permite que id_receptor sea NULL en esos casos.

-- Verificar estado actual de la columna
SELECT 
    COLUMN_NAME, 
    IS_NULLABLE, 
    COLUMN_TYPE 
FROM 
    INFORMATION_SCHEMA.COLUMNS 
WHERE 
    TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'mensajes_log' 
    AND COLUMN_NAME = 'id_receptor';

-- Modificar columna para permitir NULL
ALTER TABLE mensajes_log MODIFY COLUMN id_receptor INT NULL;

-- Verificar cambio aplicado
SELECT 
    COLUMN_NAME, 
    IS_NULLABLE, 
    COLUMN_TYPE 
FROM 
    INFORMATION_SCHEMA.COLUMNS 
WHERE 
    TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'mensajes_log' 
    AND COLUMN_NAME = 'id_receptor';
