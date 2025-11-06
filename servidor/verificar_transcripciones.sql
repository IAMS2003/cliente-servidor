-- Script para verificar las transcripciones en la base de datos
-- Ejecuta esto en MySQL Workbench o cliente MySQL

-- Ver los últimos 10 mensajes de audio con sus transcripciones
SELECT 
    id,
    id_emisor,
    id_receptor,
    id_canal,
    archivo_audio,
    CASE 
        WHEN transcripcion IS NULL THEN '(NULL)'
        WHEN transcripcion = '' THEN '(VACÍO)'
        ELSE transcripcion 
    END as transcripcion_estado,
    transcripcion,
    fecha
FROM mensajes_log 
WHERE tipo_mensaje = 'AUDIO'
ORDER BY fecha DESC 
LIMIT 10;

-- Contar mensajes de audio por estado de transcripción
SELECT 
    CASE 
        WHEN transcripcion IS NULL THEN 'NULL'
        WHEN transcripcion = '' THEN 'VACÍO'
        ELSE 'CON TEXTO'
    END as estado_transcripcion,
    COUNT(*) as cantidad
FROM mensajes_log 
WHERE tipo_mensaje = 'AUDIO'
GROUP BY estado_transcripcion;

-- Ver estructura de la tabla para confirmar que existe la columna
DESCRIBE mensajes_log;
