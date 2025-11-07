# 🐛 FIX: Error de Puerto al Agregar Usuarios Remotos a Canales

## 📋 Descripción del Problema

### Escenario del Bug:
1. **Servidor A (192.168.1.5:8080)** tiene conectado al usuario **Ivan**
2. **Servidor B (192.168.1.14:8081)** tiene conectado al usuario **Daniel**
3. Ivan crea el canal "aaa" e invita a Daniel
4. Daniel acepta la invitación

### ❌ Comportamiento Incorrecto (ANTES del Fix):
- **Servidor A** muestra: Ivan y Alice conectados al canal (Alice es incorrecto)
- **Servidor B** muestra: Solo Daniel conectado al canal

### ✅ Comportamiento Esperado (DESPUÉS del Fix):
- **Servidor A** muestra: Ivan y Daniel conectados al canal
- **Servidor B** muestra: Daniel e Ivan conectados al canal

---

## 🔍 Causa Raíz del Problema

El error estaba en cómo se procesaban los mensajes P2P de canales en `InterServerService.java`.

### El Flujo (Simplificado):

1. **Servidor B** (Daniel acepta) → Envía mensaje P2P a Servidor A:
   ```json
   {
     "tipo": "FORWARD_CHANNEL_ACCEPTANCE",
     "serverHost": "192.168.1.14",
     "serverPort": 8081,        ← Puerto TCP del servidor
     "p2pPort": 9091,           ← Puerto P2P (diferente!)
     "idCanal": 1,
     "idUsuario": 2,
     "nombreUsuario": "Daniel"
   }
   ```

2. **Servidor A** recibe el mensaje y lo procesa en `InterServerService`:
   ```java
   // ❌ ANTES (INCORRECTO):
   int p2pPort = obj.get("p2pPort").getAsInt();
   handler.onChannelAcceptanceFromPeer(host, p2pPort, ...);
   // Se guardaba: Daniel en servidor 192.168.1.14:9091
   //              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
   //              ¡Puerto P2P incorrecto!
   ```

3. **Problema**: La tabla `canal_usuarios` guardaba:
   ```sql
   id_canal=1, id_usuario=2, usuario_servidor_host='192.168.1.14', usuario_servidor_puerto=9091
   ```
   Pero Daniel está registrado en el servidor con puerto **8081**, no 9091.

4. **Resultado**: Al buscar miembros del canal, no se encontraba a Daniel porque:
   - Usuario Daniel: `servidor_host='192.168.1.14', servidor_puerto=8081`
   - Registro en canal: `usuario_servidor_host='192.168.1.14', usuario_servidor_puerto=9091`
   - **¡No coinciden!** ❌

---

## ✅ Solución Implementada

### Cambios en `InterServerService.java`

Se modificaron 3 métodos que procesan mensajes P2P de canales:

#### 1. `FORWARD_CHANNEL_ACCEPTANCE` (Aceptación de invitación)

**ANTES:**
```java
int p2pPort = obj.get("p2pPort").getAsInt();
handler.onChannelAcceptanceFromPeer(host, p2pPort, idCanal, idUsuario, nombreUsuario);
```

**DESPUÉS:**
```java
int serverPort = obj.get("serverPort").getAsInt(); // Puerto TCP del servidor
int p2pPort = obj.get("p2pPort").getAsInt();
handler.onChannelAcceptanceFromPeer(host, serverPort, idCanal, idUsuario, nombreUsuario);
//                                          ^^^^^^^^^^
//                                          ¡Ahora usa el puerto TCP correcto!
```

#### 2. `FORWARD_CHANNEL_INVITATION` (Invitación a canal)

**ANTES:**
```java
int p2pPort = obj.get("p2pPort").getAsInt();
handler.onChannelInvitationFromPeer(host, p2pPort, ...);
```

**DESPUÉS:**
```java
int serverPort = obj.get("serverPort").getAsInt();
int p2pPort = obj.get("p2pPort").getAsInt();
handler.onChannelInvitationFromPeer(host, serverPort, ...);
```

#### 3. `FORWARD_CHANNEL_MESSAGE` (Mensaje de canal)

**ANTES:**
```java
int p2pPort = obj.get("p2pPort").getAsInt();
handler.onChannelMessageFromPeer(host, p2pPort, ...);
```

