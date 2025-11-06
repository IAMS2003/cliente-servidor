# Diagnóstico de Transcripciones VOSK

## ✅ Cambios realizados para mejorar el logging

He agregado logging detallado en varios puntos para diagnosticar el problema:

### 1. ServidorTCPIntegrado.procesarMensajeAudio()
- **Verifica si el modelo está cargado** antes de intentar transcribir
- Si no está: muestra warning con instrucciones de descarga
- Si está: registra el inicio, resultado y posibles errores

### 2. MensajeLogDAO.registrar()
- Registra qué valores se están insertando en la BD

## 🔍 Cómo diagnosticar el problema

### Paso 1: Reinicia el servidor
Después de compilar, reinicia el servidor y observa los logs al arrancar.

**Busca estas líneas:**

✅ **Si el modelo está OK:**
```
[INFO] Cargando modelo VOSK desde: vosk-models/vosk-model-small-es-0.42
[INFO] Modelo VOSK cargado exitosamente
```

❌ **Si el modelo NO está:**
```
[WARN] Modelo VOSK no encontrado en: vosk-models/vosk-model-small-es-0.42
[WARN] Descarga el modelo desde: https://alphacephei.com/vosk/models
```

### Paso 2: Envía un audio desde el cliente

Observa los logs del servidor. Deberías ver:

```
[INFO] Iniciando transcripción de audio (XXXXX bytes)...
[INFO] ✓ Audio transcrito exitosamente: 'hola mundo'
[INFO] Registrando mensaje de audio en BD - Canal: X, Transcripción: 'hola mundo'
[DEBUG] Insertando mensaje - Tipo: AUDIO, Archivo: ..., Transcripción: 'hola mundo'
[INFO] Mensaje registrado en log ID: XXX con transcripción
```

### Paso 3: Verifica la base de datos

Ejecuta el script SQL `verificar_transcripciones.sql`:

```sql
SELECT id, archivo_audio, transcripcion, fecha 
FROM mensajes_log 
WHERE tipo_mensaje = 'AUDIO' 
ORDER BY fecha DESC 
LIMIT 5;
```

## 🐛 Problemas comunes y soluciones

### Problema 1: Modelo VOSK no cargado
**Síntoma:** Logs dicen "Servicio de transcripción no disponible"

**Solución:**
1. Descarga: https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip
2. Descomprime
3. Copia la carpeta `vosk-model-small-es-0.42` a `servidor/vosk-models/`
4. Reinicia el servidor

### Problema 2: Transcripción vacía
**Síntoma:** Logs dicen "Transcripción resultó vacía"

**Causas posibles:**
- El audio es muy corto (menos de 1 segundo)
- El audio es silencio
- El audio tiene mucho ruido de fondo
- El formato del audio no es correcto (debe ser WAV 16kHz mono 16-bit)

**Solución:**
- Prueba grabando un audio de 3-5 segundos diciendo algo claro
- Verifica que el micrófono funciona correctamente

### Problema 3: Columna no existe en BD
**Síntoma:** Error SQL "Unknown column 'transcripcion'"

**Solución:** Ejecuta este SQL para agregar la columna:
```sql
ALTER TABLE mensajes_log 
ADD COLUMN IF NOT EXISTS transcripcion TEXT;
```

### Problema 4: NullPointerException en transcripcionService
**Síntoma:** Error en logs al llamar a `transcribir()`

**Solución:** Ya lo manejamos con el check `estaListo()`, pero si persiste:
- Verifica que TranscripcionService se está instanciando en el constructor
- Revisa que no hay errores de dependencias de VOSK

## 📊 Estado esperado

Una vez todo funcione correctamente, al enviar un audio deberías ver:

1. **En los logs del servidor:**
   - "✓ Audio transcrito exitosamente: '[tu texto aquí]'"
   
2. **En Informes → Audio (UI servidor):**
   - Columna "Transcripción" con el texto reconocido
   
3. **En la base de datos:**
   - Campo `transcripcion` con el texto (no NULL, no vacío)

## 🔧 Pasos siguientes

1. **Reinicia** el servidor con el código actualizado
2. **Revisa** los logs al arrancar
3. **Envía** un audio de prueba
4. **Verifica** que aparece la transcripción en Informes → Audio
5. **Si hay problemas**, copia los logs y compartelos para analizar

---

**Nota:** El logging adicional puede hacer que los logs sean más verbosos. Una vez confirmado que funciona, puedes cambiar algunos `logger.info()` a `logger.debug()` si lo prefieres.
