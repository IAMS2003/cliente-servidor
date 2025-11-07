-- ============================================================================
-- MIGRACIÓN DE TABLA canal_usuarios PARA SOPORTE MULTI-SERVIDOR
-- ============================================================================
-- 
-- PROBLEMA: La tabla canal_usuarios tiene PRIMARY KEY (id_canal, id_usuario)
-- lo que impide que usuarios con el mismo ID de diferentes servidores 
-- puedan estar en el mismo canal.
--
-- SOLUCIÓN: Cambiar la PK a una columna ID autoincremental y crear un 
-- índice único que incluya el servidor del usuario.
--
-- FECHA: Noviembre 7, 2025
-- ============================================================================

-- IMPORTANTE: Hacer backup antes de ejecutar
-- mysqldump -u root -p chat_servidor_1 > backup_antes_migracion.sql

USE chat_servidor_1;

-- Verificar estructura actual
SELECT 
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_KEY
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'canal_usuarios'
ORDER BY ORDINAL_POSITION;

-- Mostrar datos actuales (para verificar)
SELECT 
    id_canal,
    id_usuario,
    usuario_servidor_host,
    usuario_servidor_puerto,
    aceptado
FROM canal_usuarios
LIMIT 10;

-- ============================================================================
-- PASO 1: Eliminar Foreign Keys existentes
-- ============================================================================

-- Obtener nombres de FKs
SELECT 
    CONSTRAINT_NAME
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'canal_usuarios'
  AND CONSTRAINT_TYPE = 'FOREIGN KEY';

-- Eliminar FKs (ajustar nombres según tu BD)
-- Ejecuta estos comandos uno por uno y ajusta los nombres si son diferentes
ALTER TABLE canal_usuarios DROP FOREIGN KEY canal_usuarios_ibfk_1;
ALTER TABLE canal_usuarios DROP FOREIGN KEY canal_usuarios_ibfk_2;

-- ============================================================================
-- PASO 2: Eliminar Primary Key actual
-- ============================================================================

ALTER TABLE canal_usuarios DROP PRIMARY KEY;

-- ============================================================================
-- PASO 3: Agregar columna ID autoincremental como nueva PK
-- ============================================================================

ALTER TABLE canal_usuarios 
ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;

-- ============================================================================
-- PASO 4: Crear índice único que incluye servidor
-- ============================================================================

-- Este índice evita duplicados considerando el servidor del usuario
ALTER TABLE canal_usuarios 
ADD UNIQUE KEY uk_canal_usuario_servidor (
    id_canal, 
    id_usuario, 
    usuario_servidor_host, 
    usuario_servidor_puerto
);

-- ============================================================================
-- PASO 5: Restaurar Foreign Keys
-- ============================================================================

ALTER TABLE canal_usuarios 
ADD CONSTRAINT fk_canal_usuarios_canal 
FOREIGN KEY (id_canal) REFERENCES canales(id);

ALTER TABLE canal_usuarios 
ADD CONSTRAINT fk_canal_usuarios_usuario 
FOREIGN KEY (id_usuario) REFERENCES usuarios(id);

-- ============================================================================
-- VERIFICACIÓN FINAL
-- ============================================================================

-- Verificar nueva estructura
DESCRIBE canal_usuarios;

-- Verificar índices
SHOW INDEX FROM canal_usuarios;

-- Verificar FKs
SELECT 
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'canal_usuarios'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- ============================================================================
-- PRUEBA: Insertar usuarios con mismo ID de diferentes servidores
-- ============================================================================

-- Asumiendo que existe el canal 1 y el usuario 1
-- Insertar usuario 1 del servidor A
INSERT INTO canal_usuarios 
    (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado)
VALUES 
    (1, 1, 'localhost', 8080, TRUE)
ON DUPLICATE KEY UPDATE aceptado = TRUE;

-- Insertar usuario 1 del servidor B (mismo ID, diferente servidor)
INSERT INTO canal_usuarios 
    (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado)
VALUES 
    (1, 1, 'localhost', 8081, TRUE)
ON DUPLICATE KEY UPDATE aceptado = TRUE;

-- Verificar que ambos coexisten
SELECT 
    id,
    id_canal,
    id_usuario,
    usuario_servidor_host,
    usuario_servidor_puerto,
    aceptado
FROM canal_usuarios
WHERE id_canal = 1 AND id_usuario = 1;

-- Debería mostrar 2 filas:
-- - Usuario 1 de localhost:8080
-- - Usuario 1 de localhost:8081

-- ============================================================================
-- LIMPIAR DATOS DE PRUEBA (si ejecutaste las inserciones de prueba)
-- ============================================================================

-- DELETE FROM canal_usuarios WHERE id_canal = 1 AND id_usuario = 1;

-- ============================================================================
-- APLICAR A OTROS SERVIDORES
-- ============================================================================

-- Si tienes múltiples bases de datos (chat_servidor_2, chat_servidor_3), 
-- ejecuta los mismos comandos cambiando la línea USE:
--
-- USE chat_servidor_2;
-- [Ejecutar todos los comandos de migración]
--
-- USE chat_servidor_3;
-- [Ejecutar todos los comandos de migración]

-- ============================================================================
-- FIN DE LA MIGRACIÓN
-- ============================================================================

SELECT '✓ Migración completada exitosamente' AS STATUS;
SELECT 'Verificar que ambos usuarios con id=1 existen en el canal' AS NEXT_STEP;
