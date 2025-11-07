-- Aumenta tipo_mensaje a VARCHAR(50) para soportar nombres largos de eventos P2P

USE chat_servidor_1;
ALTER TABLE mensajes_log MODIFY COLUMN id_emisor INT NULL;
ALTER TABLE mensajes_log MODIFY COLUMN tipo_mensaje VARCHAR(50) NOT NULL;

USE chat_servidor_2;
ALTER TABLE mensajes_log MODIFY COLUMN id_emisor INT NULL;
ALTER TABLE mensajes_log MODIFY COLUMN tipo_mensaje VARCHAR(50) NOT NULL;

USE chat_servidor_3;
ALTER TABLE mensajes_log MODIFY COLUMN id_emisor INT NULL;
ALTER TABLE mensajes_log MODIFY COLUMN tipo_mensaje VARCHAR(50) NOT NULL;

SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    COLUMN_NAME,
    IS_NULLABLE,
    COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'mensajes_log' 
  AND COLUMN_NAME IN ('id_emisor', 'tipo_mensaje')
  AND TABLE_SCHEMA LIKE 'chat_servidor_%'
ORDER BY TABLE_SCHEMA;
