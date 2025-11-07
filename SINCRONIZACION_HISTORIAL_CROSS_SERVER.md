# Sincronización de Historial Cross-Server

## Resumen

Se ha implementado un sistema completo de sincronización de historial entre servidores que permite a los usuarios obtener automáticamente toda la información actualizada (mensajes directos, mensajes de canal, e información de canales) de todos los servidores P2P conectados cuando se autentican.

## Características Implementadas

### 1. Protocolo P2P de Sincronización

Se agregaron dos nuevos tipos de mensajes al protocolo P2P:

#### REQUEST_HISTORY
Solicita el historial de un usuario específico a un servidor remoto.

**Estructura:**
```json
{
  "tipo": "REQUEST_HISTORY",
  "serverHost": "192.168.1.100",
  "p2pPort": 6001,
  "idUsuario": 5
}
```

#### RESPONSE_HISTORY
Responde con el historial completo del usuario solicitado.

**Estructura:**
```json
{
  "tipo": "RESPONSE_HISTORY",
  "serverHost": "192.168.1.100",
  "p2pPort": 6001,
  "idUsuario": 5,
  "mensajes": [
    {
      "id": 123,
      "idEmisor": 5,
      "idReceptor": 10,
      "contenido": "Hola desde servidor remoto",
      "tipoMensaje": "TEXT",
      "fecha": "2025-11-07T01:30:00"
    },
    ...
  ],
  "canales": [
    {
      "id": 3,
      "nombre": "Canal Remoto",
      "idCreador": 5,
      "esPrivado": true,
      "fechaCreacion": "2025-11-06T10:00:00",
      "usuarios": [5, 10, 15]
    },
    ...
  ]
}
```

### 2. Nuevos Métodos en DAOs

#### MensajeLogDAO
- `obtenerMensajesDeUsuario(int idUsuario)`: Obtiene todos los mensajes donde el usuario es emisor o receptor
- `obtenerMensajesRecientesDeUsuario(int idUsuario, int dias)`: Obtiene mensajes de los últimos N días

#### CanalDAO
- Ya existente: `obtenerCanalesDeUsuario(int idUsuario)` - Obtiene canales donde el usuario es miembro
- Ya existente: `obtenerUsuariosCanal(int idCanal)` - Obtiene lista de usuarios de un canal

### 3. Servicios Actualizados

#### MensajeService
- `obtenerMensajesDeUsuario(int idUsuario)`
- `obtenerMensajesRecientesDeUsuario(int idUsuario, int dias)`

#### CanalService
- `obtenerUsuariosCanal(int idCanal)` - Alias para `obtenerMiembrosCanal`

### 4. Handler P2P en InterServerService

Se actualizó la interfaz `Handler` con dos nuevos métodos:

```java
public interface Handler {
    void onDirectMessageFromPeer(...);
    void onDirectAudioFromPeer(...);
    
    // NUEVOS:
    List<Map<String, Object>> onRequestUserHistory(int idUsuario);
    List<Map<String, Object>> onRequestUserChannels(int idUsuario);
}
```

**Implementación en ServidorTCPIntegrado:**
- `onRequestUserHistory()`: Consulta mensajes recientes (últimos 30 días) del usuario en el servidor local
- `onRequestUserChannels()`: Consulta canales del usuario con lista de miembros

### 5. Métodos de Solicitud en InterServerService

```java
// Solicitar historial a un servidor específico
public void requestUserHistory(String host, int port, int idUsuario)

// Solicitar historial a todos los servidores P2P conectados
public void requestUserHistoryFromAllPeers(int idUsuario)

// Obtener respuesta de un servidor específico
public Map<String, Object> getHistoryResponse(int idUsuario, String host, int port)

// Obtener todas las respuestas disponibles
public List<Map<String, Object>> getAllHistoryResponses(int idUsuario)

// Limpiar cache de respuestas
public void clearHistoryCache(int idUsuario)
```

### 6. Sincronización Automática al Conectar

Cuando un usuario se autentica exitosamente:

1. **Historial Local**: Se envían mensajes almacenados localmente:
   - Conversaciones directas con usuarios actualmente conectados
   - Mensajes de todos los canales a los que pertenece

2. **Historial Remoto (NUEVO)**: Si P2P está habilitado:
   - Se solicita historial a todos los servidores P2P conectados
   - Se espera 1 segundo para recibir respuestas (asíncrono)
   - Se procesan y envían al cliente:
     - Mensajes directos de otros servidores
     - Mensajes de canales remotos
     - Información de canales remotos (notificación CANAL_REMOTO)
   - Se limpia el cache de respuestas

## Flujo de Sincronización

```
Usuario se autentica en Servidor A
       ↓
ServidorTCPIntegrado.procesarAutenticacion()
       ↓
enviarHistorialAlUsuarioRecienConectado()
       ↓
┌──────────────────────────────────┐
│ 1. Enviar historial local        │
│    - Conversaciones directas     │
│    - Mensajes de canales         │
└──────────────────────────────────┘
       ↓
┌──────────────────────────────────┐
│ 2. Solicitar historial P2P       │
│    interServerService.            │
│    requestUserHistoryFromAllPeers│
└──────────────────────────────────┘
       ↓
[REQUEST_HISTORY] → Servidor B
[REQUEST_HISTORY] → Servidor C
       ↓
Servidor B procesa:
  - onRequestUserHistory()
  - onRequestUserChannels()
       ↓
[RESPONSE_HISTORY] ← Servidor B
[RESPONSE_HISTORY] ← Servidor C
       ↓
┌──────────────────────────────────┐
│ 3. Procesar respuestas           │
│    getAllHistoryResponses()      │
│    - Enviar mensajes remotos     │
│    - Enviar info de canales      │
└──────────────────────────────────┘
       ↓
Cliente recibe historial completo
de todos los servidores
```