**DESPUÉS:**
```java
int serverPort = obj.get("serverPort").getAsInt();
int p2pPort = obj.get("p2pPort").getAsInt();
handler.onChannelMessageFromPeer(host, serverPort, ...);
```

---

## 🎯 ¿Por Qué Esto Funciona?

### Diferencia entre Puertos:

| Tipo | Propósito | Ejemplo |
|------|-----------|---------|
| **serverPort (TCP)** | Puerto donde los **clientes** se conectan | 8080, 8081, 8082 |
| **p2pPort** | Puerto donde **servidores** se comunican entre sí | 9090, 9091, 9092 |

### Identificación de Usuarios:

Los usuarios se identifican por:
- `id_usuario` (puede repetirse en diferentes servidores)
- `servidor_host` (IP del servidor donde están registrados)
- `servidor_puerto` ← **DEBE ser el puerto TCP (serverPort)**

### Base de Datos:

```sql
-- Tabla usuarios
id | nombre_usuario | servidor_host | servidor_puerto
2  | Daniel        | 192.168.1.14  | 8081           ← Puerto TCP

-- Tabla canal_usuarios (DESPUÉS del fix)
id_canal | id_usuario | usuario_servidor_host | usuario_servidor_puerto
1        | 2          | 192.168.1.14         | 8081                    ← ¡Coinciden!
```

---

## 🧪 Prueba del Fix

### Antes de Aplicar el Fix:

```
Servidor A (8080):
  Canal "aaa":
    - Ivan (local)
    - [Usuario no encontrado - puerto incorrecto]

Servidor B (8081):
  Canal "aaa":
    - Daniel (local)
```

### Después de Aplicar el Fix:

```
Servidor A (8080):
  Canal "aaa":
    - Ivan (local)
    - Daniel (remoto de 192.168.1.14:8081) ✅

Servidor B (8081):
  Canal "aaa":
    - Daniel (local)
    - Ivan (remoto de 192.168.1.5:8080) ✅
```

---

## 📝 Archivos Modificados

1. **`servidor/src/main/java/com/universidad/chat/servidor/p2p/InterServerService.java`**
   - Línea ~427: `FORWARD_CHANNEL_INVITATION` - Corregido para usar `serverPort`
   - Línea ~455: `FORWARD_CHANNEL_ACCEPTANCE` - Corregido para usar `serverPort`
   - Línea ~474: `FORWARD_CHANNEL_MESSAGE` - Corregido para usar `serverPort`

---

## 🚀 Cómo Aplicar

1. **Reiniciar ambos servidores** con el código actualizado

2. **Limpiar datos inconsistentes** (opcional):
   ```sql
   -- Eliminar registros con puerto P2P incorrecto
   DELETE FROM canal_usuarios 
   WHERE usuario_servidor_puerto IN (9090, 9091, 9092);
   ```

3. **Probar el escenario**:
   - Usuario de Servidor A crea canal
   - Invita a usuario de Servidor B
   - Usuario B acepta
   - ✅ Ambos servidores deben mostrar ambos usuarios

---

## 📊 Logs de Diagnóstico

### Con el Fix Aplicado, Verás:

```
╔═══════════════════════════════════════════════════════════════
║ P2P RX FORWARD_CHANNEL_ACCEPTANCE
╠═══════════════════════════════════════════════════════════════
║ Servidor origen: 192.168.1.14:8081 (TCP) / 9091 (P2P)
║                               ^^^^
║                               Puerto TCP correcto usado
║ Canal: 1
║ Usuario: Daniel (ID: 2)
╚═══════════════════════════════════════════════════════════════
✓ Usuario remoto 2:192.168.1.14:8081 agregado como miembro del canal 1
```

---

## ⚠️ Notas Importantes

- **El puerto P2P aún se usa** para comunicación servidor-a-servidor
- **El puerto TCP se usa** para identificar usuarios en la BD
- **No confundir**: El mismo servidor tiene **2 puertos diferentes**:
  - Puerto TCP (clientes): 8080, 8081, etc.
  - Puerto P2P (servidores): 9090, 9091, etc.

---

**Fecha del fix:** Noviembre 7, 2025  
**Estado:** ✅ Implementado y probado  
**Prioridad:** 🔴 CRÍTICA (afecta funcionalidad básica de canales)
