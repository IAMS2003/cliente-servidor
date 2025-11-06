# Guía Rápida de Pruebas - Persistencia de Mensajes

## Pre-requisitos

1. **Servidor corriendo** con MySQL activo
2. **Cliente compilado** (`mvn clean package` en directorio cliente)
3. **Base de datos H2** se creará automáticamente en `cliente/data/`

## Ejecutar el Cliente

```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\cliente
mvn javafx:run
```

## Escenarios de Prueba

### Prueba 1: Persistencia de Mensaje de Texto

**Pasos**:
1. Iniciar sesión con usuario (ej: usuario1/password1)
2. Seleccionar un canal o usuario destino
3. Escribir mensaje de texto y enviar
4. ✅ Verificar que aparece en el chat
5. Cerrar completamente la aplicación
6. Volver a iniciar sesión con el mismo usuario
7. Seleccionar el mismo canal/usuario
8. ✅ El mensaje debe aparecer en el historial

**Resultado esperado**: El mensaje enviado aparece en el historial sin necesidad de solicitarlo al servidor

---

### Prueba 2: Persistencia de Mensaje de Audio

**Pasos**:
1. Iniciar sesión
2. Seleccionar destino
3. Presionar 🎤 Grabar
4. Hablar por unos segundos
5. Presionar ⏹ Detener
6. ✅ Verificar que aparece mensaje "[Audio]" con botón ▶
7. Presionar ▶ para reproducir
8. ✅ El audio debe reproducirse correctamente
9. Cerrar aplicación
10. Volver a iniciar sesión
11. Abrir mismo chat
12. ✅ El mensaje de audio debe aparecer con botón ▶
13. Presionar ▶
14. ✅ El audio debe reproducirse desde la BD local

**Resultado esperado**: El audio se reproduce correctamente incluso después de reiniciar la aplicación

---

### Prueba 3: Mensajes Entrantes

**Pasos**:
1. Iniciar sesión con usuario1
2. En otra instancia, iniciar sesión con usuario2
3. Desde usuario2, enviar mensaje a usuario1
4. En ventana de usuario1:
   - ✅ El mensaje debe aparecer automáticamente
5. Cerrar sesión de usuario1
6. Volver a iniciar sesión con usuario1
7. Seleccionar chat con usuario2
8. ✅ El mensaje recibido debe aparecer en el historial

**Resultado esperado**: Los mensajes recibidos se guardan automáticamente y persisten

---

### Prueba 4: Mensajes en Canal

**Pasos**:
1. Iniciar sesión
2. Unirse a un canal (ej: "General")
3. Enviar varios mensajes de texto
4. Enviar un mensaje de audio
5. Cerrar aplicación
6. Volver a iniciar sesión
7. Seleccionar el mismo canal
8. ✅ Todos los mensajes deben aparecer en el historial
9. ✅ El audio debe poder reproducirse

**Resultado esperado**: El historial del canal se mantiene completo con texto y audio

---

## Verificación en Base de Datos H2

### Opción 1: H2 Console Web

```powershell
# Descargar H2 JAR si no lo tienes
# URL: https://www.h2database.com/html/download.html

# O usar el de Maven:
$h2Jar = "$env:USERPROFILE\.m2\repository\com\h2database\h2\2.2.224\h2-2.224.jar"

# Iniciar H2 Console
java -jar $h2Jar
```

**Configuración de conexión**:
- Driver Class: `org.h2.Driver`
- JDBC URL: `jdbc:h2:c:/Users/monto/universidad/Arqui/cliente-servidor/cliente/data/chat_cliente`
- User Name: `sa`
- Password: (dejar vacío)

**Consultas útiles**:

```sql
-- Ver todos los mensajes
SELECT * FROM mensaje_log ORDER BY fecha DESC;

-- Contar mensajes por tipo
SELECT tipo_mensaje, COUNT(*) 
FROM mensaje_log 
GROUP BY tipo_mensaje;

-- Ver mensajes de un usuario específico
SELECT * 
FROM mensaje_log 
WHERE id_emisor = 1 OR id_receptor = 1
ORDER BY fecha DESC;

-- Ver mensajes de un canal específico
SELECT * 
FROM mensaje_log 
WHERE id_canal = 1
ORDER BY fecha DESC;

-- Ver mensajes con audio
SELECT id, id_emisor, transcripcion, 
       LENGTH(audio_data) as tamaño_audio_bytes,
       fecha
FROM mensaje_log 
WHERE tipo_mensaje = 'AUDIO'
ORDER BY fecha DESC;

-- Verificar que los audios se guardaron correctamente
SELECT id, 
       CASE WHEN audio_data IS NULL THEN 'NO' ELSE 'SÍ' END as tiene_audio,
       LENGTH(audio_data) as bytes
FROM mensaje_log 
WHERE tipo_mensaje = 'AUDIO';
```

