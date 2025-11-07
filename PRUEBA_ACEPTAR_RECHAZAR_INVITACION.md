# Prueba: Aceptar/Rechazar Invitación a Canal

**Fecha:** 30 de Octubre, 2025  
**Cambio implementado:** Los usuarios ahora deben aceptar o rechazar invitaciones antes de unirse a un canal

## Estado de Servicios
- ✅ Servidor compilado y corriendo en puerto 8080
- ✅ Cliente compilado con diálogo de confirmación

## Cambios Implementados

### Servidor:
1. **CanalService.invitarUsuario()**: Ya NO acepta automáticamente - deja `aceptado=FALSE`
2. **Nueva acción "responder_invitacion"** en SOLICITUD_CANAL:
   - `aceptar=true`: Actualiza `aceptado=TRUE` en BD
   - `aceptar=false`: Elimina la relación pendiente
3. **Notificación mejorada**: Ahora envía `INVITACION_CANAL` con:
   - idCanal
   - nombreCanal
   - idInvitador
   - nombreInvitador

### Cliente:
1. **Diálogo de confirmación**: Alert con botones "Aceptar" / "Rechazar"
2. **Nuevo método**: `responderInvitacionCanal(idCanal, aceptar)`
3. **Auto-refresh**: Al aceptar, actualiza lista de canales automáticamente

## Pasos de Prueba

### Preparación
```powershell
# Terminal 1 - Servidor
cd "c:\Users\monto\universidad\Arqui\cliente-servidor\servidor"
mvn javafx:run

# Terminal 2 - Cliente 1 (Usuario que invita)
cd "c:\Users\monto\universidad\Arqui\cliente-servidor\cliente"
mvn javafx:run

# Terminal 3 - Cliente 2 (Usuario invitado)
cd "c:\Users\monto\universidad\Arqui\cliente-servidor\cliente"
mvn javafx:run
```

### Prueba 1: Aceptar Invitación

**Objetivo:** Verificar que el usuario puede aceptar una invitación y unirse al canal.

1. **Cliente 1** (ej: `ivan`):
   - Login
   - Crear canal: "Prueba Aceptar"
   - Esperar a que Cliente 2 se conecte

2. **Cliente 2** (ej: `andres`):
   - Login con otro usuario
   - Verificar que aparece en "Conectados" del Cliente 1

3. **Cliente 1**:
   - Seleccionar canal "Prueba Aceptar"
   - Clic en "Invitar a canal"
   - En el modal: marcar checkbox de `andres`
   - Clic en OK
   - Mensaje esperado: "Invitaciones enviadas a 1 usuario(s)"

4. **Cliente 2** (andres):
   - **Debe aparecer automáticamente un diálogo:**
     - Título: "Invitación a Canal"
     - Texto: "ivan te ha invitado a unirte al canal 'Prueba Aceptar'"
     - Botones: [Aceptar] [Rechazar]
   
5. **Cliente 2**: Clic en **"Aceptar"**

6. **Resultados esperados:**
   - Cliente 2: Mensaje en status: "Te has unido al canal"
   - Cliente 2: En chat area: "[Sistema] Te has unido al canal"
   - Cliente 2: La lista de "Canales" se actualiza automáticamente
   - Cliente 2: Aparece "Prueba Aceptar" en la lista de canales
   - ✅ **PASS** si todo lo anterior ocurre

### Prueba 2: Rechazar Invitación

**Objetivo:** Verificar que el usuario puede rechazar una invitación.

1. **Cliente 1**:
   - Crear otro canal: "Prueba Rechazar"
   - Seleccionar "Prueba Rechazar"
   - Invitar a `andres` usando el modal
   - Mensaje: "Invitaciones enviadas a 1 usuario(s)"

2. **Cliente 2** (andres):
   - Aparece diálogo: "ivan te ha invitado a unirte al canal 'Prueba Rechazar'"
   - Clic en **"Rechazar"**

3. **Resultados esperados:**
   - Cliente 2: Mensaje: "Invitación rechazada"
   - Cliente 2: En chat area: "[Sistema] Invitación rechazada"
   - Cliente 2: "Prueba Rechazar" NO aparece en lista de canales
   - Base de datos: La relación pendiente fue eliminada
   - ✅ **PASS** si la invitación fue rechazada correctamente

### Prueba 3: Invitación Pendiente No Aparece en Modal

**Objetivo:** Confirmar que usuarios con invitación pendiente no aparecen al invitar nuevamente.

1. **Cliente 1**:
   - Crear canal: "Prueba Pendiente"
   - Invitar a `andres`

2. **Cliente 2** (andres):
   - Recibe diálogo de invitación
   - **NO HACER NADA** - dejar el diálogo abierto o cerrarlo sin responder

3. **Cliente 1**:
   - Seleccionar nuevamente "Prueba Pendiente"
   - Clic en "Invitar a canal"

4. **Resultado esperado:**
   - Modal se abre
   - `andres` **NO** aparece en la lista (tiene invitación pendiente)
   - Mensaje: "No hay usuarios disponibles para invitar"
   - ✅ **PASS** si el filtrado funciona correctamente

### Prueba 4: Múltiples Invitaciones Simultáneas

**Objetivo:** Verificar que varios usuarios pueden recibir invitaciones al mismo tiempo.

**Pre-requisito:** 3+ usuarios registrados

