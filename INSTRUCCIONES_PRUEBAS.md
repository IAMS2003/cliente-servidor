# Instrucciones para Probar las Notificaciones en Tiempo Real

## Estado Actual
✅ **Servidor corriendo** en puerto 8080
✅ **Cliente 1 iniciado** y listo para usar
✅ **Ambos proyectos compilados sin errores**

## Pruebas a Realizar

### Prueba 1: Conexión de Usuario
**Objetivo:** Verificar que cuando un usuario se conecta, los demás lo vean inmediatamente.

**Pasos:**
1. En el **Cliente 1** (ya está abierto):
   - Ingresa con un usuario existente (por ejemplo: `admin` / `admin123`)
   - Observa la lista "Conectados" en el panel izquierdo

2. **Abre un segundo cliente:**
   - Ejecuta en otra terminal: `cd "c:\Users\monto\universidad\Arqui\cliente-servidor\cliente"; mvn javafx:run`
   - Ingresa con otro usuario diferente

3. **Verifica en el Cliente 1:**
   - La lista "Conectados" debe actualizarse **INMEDIATAMENTE** sin necesidad de presionar "Refrescar"
   - Debe aparecer el segundo usuario que acaba de conectarse
   - ⚠️ El usuario no debe verse a sí mismo en su propia lista

### Prueba 2: Desconexión de Usuario
**Objetivo:** Verificar que cuando un usuario se desconecta, los demás lo vean inmediatamente.

**Pasos:**
1. Con ambos clientes conectados y viéndose mutuamente en sus listas
2. **Cierra el Cliente 2** (click en la X de la ventana)
3. **Verifica en el Cliente 1:**
   - La lista "Conectados" debe actualizarse **INMEDIATAMENTE**
   - El usuario del Cliente 2 debe desaparecer de la lista
   - No debe haber necesidad de presionar "Refrescar"

### Prueba 3: Múltiples Usuarios
**Objetivo:** Verificar que funciona con más de 2 usuarios.

**Pasos:**
1. Abre un **tercer cliente** (otra terminal con el mismo comando)
2. Conéctate con un tercer usuario
3. **Verifica en todos los clientes:**
   - Todos deben ver a todos los demás (excepto a sí mismos)
   - Las actualizaciones deben ser instantáneas al conectar/desconectar cualquier usuario

### Prueba 4: Verificar el Servidor
**Objetivo:** Comprobar que el servidor registra correctamente los eventos.

**Pasos:**
1. En la interfaz del **Servidor**, ve a la pestaña **"Informes"**
2. Verifica que se registran eventos de tipo:
   - `LOGIN` cuando un usuario se conecta
   - `LOGOUT` cuando un usuario se desconecta
3. Ve a la pestaña **"Usuarios"** del servidor
   - Solo deben aparecer los usuarios **actualmente conectados**
   - La lista se actualiza automáticamente

## Resultados Esperados

### ✅ Comportamiento Correcto:
- **Sin polling:** No hay actualizaciones cada 5 segundos
- **Actualizaciones instantáneas:** Los cambios se ven inmediatamente (< 1 segundo)
- **Sin auto-inclusión:** Cada usuario NO se ve a sí mismo en su lista
- **Logs del servidor:** Registra todos los LOGIN y LOGOUT
- **Sin necesidad de "Refrescar":** El botón ya no es necesario para ver cambios de conexión

### ❌ Problemas Posibles:
- Si la lista no se actualiza → Revisar logs del servidor en la terminal
- Si un usuario se ve a sí mismo → Revisar el filtrado en `ChatView.refrescarSoloUsuarios()`
- Si hay delay de 5 segundos → El polling no fue eliminado correctamente

## Verificación Técnica en el Código del Servidor

**Archivo:** `ServidorTCPIntegrado.java`

Al conectarse un usuario, el servidor debe ejecutar:
```java
// En procesarAutenticacion() después de login exitoso:
difundirATodos(TipoMensaje.NOTIFICACION, notificacion);
```

Al desconectarse:
```java
// En cerrarConexion() antes de cerrar:
difundirATodos(TipoMensaje.NOTIFICACION, notificacion);
```

## Cómo Abrir Clientes Adicionales

**Opción 1 - Nueva Terminal PowerShell:**
```powershell
cd "c:\Users\monto\universidad\Arqui\cliente-servidor\cliente"
mvn javafx:run
```

**Opción 2 - Desde VS Code:**
- Abre una nueva terminal (Ctrl + Shift + `)
- Ejecuta el comando anterior

## Usuarios de Prueba Disponibles

Si ya tienes usuarios registrados en el servidor, úsalos. Si no, el servidor puede registrar nuevos usuarios desde su interfaz (pestaña "Registro").

Ejemplos:
- Usuario: `admin` / Contraseña: `admin123`
- Usuario: `usuario1` / Contraseña: `password1`
- Usuario: `usuario2` / Contraseña: `password2`

---

## Resumen de la Funcionalidad Implementada

### Antes (Polling):
- ❌ Cliente consultaba lista de conectados cada 5 segundos
- ❌ Delay de hasta 5 segundos para ver cambios
- ❌ Tráfico de red innecesario
- ❌ Recursos del sistema desperdiciados

### Ahora (Push Notifications):
- ✅ Servidor notifica cambios en tiempo real
- ✅ Actualizaciones instantáneas (< 1 segundo)
- ✅ Menos tráfico de red
- ✅ Arquitectura event-driven eficiente
- ✅ Escalable para muchos usuarios

---

**Fecha de pruebas:** 30 de Octubre, 2025
**Estado:** ✅ Listo para pruebas
