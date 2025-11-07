# Funcionalidad de Reproducción de Audio

## Descripción General

El sistema de chat ahora soporta grabación, envío y reproducción de mensajes de audio en tiempo real.

## Características Implementadas

### 1. Grabación de Audio (Cliente)
- **Botón**: "🎤 Grabar" en la interfaz de chat
- **Funcionalidad**: 
  - Click para iniciar grabación (botón cambia a "⏹ Detener" con fondo rojo)
  - Click nuevamente para detener y enviar el audio
- **Formato**: PCM 16-bit mono a 16kHz, convertido a WAV
- **Clase**: `AudioRecorder.java` en `cliente/util/`

### 2. Almacenamiento de Audio (Servidor)
- **Ubicación**: `uploads/audios/` en el directorio del servidor
- **Formato de nombre**: `audio_<timestamp>_<idEmisor>.wav`
- **Base de datos**: La ruta del archivo se guarda en `mensajes_log.archivo_audio`
- **Transmisión**: El servidor envía tanto la ruta como los bytes del audio (Base64) a los receptores

### 3. Reproducción de Audio (Cliente)

#### Recepción de Audio
Cuando recibes un mensaje de audio, aparecerá en el chat con este formato:
```
Usuario 2 [Audio]
Para reproducir, escribe: /play 1698765432
```

O si hay transcripción:
```
Usuario 2 [Audio]: Hola, este es un mensaje de voz
Para reproducir, escribe: /play 1698765432
```

#### Reproducir Audio
Para reproducir un audio:
1. Escribe en el campo de mensaje: `/play <ID>`
   - Ejemplo: `/play 1698765432`
2. Presiona Enter o click en "Enviar"

#### Controles de Reproducción
- **Primera vez**: Reproduce el audio desde el inicio
- **Durante reproducción**: Escribe `/play <ID>` nuevamente para pausar
- **Audio pausado**: Escribe `/play <ID>` para continuar desde donde se pausó

Los mensajes de estado aparecerán en el chat:
- `[▶️ Reproduciendo audio...]` - Iniciando reproducción
- `[▶️ Reproduciendo...]` - Reanudando reproducción
- `[⏸️ Audio pausado]` - Audio pausado
- `[Error] Audio no encontrado` - ID inválido

## Arquitectura Técnica

### Flujo de Datos

```
Cliente 1 (Grabación)
    ↓
AudioRecorder.java (Captura PCM → WAV)
    ↓
Base64 Encoding
    ↓
TCP Protocol (MENSAJE_AUDIO)
    ↓
Servidor (ServidorTCPIntegrado.java)
    ↓
├─ Guardar archivo WAV en uploads/audios/
├─ Registrar en DB (mensajes_log)
└─ Reenviar con audioData en Base64
    ↓
Cliente 2 (Recepción)
    ↓
Base64 Decoding
    ↓
AudioPlayer.java (Javax Sound API)
    ↓
Reproducción con Play/Pause
```

### Clases Principales

#### Servidor
- **ServidorTCPIntegrado.java**
  - Método: `procesarMensajeAudio(int idUsuario, String cuerpoTexto)`
  - Funcionalidad: Decodifica audio Base64, guarda archivo, reenvía con datos

#### Cliente
- **AudioRecorder.java**
  - `startRecording()`: Inicia captura desde micrófono
  - `stopRecording()`: Detiene y retorna bytes WAV

- **AudioPlayer.java** (NUEVO)
  - `loadAudio(byte[] audioData)`: Carga audio desde bytes
  - `play()`: Reproduce audio
  - `pause()`: Pausa reproducción
  - `stop()`: Detiene y reinicia posición
  - `isPlaying()`: Verifica si está reproduciendo
  - `isPaused()`: Verifica si está pausado

- **ChatView.java**
  - `agregarMensajeAudio()`: Agrega mensaje de audio al chat con ID
  - `reproducirAudio(String audioId)`: Reproduce/pausa audio por ID
  - Comando `/play` interceptado en `enviarMensaje()`

- **ClienteProtocoloService.java**
  - Callbacks modificados:
    - `onAudioUsuario(int idEmisor, String archivoAudio, String transcripcion, String audioData)`
    - `onAudioCanal(int idCanal, int idEmisor, String archivoAudio, String transcripcion, String audioData)`

## Protocolo de Mensaje de Audio

### Envío (Cliente → Servidor)
```json
{
  "tipo": "MENSAJE_AUDIO",
  "audioData": "<Base64-encoded-WAV>",
  "idReceptor": 3,  // Para mensajes directos
  "idCanal": 1,     // Para mensajes de canal
  "transcripcion": "" // Opcional
}
```

