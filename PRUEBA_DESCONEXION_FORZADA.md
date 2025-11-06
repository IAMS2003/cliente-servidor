# Prueba de Desconexión Forzada por el Servidor

## Objetivo
Validar que cuando el servidor desconecta forzosamente a un usuario desde el dashboard, el cliente recibe la notificación "El servidor te ha desconectado" y se cierra correctamente.

## Implementación Realizada

### Servidor
- **Archivo modificado**: `servidor/src/main/java/com/universidad/chat/servidor/service/ServidorTCPIntegrado.java`
- **Método**: `forzarDesconexionUsuario(int idUsuario)`
- **Cambios**:
  - Antes de cerrar la conexión, envía una notificación especial al cliente
  - El mensaje JSON incluye: `{"desconexion_forzada": true, "contenido": "El servidor te ha desconectado"}`
  - Espera 200ms para asegurar que el mensaje llegue antes de cerrar el socket

### Cliente
- **Archivo modificado**: `cliente/src/main/java/com/universidad/chat/cliente/service/ClienteProtocoloService.java`
- **Cambios**:
  - Detecta notificaciones con flag `desconexion_forzada`
  - Invoca `eventos.onServidorDetenido(contenido)` con el mensaje
  - Cierra la sesión del cliente automáticamente

### UI del Cliente
- **Archivo**: `cliente/src/main/java/com/universidad/chat/cliente/ui/ChatView.java` (sin modificación)
- El método `onServidorDetenido(String mensaje)` ya existente muestra un alert con el mensaje y cierra la aplicación

## Pasos de Prueba

### 1. Preparación
```powershell
# Terminal 1: Iniciar el servidor
.\iniciar-servidor.ps1

# Terminal 2: Iniciar un cliente
.\prueba-cliente.ps1
```

### 2. Conectar un usuario
1. En el cliente, hacer login con un usuario existente (por ejemplo: usuario "andres", password "123")
2. Verificar que aparece conectado en el servidor

### 3. Desconectar forzosamente desde el servidor
1. En el servidor, ir a la pestaña **"Usuarios"**
2. Seleccionar el usuario conectado de la lista
3. Hacer clic en el botón **"Desconectar"**

### 4. Verificar comportamiento del cliente
El cliente debe:
- ✅ Recibir un alert/diálogo con el mensaje: **"El servidor te ha desconectado"**
- ✅ Cerrar automáticamente la ventana del chat
- ✅ Terminar el proceso de la aplicación

### 5. Verificar comportamiento del servidor
El servidor debe:
- ✅ Mostrar en el log: "Solicitando desconexión de usuario X"
- ✅ Remover al usuario de la lista de conectados
- ✅ Actualizar automáticamente la vista (sin necesidad de refrescar)

## Resultados Esperados

| Acción | Resultado |
|--------|-----------|
| Servidor envía notificación | Cliente recibe JSON con `desconexion_forzada: true` |
| Cliente procesa notificación | Muestra alert "El servidor te ha desconectado" |
| Cliente cierra sesión | Desconecta del servidor y cierra la ventana |
| Servidor actualiza estado | Usuario marcado como desconectado en BD y UI |

## Flujo Técnico

```
┌─────────────┐                                    ┌─────────────┐
│  Servidor   │                                    │   Cliente   │
│  Dashboard  │                                    │             │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │ 1. Admin hace clic en "Desconectar"             │
       │                                                  │
       │ 2. forzarDesconexionUsuario(idUsuario)          │
       │                                                  │
       │ 3. Envía NOTIFICACION con desconexion_forzada   │
       │ ─────────────────────────────────────────────> │
       │                                                  │
       │                                                  │ 4. Detecta flag
       │                                                  │    desconexion_forzada
       │                                                  │
       │                                                  │ 5. Llama onServidorDetenido()
       │                                                  │
       │                                                  │ 6. Muestra Alert
       │                                                  │    "El servidor te ha
       │                                                  │     desconectado"
       │                                                  │
       │                                                  │ 7. desconectar()
       │ 5. Espera 200ms                                 │
       │                                                  │ 8. Platform.exit()
       │ 6. cerrarConexion()                             │    System.exit(0)
       │                                                  │
       │ 7. socket.close()                               │
       │ <──────────────────────────────────────────── │
       │                                                  │
       │ 8. Actualiza BD y UI                            │
       │    (usuario desconectado)                       │
       │                                                  ▼
       ▼                                              (Proceso
  (Usuario removido                                   terminado)
   de conectados)
```

## Notas Técnicas

1. **Tiempo de espera**: El servidor espera 200ms después de enviar la notificación para dar tiempo a que el mensaje llegue al cliente antes de cerrar el socket.

2. **Reutilización de mecanismo**: Se reutiliza el mecanismo existente `onServidorDetenido()` que ya se usaba cuando el servidor se apaga completamente.

3. **Robustez**: Si el envío de la notificación falla (cliente ya desconectado, red caída, etc.), el servidor igualmente cierra la conexión limpiamente.

4. **Logging en servidor**: El evento de desconexión forzada se registra en la base de datos como evento "DESCONEXION".

## Estado de Compilación

✅ **Servidor**: Compilado exitosamente  
✅ **Cliente**: Compilado exitosamente

Ambos módulos están listos para ejecutarse y probar la funcionalidad.
