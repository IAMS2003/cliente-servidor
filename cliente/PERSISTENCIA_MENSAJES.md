# Sistema de Persistencia de Mensajes - Cliente

## Descripción

El cliente ahora persiste **todos los mensajes** (texto y audio) en la base de datos local H2. Los mensajes se guardan automáticamente cuando se envían o reciben, y el historial se carga desde la base de datos local en lugar de solicitarlo al servidor.

## Características Implementadas

### ✅ Persistencia Automática

1. **Mensajes de Texto Entrantes**: Se guardan automáticamente cuando se reciben de otros usuarios o canales
2. **Mensajes de Audio Entrantes**: Se guardan con la transcripción y el audio completo en formato BLOB
3. **Mensajes de Texto Salientes**: Se guardan antes de enviarlos al servidor
4. **Mensajes de Audio Salientes**: Se guardan con el audio completo antes de enviarlos al servidor

### 📦 Almacenamiento de Audio

- El audio se almacena en formato **BLOB** en la base de datos
- Conversión automática de Base64 ↔ bytes[]
- Permite reproducción offline desde la base de datos local
- Incluye transcripción automática (realizada por el servidor)

### 🗄️ Esquema de Base de Datos

**Tabla: mensaje_log**
```sql
CREATE TABLE IF NOT EXISTS mensaje_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_emisor INT NOT NULL,
    id_receptor INT,
    id_canal INT,
    contenido TEXT,
    tipo_mensaje VARCHAR(20),
    audio_data BLOB,
    transcripcion TEXT,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

## Componentes Modificados

### 1. `MensajePersistenciaService` (NUEVO)
**Ubicación**: `cliente/src/main/java/com/universidad/chat/cliente/service/MensajePersistenciaService.java`

Métodos principales:
- `guardarMensajeTexto(idEmisor, idReceptor, idCanal, contenido)`: Guarda mensajes de texto
- `guardarMensajeAudio(idEmisor, idReceptor, idCanal, transcripcion, audioBase64)`: Guarda mensajes de audio con conversión Base64→bytes
- `cargarMensajesUsuario(idUsuario1, idUsuario2)`: Carga historial de chat directo
- `cargarMensajesCanal(idCanal)`: Carga historial de canal

### 2. `ChatView.java`
**Ubicación**: `cliente/src/main/java/com/universidad/chat/cliente/ui/ChatView.java`

**Cambios implementados**:

#### Inicialización del servicio de persistencia:
```java
private final MensajePersistenciaService persistencia;

public ChatView(ClienteProtocoloService svc) {
    this.svc = svc;
    this.persistencia = new MensajePersistenciaService(svc.getIdUsuario());
    // ...
}
```

#### Persistencia en callbacks de mensajes entrantes:
```java
// onMensajeUsuario
persistencia.guardarMensajeTexto(idEmisor, miId, null, contenido);

// onMensajeCanal
persistencia.guardarMensajeTexto(idEmisor, null, idCanal, contenido);

// onAudioUsuario
persistencia.guardarMensajeAudio(idEmisor, miId, null, transcripcion, audioData);

// onAudioCanal
persistencia.guardarMensajeAudio(idEmisor, null, idCanal, transcripcion, audioData);
```

#### Persistencia en envío de mensajes:
```java
// enviarMensaje() - texto saliente
int userId = svc.getIdUsuario();
if (destinoActual == Destino.CANAL) {
    persistencia.guardarMensajeTexto(userId, null, idDestino, texto);
} else {
    persistencia.guardarMensajeTexto(userId, idDestino, null, texto);
}

// toggleGrabacionAudio() - audio saliente
String audioBase64 = Base64.getEncoder().encodeToString(audioData);
if (destinoActual == Destino.CANAL) {
    persistencia.guardarMensajeAudio(userId, null, idDestino, "", audioBase64);
} else {
    persistencia.guardarMensajeAudio(userId, idDestino, null, "", audioBase64);
}
```

#### Carga de historial desde BD:
```java
// cargarHistorialUsuario()
List<MensajeLog> mensajes = persistencia.cargarMensajesUsuario(miId, idUsuarioSeleccionado);
for (MensajeLog msg : mensajes) {
    mostrarMensajeDesdeBD(msg, miId);
}

