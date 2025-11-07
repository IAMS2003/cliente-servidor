# 🚀 Guía Rápida: Ejecutar Múltiples Servidores

## Opción 1: Usar Scripts PowerShell (Más Fácil)

### Paso 1: Crear las bases de datos
Ejecuta en MySQL:
```sql
CREATE DATABASE IF NOT EXISTS chat_servidor_1;
CREATE DATABASE IF NOT EXISTS chat_servidor_2;
CREATE DATABASE IF NOT EXISTS chat_servidor_3;
```

O ejecuta el archivo SQL:
```powershell
mysql -u root -p < crear_bases_datos.sql
```

### Paso 2: Iniciar los servidores

Abre **3 terminales PowerShell** diferentes y ejecuta en cada una:

**Terminal 1:**
```powershell
.\iniciar-servidor-1.ps1
```

**Terminal 2:**
```powershell
.\iniciar-servidor-2.ps1
```

**Terminal 3:**
```powershell
.\iniciar-servidor-3.ps1
```

---

## Opción 2: Comandos Manuales

### Servidor 1 (puerto 8080):
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run `
  -Dserver.port=8080 `
  -Dp2p.port=9090 `
  -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_1?connectTimeout=3000&socketTimeout=5000"
```

### Servidor 2 (puerto 8081):
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run `
  -Dserver.port=8081 `
  -Dp2p.port=9091 `
  -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_2?connectTimeout=3000&socketTimeout=5000" `
  -Dp2p.peers="localhost:9090"
```

### Servidor 3 (puerto 8082):
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run `
  -Dserver.port=8082 `
  -Dp2p.port=9092 `
  -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_3?connectTimeout=3000&socketTimeout=5000" `
  -Dp2p.peers="localhost:9090"
```

---

## ✅ Verificar que funciona

1. **Verifica logs del servidor**: Deberías ver:
   ```
   P2P escuchando en 192.168.x.x:9090
   P2P TX HELLO -> localhost:9091
   P2P ACK: procesados X usuarios
   ```

2. **Revisa la pestaña "Servidores"** en cualquier servidor:
   - Deberías ver los otros servidores con estado **ONLINE**

3. **Conecta clientes a diferentes servidores** y verifica que se vean entre sí:
   ```powershell
   # Cliente 1 → Servidor 1
   cd cliente
   mvn javafx:run -Dserver.port=8080
   
   # Cliente 2 → Servidor 2
   cd cliente
   mvn javafx:run -Dserver.port=8081
   ```

4. **Envía mensajes entre usuarios de diferentes servidores**
   - Los mensajes deben entregarse automáticamente vía P2P

---

## 🔧 Propiedades Configurables

Puedes cambiar cualquier configuración usando `-D`:

| Propiedad | Ejemplo |
|-----------|---------|
| `server.port` | `-Dserver.port=8080` |
| `p2p.port` | `-Dp2p.port=9090` |
| `jdbc.url` | `-Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_1..."` |
| `p2p.peers` | `-Dp2p.peers="localhost:9090,localhost:9091"` |

---

## 📚 Documentación Completa

Ver `EJECUTAR_MULTIPLES_SERVIDORES.md` para:
- Todas las opciones de configuración
- Troubleshooting detallado
- Ejemplos avanzados
- Scripts de automatización
