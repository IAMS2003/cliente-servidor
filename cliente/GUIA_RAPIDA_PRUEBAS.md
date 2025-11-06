# 🚀 Guía Rápida: BD por Usuario + Sincronización

## Cambios Principales

✅ **Cada usuario tiene su propia base de datos H2**  
✅ **Sincronización automática** al abrir chats (solo descarga mensajes nuevos)  
✅ **Prevención de duplicados** inteligente  
✅ **Funciona offline** (muestra historial local si no hay servidor)  

---

## 📋 Cómo Funciona

### Al Iniciar Sesión

```
1. Usuario ingresa: usuario1 / password1
2. Sistema autentica con el servidor
3. Se crea/abre: data/chat_usuario1.mv.db
4. Usuario puede chatear normalmente
```

### Al Abrir un Chat

```
1. Usuario selecciona chat con "usuario2"
2. Sistema sincroniza con servidor:
   - Descarga mensajes nuevos
   - Verifica duplicados
   - Guarda solo los que faltan
3. Sistema carga todo desde BD local
4. Muestra historial completo en pantalla
```

---

## 🧪 Prueba Rápida

### Paso 1: Iniciar con dos usuarios

**Terminal 1 - Usuario 1:**
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\cliente
mvn javafx:run
# Login: usuario1 / password1
```

**Terminal 2 - Usuario 2:**
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\cliente
mvn javafx:run
# Login: usuario2 / password2
```

### Paso 2: Enviar mensajes

1. En ventana de **usuario1**: Enviar mensaje a usuario2
2. En ventana de **usuario2**: Debería aparecer el mensaje automáticamente

### Paso 3: Verificar archivos BD

```powershell
dir c:\Users\monto\universidad\Arqui\cliente-servidor\cliente\data\
```

**Resultado esperado:**
```
chat_usuario1.mv.db    ← Base de datos del usuario 1
chat_usuario2.mv.db    ← Base de datos del usuario 2
```

### Paso 4: Probar sincronización

1. **Cerrar** ventana de usuario2
2. En ventana de usuario1: **Enviar 5 mensajes** a usuario2
3. **Reabrir** cliente y hacer login con usuario2
4. **Abrir chat** con usuario1

**Resultado esperado:**
- Barra de estado muestra: `"Sincronizados 5 mensajes nuevos"`
- Los 5 mensajes aparecen en el historial

### Paso 5: Probar modo offline

1. **Detener el servidor**
2. En cliente activo: **Cerrar y reabrir** un chat

**Resultado esperado:**
- Barra de estado: `"Historial cargado desde BD local (sin conexión)"`
- Los mensajes previos **SÍ aparecen** (guardados localmente)

---

## 🔍 Verificar en H2 Console

### Opción 1: Usar H2 Console Web

```powershell
# Iniciar H2 Console
$h2Jar = "$env:USERPROFILE\.m2\repository\com\h2database\h2\2.2.224\h2-2.224.jar"
java -jar $h2Jar
```

**Configuración:**
- JDBC URL: `jdbc:h2:c:/Users/monto/universidad/Arqui/cliente-servidor/cliente/data/chat_usuario1`
- Usuario: `sa`
- Password: (dejar vacío)

### Consultas útiles:

```sql
-- Ver todos los mensajes guardados
SELECT * FROM mensaje_log ORDER BY fecha DESC;

-- Contar mensajes
SELECT COUNT(*) as total FROM mensaje_log;

-- Ver mensajes con audio
SELECT id, id_emisor, transcripcion, 
       LENGTH(audio_data) as tamaño_audio 
FROM mensaje_log 
WHERE tipo_mensaje = 'AUDIO';
```

---

## 📊 Diferencias Visibles

### Antes vs Ahora

| Aspecto | ❌ Antes | ✅ Ahora |
|---------|---------|---------|
| **Archivo BD** | `chat_cliente.mv.db` (compartido) | `chat_usuario1.mv.db`, `chat_usuario2.mv.db` (separados) |
| **Al abrir chat** | Siempre solicita todo al servidor | Sincroniza solo mensajes nuevos |
| **Duplicados** | Posibles al recargar | Automáticamente prevenidos |
| **Sin servidor** | No funciona | Muestra historial local |
| **Barra de estado** | "Cargando..." | "Sincronizados X mensajes nuevos" |

---

## ⚠️ Notas Importantes

1. **Primera vez**: Al iniciar sesión por primera vez, se crea el archivo de BD automáticamente

2. **Cambio de usuario**: Al cerrar sesión y entrar con otro usuario, se usa una BD diferente

3. **Sincronización inteligente**: 
   - Compara mensajes por emisor, tipo, fecha y destino
   - Tolerancia de ±2 segundos en la fecha
   - No guarda duplicados

4. **Modo offline**: 
   - Si el servidor no responde, solo muestra historial local
   - No se bloquea ni crashea la aplicación

---

## 🐛 Solución de Problemas

### No se crea el archivo de BD

**Causa**: Error en inicialización  
**Solución**: Revisar logs en consola al hacer login

### Los mensajes aparecen duplicados

**Causa**: Error en verificación de duplicados  
**Solución**: 
```sql
-- Verificar duplicados en H2 Console
SELECT id_emisor, id_receptor, tipo_mensaje, fecha, COUNT(*) 
FROM mensaje_log 
GROUP BY id_emisor, id_receptor, tipo_mensaje, fecha 
HAVING COUNT(*) > 1;
```

### No sincroniza mensajes del servidor

**Causa**: Error en conexión o en conversión JSON  
**Solución**: Revisar logs en consola:
```
Error sincronizando con servidor: ...
```

---

## 📁 Ubicación de Archivos

```
cliente/
├── data/
│   ├── chat_usuario1.mv.db     ← BD del usuario ID 1
│   ├── chat_usuario2.mv.db     ← BD del usuario ID 2
│   └── chat_usuario3.mv.db     ← BD del usuario ID 3
├── target/
│   └── chat-cliente-1.0-SNAPSHOT.jar
└── src/
    └── main/java/...
```

---

## ✅ Checklist de Validación

Antes de dar por completada la prueba:

- [ ] Se crean archivos `chat_usuario*.mv.db` separados por usuario
- [ ] Los mensajes se sincronizan correctamente al abrir chats
- [ ] No aparecen mensajes duplicados
- [ ] La barra de estado muestra "Sincronizados X mensajes nuevos"
- [ ] Funciona en modo offline (muestra historial local)
- [ ] Cada usuario solo ve sus propios mensajes (aislamiento correcto)

---

**Estado**: ✅ Listo para pruebas  
**Fecha**: 6 de noviembre de 2025
