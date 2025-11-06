# Prueba de Historial de Mensajes

## Objetivo
Validar que cuando un usuario selecciona un chat (con otro usuario o en un canal), se carga y muestra automáticamente todo el historial de mensajes previos.

## Implementación Realizada

### Servidor
**Archivo modificado**: `servidor/src/main/java/com/universidad/chat/servidor/service/ServidorTCPIntegrado.java`

**Método**: `procesarSolicitudLista(Mensaje mensaje)`

**Nuevos casos agregados**:
1. **`historial_usuario`**: Obtiene mensajes entre dos usuarios usando `mensajeService.obtenerHistorialUsuarios()`
2. **`historial_canal`**: Obtiene mensajes de un canal usando `mensajeService.obtenerMensajesCanal()`

**Formato de respuesta**:
```json
{
  "tipo": "historial_usuario" | "historial_canal",
  "mensajes": [
    {
      "id": 1,
      "idEmisor": 2,
      "idReceptor": 3,
      "idCanal": null,
      "contenido": "Hola",
      "tipoMensaje": "TEXT",
      "fecha": "2025-10-30T10:15:00",
      "archivoAudio": null,
      "transcripcion": null
    },
    ...
  ]
}
```

### Cliente - Servicio
**Archivo modificado**: `cliente/src/main/java/com/universidad/chat/cliente/service/ClienteProtocoloService.java`

**Nuevos métodos**:
- `solicitarHistorialUsuario(int idOtroUsuario)`: Solicita historial de conversación con un usuario
- `solicitarHistorialCanal(int idCanal)`: Solicita historial de mensajes de un canal

Ambos métodos:
- Envían una solicitud tipo `SOLICITUD_LISTA` con el tipo correspondiente
- Esperan respuesta con timeout de 5 segundos
- Retornan `CompletableFuture<List<JsonObject>>` con los mensajes

### Cliente - UI
**Archivo modificado**: `cliente/src/main/java/com/universidad/chat/cliente/ui/ChatView.java`

**Cambios en listeners**:
- **Selección de usuario**: Limpia el chat, muestra encabezado, y llama a `cargarHistorialUsuario()`
- **Selección de canal**: Limpia el chat, muestra encabezado, y llama a `cargarHistorialCanal()`

**Nuevos métodos privados**:
1. **`cargarHistorialUsuario(int idOtroUsuario)`**:
   - Ejecuta en hilo background
   - Solicita historial al servidor
   - Muestra mensajes en el chatArea

2. **`cargarHistorialCanal(int idCanal)`**:
   - Ejecuta en hilo background
   - Solicita historial al servidor
   - Muestra mensajes en el chatArea

3. **`mostrarMensajeHistorial(JsonObject msg)`**:
   - Formatea y muestra un mensaje del historial
   - Extrae timestamp (hora:minuto) de la fecha
   - Diferencia entre "Tú" y "Usuario #X" según el emisor
   - Soporta tipos: TEXT, AUDIO, y otros

**Formato de visualización**:
```
[10:15] Tú: Hola
[10:16] Usuario #2: ¿Cómo estás?
[10:17] Tú [Audio]: Transcripción del mensaje de voz
```

## Pasos de Prueba

### 1. Preparación - Crear datos de prueba

#### Terminal 1: Iniciar servidor
```powershell
.\iniciar-servidor.ps1
```

#### Terminal 2 y 3: Iniciar dos clientes
```powershell
.\prueba-cliente.ps1
```

### 2. Crear conversación entre usuarios

**En Cliente 1** (Usuario: andres):
1. Login con "andres" / "123"
2. Seleccionar "ivan" de la lista de usuarios
3. Enviar mensaje: "Hola Ivan, ¿cómo estás?"
4. Enviar mensaje: "¿Vamos a estudiar hoy?"

**En Cliente 2** (Usuario: ivan):
1. Login con "ivan" / "456"
2. Seleccionar "andres" de la lista de usuarios
3. Enviar mensaje: "Hola Andrés, todo bien"
4. Enviar mensaje: "Sí, a las 3pm"

### 3. Crear conversación en canal

**En Cliente 1** (andres):
1. Crear un canal: "Estudio"
2. Invitar a "ivan" al canal
3. Enviar en el canal: "Bienvenidos al canal de estudio"

**En Cliente 2** (ivan):
1. Aceptar invitación al canal "Estudio"
2. Seleccionar canal "Estudio"
3. Enviar: "Gracias por invitarme"

**En Cliente 1**:
4. Enviar: "Podemos compartir materiales aquí"

### 4. Probar carga de historial

#### Prueba A: Historial de usuario
**En Cliente 1** (andres):
1. Seleccionar otro usuario de la lista (diferente a ivan)
2. Volver a seleccionar "ivan"
3. **Verificar**: Debe aparecer todo el historial previo con timestamps

**Comportamiento esperado**:
```
-- Conversando con usuario #3 ivan --
[Historial de mensajes]
[10:30] Tú: Hola Ivan, ¿cómo estás?
[10:31] Usuario #3: Hola Andrés, todo bien
[10:32] Tú: ¿Vamos a estudiar hoy?
[10:33] Usuario #3: Sí, a las 3pm
[Fin del historial]
```

#### Prueba B: Historial de canal
**En Cliente 2** (ivan):
1. Cerrar sesión (o cerrar y reabrir el cliente)
2. Login nuevamente con "ivan" / "456"
3. Seleccionar el canal "Estudio"
4. **Verificar**: Debe aparecer todo el historial del canal