// cargarHistorialCanal()
List<MensajeLog> mensajes = persistencia.cargarMensajesCanal(idCanalSeleccionado);
for (MensajeLog msg : mensajes) {
    mostrarMensajeDesdeBD(msg, miId);
}
```

### 3. `MensajeLogDAO.java`
**Ubicación**: `cliente/src/main/java/com/universidad/chat/cliente/dao/MensajeLogDAO.java`

**Mejoras**:
- Método `crear()` ahora retorna el ID del mensaje insertado
- Soporte completo para campos `audio_data` (BLOB) y `transcripcion`
- Métodos `obtenerEntreUsuarios()` y `listarPorCanal()` actualizados
- Método `mapearMensaje()` para conversión ResultSet → MensajeLog

### 4. `MensajeLog.java`
**Ubicación**: `cliente/src/main/java/com/universidad/chat/cliente/model/MensajeLog.java`

**Campos**:
```java
private int id;
private int idEmisor;
private Integer idReceptor;  // nullable para mensajes de canal
private Integer idCanal;     // nullable para mensajes directos
private String contenido;
private String tipoMensaje;  // "TEXTO" o "AUDIO"
private byte[] audioData;    // BLOB
private String transcripcion;
private Timestamp fecha;
```

### 5. `ConexionBD.java`
**Ubicación**: `cliente/src/main/java/com/universidad/chat/cliente/config/ConexionBD.java`

**Mejoras**:
- Creación automática del directorio `data/` para la BD
- Carga explícita del driver H2 (`Class.forName("org.h2.Driver")`)
- Esquema actualizado con campos de audio y transcripción

## Ubicación de la Base de Datos

**Ruta**: `c:\Users\monto\universidad\Arqui\cliente-servidor\cliente\data\chat_cliente.mv.db`

La base de datos es **persistente** y se mantiene entre reinicios de la aplicación.

## Flujo de Datos

### Mensaje de Texto Saliente:
1. Usuario escribe mensaje y presiona "Enviar"
2. `enviarMensaje()` muestra echo local inmediato
3. **Guarda en BD local** usando `persistencia.guardarMensajeTexto()`
4. Envía al servidor vía TCP
5. Servidor lo distribuye a destinatarios

### Mensaje de Audio Saliente:
1. Usuario graba audio con el botón 🎤
2. `toggleGrabacionAudio()` detiene grabación
3. Muestra echo local con botón de reproducción
4. **Guarda en BD local** (audio completo en BLOB) usando `persistencia.guardarMensajeAudio()`
5. Envía al servidor vía TCP
6. Servidor lo transcribe con VOSK
7. Servidor lo distribuye a destinatarios con transcripción

### Mensaje Entrante (texto o audio):
1. Servidor envía mensaje via TCP
2. Cliente recibe en callback (`onMensajeUsuario`, `onAudioCanal`, etc.)
3. **Guarda en BD local** inmediatamente
4. Muestra en la interfaz

### Carga de Historial:
1. Usuario selecciona un usuario o canal
2. `cargarHistorialUsuario()` o `cargarHistorialCanal()`
3. **Lee desde BD local** (no desde servidor)
4. Muestra mensajes usando `mostrarMensajeDesdeBD()`

## Ventajas del Sistema

✅ **Acceso offline**: Los mensajes están disponibles sin conexión al servidor  
✅ **Rendimiento**: Carga instantánea del historial desde la BD local  
✅ **Reducción de carga del servidor**: No se solicita historial en cada apertura de chat  
✅ **Audio completo**: Se almacena el audio original para reproducción offline  
✅ **Transcripciones**: Disponibles localmente para búsqueda y lectura  

## Pruebas Recomendadas

1. **Test de persistencia de texto**:
   - Enviar mensaje a usuario/canal
   - Cerrar y reabrir cliente
   - Verificar que el mensaje aparece en el historial

2. **Test de persistencia de audio**:
   - Grabar y enviar audio
   - Cerrar y reabrir cliente
   - Verificar que el audio se puede reproducir desde el historial

3. **Test de mensajes entrantes**:
   - Recibir mensaje de otro usuario
   - Verificar que se guarda automáticamente
   - Reabrir chat y verificar que aparece

4. **Verificar BD manualmente**:
   ```powershell
   # Usar H2 Console para inspeccionar
   java -cp "~/.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar" org.h2.tools.Shell
   # URL: jdbc:h2:./data/chat_cliente
   # Usuario: sa
   # Password: (vacío)
   
   # Consultas útiles:
   SELECT * FROM mensaje_log ORDER BY fecha DESC LIMIT 10;
   SELECT COUNT(*) FROM mensaje_log WHERE tipo_mensaje = 'AUDIO';
   SELECT id_emisor, COUNT(*) FROM mensaje_log GROUP BY id_emisor;
   ```

## Notas Técnicas

- La transcripción se realiza en el **servidor** usando VOSK (modelo español)
- El cliente almacena la transcripción recibida del servidor
- Los audios salientes se guardan **sin transcripción** (string vacío) porque aún no han sido procesados
- Los audios entrantes **incluyen la transcripción** realizada por el servidor
- El campo `audio_data` usa `setBytes()` y `getBytes()` de JDBC para manejar BLOBs

## Estado de Compilación

✅ **Cliente compilado exitosamente** con todas las funciones de persistencia  
✅ **JAR generado**: `cliente/target/chat-cliente-1.0-SNAPSHOT.jar`  
✅ **Sin errores de compilación**  

## Próximos Pasos

1. Ejecutar pruebas de integración
2. Verificar que los audios se reproduzcan correctamente desde la BD
3. Instalar modelo VOSK en el servidor para transcripciones (ver `servidor/vosk-models/README.md`)
4. Opcional: Añadir función de búsqueda en mensajes guardados
5. Opcional: Implementar paginación para conversaciones muy largas