### Recepción (Servidor → Cliente)
```json
{
  "tipo": "MENSAJE_AUDIO",
  "idEmisor": 2,
  "idCanal": 1,  // Solo para canales
  "archivoAudio": "uploads/audios/audio_1698765432_2.wav",
  "audioData": "<Base64-encoded-WAV>",  // Para reproducción
  "transcripcion": ""
}
```

## Requisitos del Sistema

- **Java Sound API**: Incluido en JDK (javax.sound.sampled)
- **Micrófono**: Necesario para grabar audio
- **Parlantes/Audífonos**: Necesarios para reproducir audio
- **Permisos**: El sistema operativo debe permitir acceso al micrófono

## Almacenamiento y Gestión

### Servidor
- **Directorio**: `uploads/audios/` (creado automáticamente si no existe)
- **Formato**: Archivos WAV estándar
- **Tamaño**: Depende de la duración de la grabación (~32 KB/segundo)
- **Persistencia**: Los archivos permanecen en el servidor después del envío

### Cliente
- **Memoria**: Los audios recibidos se mantienen en RAM (`Map<String, byte[]>`)
- **Reproductores**: Un `AudioPlayer` por cada audio cargado (`Map<String, AudioPlayer>`)
- **Limpieza**: Se liberan al cerrar la aplicación

## Limitaciones Conocidas

1. **UI Simplificada**: Los controles de reproducción son mediante comandos de texto
   - Mejora futura: Botones gráficos integrados en cada mensaje

2. **Sin barra de progreso**: No se muestra el progreso de reproducción
   - Se puede implementar con `AudioPlayer.getPosition()` y `getDuration()`

3. **Sin visualización de forma de onda**: Los mensajes de audio son texto plano
   - Mejora futura: Visualización gráfica de la onda de audio

4. **Gestión de memoria**: Todos los audios se mantienen en RAM
   - Mejora futura: Límite de audios en memoria o descarga bajo demanda

## Solución de Problemas

### "Error al iniciar grabación"
- Verifica que el micrófono esté conectado
- Verifica permisos de la aplicación para acceder al micrófono
- Comprueba que no haya otra aplicación usando el micrófono

### "Audio no encontrado"
- Verifica que el ID del audio sea correcto
- El ID debe coincidir exactamente con el mostrado en el mensaje

### "No se pudo reproducir el audio"
- Verifica que los parlantes/audífonos estén conectados
- Comprueba que el formato de audio sea compatible (WAV 16kHz mono)
- Revisa los logs del servidor para errores de codificación

## Pruebas

### Probar Grabación
1. Abrir cliente y conectarse
2. Seleccionar un chat (usuario o canal)
3. Click en "🎤 Grabar"
4. Hablar durante unos segundos
5. Click en "⏹ Detener"
6. Verificar mensaje "[Tú - Audio enviado]"

### Probar Reproducción
1. Esperar a recibir un mensaje de audio
2. Copiar el ID mostrado (ej: `1698765432`)
3. Escribir `/play 1698765432` y presionar Enter
4. Verificar mensaje "[▶️ Reproduciendo audio...]"
5. Escribir `/play 1698765432` nuevamente para pausar
6. Verificar mensaje "[⏸️ Audio pausado]"

### Verificar en Servidor
1. Navegar a `servidor/uploads/audios/`
2. Verificar que exista el archivo WAV
3. Reproducir manualmente con cualquier reproductor de audio
4. Verificar en base de datos la entrada en `mensajes_log` con `archivo_audio` no nulo

## Código de Ejemplo

### Reproducir Audio Programáticamente
```java
// En ChatView o cualquier clase con acceso al mapa de reproductores
String audioId = "audio_1698765432_123";
AudioPlayer player = audioPlayers.get(audioId);

if (player != null) {
    if (!player.isPlaying()) {
        player.play();  // Reproducir
    } else {
        player.pause(); // Pausar
    }
}
```

### Verificar Estado del Reproductor
```java
AudioPlayer player = audioPlayers.get(audioId);
if (player != null) {
    boolean playing = player.isPlaying();
    boolean paused = player.isPaused();
    long duration = player.getDuration();    // Microsegundos
    long position = player.getPosition();    // Microsegundos
    
    double durationSec = duration / 1_000_000.0;
    double positionSec = position / 1_000_000.0;
}
```

## Próximas Mejoras

- [ ] Interfaz gráfica con botones de play/pause por mensaje
- [ ] Barra de progreso de reproducción
- [ ] Control de volumen
- [ ] Visualización de forma de onda
- [ ] Descarga bajo demanda (no mantener todo en RAM)
- [ ] Soporte para otros formatos de audio (MP3, OGG)
- [ ] Límite de duración de grabación
- [ ] Indicador de nivel de audio durante la grabación