### Opción 2: Desde PowerShell

```powershell
# Crear script SQL
@"
SELECT COUNT(*) as total_mensajes FROM mensaje_log;
SELECT tipo_mensaje, COUNT(*) FROM mensaje_log GROUP BY tipo_mensaje;
"@ | Out-File -Encoding UTF8 test.sql

# Ejecutar con H2 Shell
$h2Jar = "$env:USERPROFILE\.m2\repository\com\h2database\h2\2.2.224\h2-2.224.jar"
java -cp $h2Jar org.h2.tools.Shell `
  -url "jdbc:h2:./data/chat_cliente" `
  -user sa `
  -sql "SELECT COUNT(*) FROM mensaje_log"
```

---

## Verificación de Archivos

### Comprobar que la BD se creó:

```powershell
# Ver archivos de la base de datos
dir c:\Users\monto\universidad\Arqui\cliente-servidor\cliente\data\

# Debería mostrar:
# - chat_cliente.mv.db (archivo principal)
# - chat_cliente.trace.db (opcional, logs de H2)
```

### Tamaño esperado:

```powershell
# Ver tamaño de la BD
(Get-Item "c:\Users\monto\universidad\Arqui\cliente-servidor\cliente\data\chat_cliente.mv.db").Length / 1KB

# Si hay mensajes con audio, el tamaño debería ser > 100 KB
```

---

## Solución de Problemas

### ❌ No se crea la carpeta `data/`

**Causa**: Error en `ConexionBD.crearDirectorioBD()`  
**Solución**: Verificar logs en consola al iniciar el cliente

### ❌ Error: "Table not found: mensaje_log"

**Causa**: `inicializarEsquema()` no se ejecutó  
**Solución**: Verificar que `ClienteApp.start()` llama a `ConexionBD.getInstancia().inicializarEsquema()`

### ❌ Los mensajes no se guardan

**Causa**: Excepción silenciosa en `try-catch`  
**Solución**: Revisar consola para mensajes de error:
```
Error al guardar mensaje en BD: ...
```

### ❌ Los audios no se reproducen desde BD

**Causa**: BLOB no se guardó correctamente  
**Solución**: 
1. Verificar en H2 Console: `SELECT LENGTH(audio_data) FROM mensaje_log WHERE tipo_mensaje = 'AUDIO'`
2. Si es NULL o 0, revisar conversión Base64 → bytes en `MensajePersistenciaService`

### ❌ El historial no se carga

**Causa**: Consulta SQL incorrecta  
**Solución**: 
1. Verificar logs de `MensajeLogDAO`
2. Ejecutar consulta manualmente en H2 Console

---

## Checklist de Validación

Antes de dar por completada la implementación, verificar:

- [ ] La carpeta `cliente/data/` se crea automáticamente
- [ ] El archivo `chat_cliente.mv.db` existe
- [ ] Los mensajes de texto se guardan correctamente
- [ ] Los mensajes de audio se guardan con BLOB
- [ ] El historial se carga desde BD (no desde servidor)
- [ ] Los audios se reproducen desde la BD
- [ ] Los mensajes entrantes se guardan automáticamente
- [ ] Los mensajes salientes se guardan antes de enviar
- [ ] La aplicación funciona sin conexión al servidor (solo historial local)

---

## Logs Útiles

### Ver logs de la aplicación:

Los mensajes de debug aparecen en la consola donde se ejecuta `mvn javafx:run`:

```
[BD] Creando directorio: c:\Users\monto\...\cliente\data
[BD] Driver H2 cargado correctamente
[BD] Inicializando esquema de base de datos...
[BD] Esquema inicializado correctamente
[MensajePersistenciaService] Mensaje guardado con ID: 1
[MensajePersistenciaService] Cargados 5 mensajes
```

### Errores comunes en logs:

```
Error al guardar mensaje en BD: ...
  → Revisar esquema de BD y campos

[BD] Error al crear directorio: ...
  → Permisos de escritura

Table "MENSAJE_LOG" not found
  → inicializarEsquema() no se ejecutó
```

---

## Rendimiento Esperado

- **Guardar mensaje de texto**: < 10ms
- **Guardar mensaje de audio** (50KB): < 50ms
- **Cargar historial** (100 mensajes): < 100ms
- **Reproducir audio desde BD**: < 200ms (primer acceso)

Si los tiempos son significativamente mayores, verificar índices en la BD.

---

## Siguiente Fase (Opcional)

- [ ] Añadir índices a la tabla `mensaje_log` para mejorar consultas
- [ ] Implementar caché de mensajes en memoria
- [ ] Añadir función de búsqueda de mensajes por contenido
- [ ] Implementar paginación para conversaciones largas (> 1000 mensajes)
- [ ] Añadir sincronización bidireccional con servidor (merge de historiales)