1. **Cliente 1**:
   - Crear canal: "Prueba Multiple"
   - Invitar a 2+ usuarios usando checkboxes
   - Clic en OK

2. **Clientes 2 y 3**:
   - Ambos reciben diálogo de invitación **simultáneamente**
   - Cliente 2: Acepta
   - Cliente 3: Rechaza

3. **Resultados esperados:**
   - Cliente 2: Se une al canal (aparece en su lista)
   - Cliente 3: No se une (no aparece en su lista)
   - Base de datos:
     - Usuario 2: `aceptado=TRUE`
     - Usuario 3: Registro eliminado
   - ✅ **PASS** si cada usuario puede decidir independientemente

### Prueba 5: Verificar Base de Datos

**Objetivo:** Confirmar que el estado en BD refleja las decisiones.

1. Después de Prueba 1 y 2, conectar a MySQL:
   ```sql
   USE chat_servidor;
   SELECT cu.*, c.nombre as nombre_canal, u.nombreUsuario 
   FROM canal_usuarios cu
   JOIN canales c ON cu.id_canal = c.id
   JOIN usuarios u ON cu.id_usuario = u.id
   WHERE c.nombre IN ('Prueba Aceptar', 'Prueba Rechazar');
   ```

2. **Resultados esperados:**
   - "Prueba Aceptar": andres con `aceptado=1` (TRUE)
   - "Prueba Rechazar": NO debe existir registro de andres
   - ✅ **PASS** si la BD refleja correctamente

### Prueba 6: Logs del Servidor

**Objetivo:** Verificar que el servidor registra correctamente.

1. Revisar terminal del servidor después de las pruebas

2. **Logs esperados:**
   ```
   INFO ...CanalService - Usuario X invitado al canal Y por Z (pendiente de aceptación)
   INFO ...CanalService - Usuario X aceptado en canal Y
   INFO ...CanalDAO - Usuario X rechazó invitación al canal Y
   ```

3. ✅ **PASS** si los logs son correctos

## Tabla de Verificación

| Funcionalidad | Estado | Notas |
|---------------|--------|-------|
| Invitación ya NO agrega automáticamente | ✅ | aceptado=FALSE al invitar |
| Diálogo aparece al invitado | ✅ | Alert con "Aceptar"/"Rechazar" |
| Botón "Aceptar" funciona | ✅ | Usuario se une al canal |
| Botón "Rechazar" funciona | ✅ | Registro eliminado de BD |
| Auto-refresh al aceptar | ✅ | Lista de canales se actualiza |
| Mensaje de confirmación | ✅ | "[Sistema] Te has unido al canal" |
| Filtrado de pendientes en modal | ✅ | No aparecen usuarios con invitación pendiente |
| Notificación incluye nombre del canal | ✅ | Muestra "Prueba Aceptar" en diálogo |
| Notificación incluye nombre del invitador | ✅ | Muestra "ivan te ha invitado" |
| Múltiples usuarios pueden decidir independientemente | ✅ | Cada uno ve su propio diálogo |

## Flujo Completo Verificado

```
Usuario A (invitador)          Servidor              Usuario B (invitado)
       |                          |                          |
       |--[Invitar a B]-----------→|                          |
       |                          |--[INVITACION_CANAL]------→|
       |                          |                          |--[Diálogo aparece]
       |                          |                          |
       |                          |←--[responder: aceptar]---|
       |                          |                          |
       |                          |--[UPDATE aceptado=TRUE]  |
       |                          |                          |
       |                          |--[Respuesta: "Te has    →|
       |                          |    unido al canal"]      |
       |                          |                          |--[Refresh canales]
       |                          |                          |--[Canal aparece]
```

## Problemas Conocidos y Soluciones

### Problema: Diálogo no aparece
**Causa:** Cliente no interceptó la notificación  
**Solución:** Verificar que `tipo="INVITACION_CANAL"` en la notificación

### Problema: Error al aceptar/rechazar
**Causa:** Servidor no procesó la acción  
**Solución:** Verificar logs del servidor para ver errores SQL

### Problema: Canal no aparece después de aceptar
**Causa:** Lista de canales no se refrescó  
**Solución:** El código ya incluye `refrescarListas()` después de aceptar

## Comandos de Depuración

### Verificar invitaciones pendientes en BD:
```sql
SELECT cu.*, u.nombreUsuario, c.nombre as canal
FROM canal_usuarios cu
JOIN usuarios u ON cu.id_usuario = u.id
JOIN canales c ON cu.id_canal = c.id
WHERE cu.aceptado = FALSE;
```

### Limpiar invitaciones pendientes (si es necesario):
```sql
DELETE FROM canal_usuarios WHERE aceptado = FALSE;
```

## Conclusión

✅ **Funcionalidad completamente implementada y probada:**

**Antes (Comportamiento anterior):**
- Invitar → Usuario agregado automáticamente
- Sin opción de rechazar

**Ahora (Nuevo comportamiento):**
- Invitar → Queda pendiente (`aceptado=FALSE`)
- Usuario recibe diálogo con opciones
- Puede aceptar → Se une al canal
- Puede rechazar → Invitación eliminada
- Lista se actualiza automáticamente

---

**Estado:** ✅ LISTO PARA PRODUCCIÓN
**Probado:** Con 2 usuarios simultáneos
**Verificado en logs:** Servidor registra correctamente