**Comportamiento esperado**:
```
-- Canal [1] Estudio --
[Historial de mensajes]
[10:35] Usuario #2: Bienvenidos al canal de estudio
[10:36] Tú: Gracias por invitarme
[10:37] Usuario #2: Podemos compartir materiales aquí
[Fin del historial]
```

#### Prueba C: Continuar conversación
1. Después de cargar el historial, enviar un nuevo mensaje
2. **Verificar**: El mensaje se envía y aparece normalmente
3. Deseleccionar y volver a seleccionar el chat
4. **Verificar**: Aparece el historial completo incluyendo el nuevo mensaje

### 5. Casos límite a probar

| Caso | Acción | Resultado Esperado |
|------|--------|-------------------|
| Chat sin mensajes | Seleccionar usuario con quien no has hablado | Solo aparece el encabezado, sin "[Historial de mensajes]" |
| Cambio rápido | Seleccionar usuario A → inmediatamente seleccionar usuario B | Solo aparece historial de B (no se mezclan) |
| Mensaje de audio | Enviar audio con transcripción → recargar historial | Aparece como "[Audio]: [transcripción]" |
| Canal privado | Crear canal privado, enviar mensajes, recargar | Historial se carga correctamente |
| Usuario desconectado | Enviar mensajes a usuario offline → él se conecta y abre el chat | Ve todo el historial de mensajes |

## Flujo Técnico

### Selección de Usuario
```
┌─────────────┐                                    ┌─────────────┐
│   Cliente   │                                    │  Servidor   │
│     UI      │                                    │             │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │ 1. Usuario selecciona "ivan" en lista           │
       │                                                  │
       │ 2. listener.onSelectedItem(ivan)                │
       │    - chatArea.clear()                           │
       │    - destinoActual = USUARIO                    │
       │    - idDestino = 3                              │
       │                                                  │
       │ 3. cargarHistorialUsuario(3)                    │
       │    (en hilo background)                         │
       │                                                  │
       │ 4. SOLICITUD_LISTA                              │
       │    {"tipo":"historial_usuario","idUsuario":3}   │
       │ ─────────────────────────────────────────────> │
       │                                                  │
       │                                                  │ 5. procesarSolicitudLista()
       │                                                  │    - mensajeService
       │                                                  │      .obtenerHistorialUsuarios(
       │                                                  │        idUsuarioActual:2, 
       │                                                  │        idOtroUsuario:3)
       │                                                  │
       │                                                  │ 6. Query SQL:
       │                                                  │    SELECT * FROM mensajes_log
       │                                                  │    WHERE (id_emisor=2 AND id_receptor=3)
       │                                                  │       OR (id_emisor=3 AND id_receptor=2)
       │                                                  │    ORDER BY fecha
       │                                                  │
       │ 7. NOTIFICACION                                 │
       │ {"tipo":"historial_usuario",                    │
       │  "mensajes":[...]}                              │
       │ <──────────────────────────────────────────── │
       │                                                  │
       │ 8. Platform.runLater()                          │
       │    - chatArea.appendText("[Historial...]")      │
       │    - for each mensaje:                          │
       │        mostrarMensajeHistorial(msg)             │
       │                                                  │
       ▼                                                  ▼
  (Historial                                        (Esperando
   mostrado)                                         siguiente
                                                      solicitud)
```

### Selección de Canal
```
Similar al flujo de usuario, pero:
- Solicitud: {"tipo":"historial_canal","idCanal":1}
- Servidor: mensajeService.obtenerMensajesCanal(idCanal)
- Query SQL: SELECT * FROM mensajes_log WHERE id_canal = 1 ORDER BY fecha
```

## Notas Técnicas

1. **Persistencia**: Los mensajes se obtienen de la tabla `mensajes_log` en MySQL del servidor, que ya almacena todos los mensajes enviados.

2. **Orden cronológico**: Los mensajes se ordenan por fecha ascendente (más antiguos primero).

3. **Performance**: 
   - La carga se hace en hilo background para no bloquear la UI
   - Timeout de 5 segundos para la solicitud
   - El servidor usa consultas SQL optimizadas con índices

4. **Tipos de mensaje soportados**:
   - `TEXT` / `TEXTO`: Mensaje de texto normal
   - `AUDIO`: Muestra transcripción si está disponible
   - Otros tipos: Se muestran con prefijo del tipo

5. **Formato de timestamp**: Se extrae HH:mm del campo fecha en formato ISO (ej: "2025-10-30T14:25:00")

6. **Identificación del emisor**: 
   - Si `idEmisor == idUsuarioActual` → "Tú"
   - Si no → "Usuario #X"

## Estado de Compilación

✅ **Servidor**: Compilado exitosamente  
✅ **Cliente**: Compilado exitosamente

Ambos módulos están listos para ejecutar y probar la funcionalidad de historial.

## Mejoras Futuras Opcionales

1. **Caché local**: Guardar historial en H2 del cliente para acceso offline
2. **Paginación**: Si hay muchos mensajes (>100), cargar por páginas
3. **Scroll automático**: Hacer scroll al último mensaje después de cargar historial
4. **Indicador visual**: Mostrar línea separadora entre historial y mensajes nuevos
5. **Búsqueda**: Permitir buscar en el historial de mensajes
6. **Exportar**: Opción de exportar historial a archivo de texto