## Ventanas de Sincronización

- **Mensajes**: Se sincronizan los últimos **30 días** de cada servidor remoto
- **Canales**: Se sincronizan todos los canales donde el usuario es miembro aceptado
- **Usuarios del canal**: Se incluye la lista completa de miembros por canal

## Tipos de Mensajes al Cliente

### Mensajes Existentes
- `TipoMensaje.MENSAJE_TEXTO`: Mensaje directo o de canal (texto)
- `TipoMensaje.MENSAJE_AUDIO`: Mensaje de audio con transcripción

### Nuevas Notificaciones
- `CANAL_REMOTO`: Información sobre un canal en servidor remoto
  ```json
  {
    "tipo": "CANAL_REMOTO",
    "id": 3,
    "nombre": "Canal en Servidor B",
    "idCreador": 10,
    "esPrivado": true
  }
  ```

## Consideraciones de Rendimiento

1. **Cache Temporal**: Las respuestas se almacenan en `historyCache` (ConcurrentHashMap)
2. **Timeout**: Se espera 1 segundo para recibir respuestas (configurable)
3. **Limpieza**: El cache se limpia automáticamente después de procesar
4. **Asincronía**: Las solicitudes P2P se ejecutan en pool de threads

## Limitaciones Actuales

1. **Audio Files**: Los archivos de audio NO se sincronizan entre servidores (solo metadatos y transcripciones)
2. **Ventana Temporal**: Solo últimos 30 días de mensajes (configurable en código)
3. **No Persistencia**: Los mensajes remotos no se guardan en la BD local (solo se envían al cliente)
4. **Timeout Fijo**: 1 segundo de espera puede ser insuficiente en redes lentas

## Mejoras Futuras Sugeridas

1. **Sincronización Incremental**: 
   - Guardar timestamp de última sincronización
   - Solo solicitar mensajes nuevos

2. **Persistencia Local**:
   - Opcionalmente guardar mensajes remotos en BD local
   - Marcar origen con servidor_host/servidor_puerto

3. **Sincronización de Archivos**:
   - Transferir archivos de audio entre servidores
   - Usar chunks o streaming para archivos grandes

4. **Timeout Configurable**:
   - Agregar parámetro en ServidorConfig
   - Ajustar según latencia de red

5. **Priorización**:
   - Sincronizar primero mensajes más recientes
   - Lazy loading para historial antiguo

## Pruebas Recomendadas

### Escenario 1: Usuario Nuevo
1. Iniciar Servidor A (puerto 5000, P2P 6000)
2. Iniciar Servidor B (puerto 5001, P2P 6001)
3. Conectar Usuario1 a Servidor A
4. Conectar Usuario2 a Servidor B
5. Usuario1 envía mensajes a Usuario2
6. Usuario2 se desconecta y reconecta
7. **Verificar**: Usuario2 recibe historial de Servidor A

### Escenario 2: Canales Cross-Server
1. Usuario1 (Servidor A) crea canal
2. Usuario2 (Servidor B) se une al canal
3. Se envían varios mensajes en el canal
4. Usuario2 se desconecta y reconecta
5. **Verificar**: Usuario2 recibe:
   - Historial de mensajes del canal
   - Información del canal remoto
   - Lista de miembros

### Escenario 3: Múltiples Servidores
1. Iniciar 3 servidores (A, B, C)
2. Usuario1 en A envía mensajes a Usuario2 en B
3. Usuario1 en A envía mensajes a Usuario3 en C
4. Usuario1 se desconecta y reconecta
5. **Verificar**: Usuario1 recibe historial de B y C

## Logs Relevantes

```
INFO  ServidorTCPIntegrado - Usuario autenticado: user1 (ID: 5)
INFO  ServidorTCPIntegrado - Enviando historial pendiente al usuario 5
INFO  ServidorTCPIntegrado - Solicitando historial cross-server para usuario 5
INFO  InterServerService - P2P TX REQUEST_HISTORY -> 192.168.1.101:6001 para usuario 5
INFO  InterServerService - P2P RX RESPONSE_HISTORY de 192.168.1.101:6001 - 15 mensajes, 2 canales
INFO  ServidorTCPIntegrado - Procesando 15 mensajes de historial remoto para usuario 5
INFO  ServidorTCPIntegrado - Procesando 2 canales de historial remoto para usuario 5
```

## Archivos Modificados

1. **InterServerService.java**
   - Nuevos tipos de mensaje REQUEST_HISTORY / RESPONSE_HISTORY
   - Handler interface extendida
   - Métodos de solicitud y cache de historial
   - Manejo de respuestas en handleIncoming()

2. **ServidorTCPIntegrado.java**
   - Implementación de onRequestUserHistory() y onRequestUserChannels()
   - Lógica de sincronización en enviarHistorialAlUsuarioRecienConectado()
   - Imports: Canal, ArrayList, HashMap

3. **MensajeLogDAO.java**
   - obtenerMensajesDeUsuario()
   - obtenerMensajesRecientesDeUsuario()

4. **MensajeService.java**
   - Wrappers para nuevos métodos DAO

5. **CanalService.java**
   - Alias obtenerUsuariosCanal()

## Conclusión

El sistema de sincronización de historial cross-server permite que los usuarios tengan una experiencia unificada independientemente del servidor al que se conecten. Toda la información relevante (mensajes directos, mensajes de canal, metadatos de canales) se sincroniza automáticamente al autenticarse, manteniendo la coherencia del sistema distribuido.
