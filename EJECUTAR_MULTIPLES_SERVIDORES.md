# Ejecutar Múltiples Servidores en la Misma Máquina

## 📋 Preparación

### 1. Crear bases de datos separadas

Ejecuta en MySQL:

```sql
CREATE DATABASE IF NOT EXISTS chat_servidor_1;
CREATE DATABASE IF NOT EXISTS chat_servidor_2;
CREATE DATABASE IF NOT EXISTS chat_servidor_3;
```

Las tablas se crearán automáticamente al iniciar cada servidor.

---

## 🚀 Opción 1: System Properties (Recomendado)

Puedes sobrescribir cualquier configuración usando `-D` al ejecutar Maven, **sin modificar archivos**.

### Servidor 1 (Puerto 8080, P2P 9090)

**Terminal 1 (PowerShell):**
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run `
  -Dserver.port=8080 `
  -Dp2p.port=9090 `
  -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_1?connectTimeout=3000&socketTimeout=5000"
```

### Servidor 2 (Puerto 8081, P2P 9091)

**Terminal 2 (PowerShell):**
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run `
  -Dserver.port=8081 `
  -Dp2p.port=9091 `
  -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_2?connectTimeout=3000&socketTimeout=5000" `
  -Dp2p.peers="localhost:9090"
```

### Servidor 3 (Puerto 8082, P2P 9092)

**Terminal 3 (PowerShell):**
```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run `
  -Dserver.port=8082 `
  -Dp2p.port=9092 `
  -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_3?connectTimeout=3000&socketTimeout=5000" `
  -Dp2p.peers="localhost:9090,localhost:9091"
```

---

## 🔧 Opción 2: Modificar config.properties antes de ejecutar

Si prefieres modificar el archivo antes de cada ejecución:

### Para Servidor 1:

1. Edita `servidor/src/main/resources/config.properties`:

```properties
jdbc.url=jdbc:mysql://localhost:3306/chat_servidor_1?connectTimeout=3000&socketTimeout=5000
server.port=8080
p2p.port=9090
p2p.peers=
```

2. Ejecuta:
```powershell
mvn javafx:run
```

### Para Servidor 2:

1. Modifica `config.properties`:

```properties
jdbc.url=jdbc:mysql://localhost:3306/chat_servidor_2?connectTimeout=3000&socketTimeout=5000
server.port=8081
p2p.port=9091
p2p.peers=localhost:9090
```

2. Ejecuta:
```powershell
mvn javafx:run
```

---

## 📊 Propiedades Configurables

Todas estas propiedades pueden sobrescribirse con `-D`:

| Propiedad | Descripción | Ejemplo |
|-----------|-------------|---------|
| `server.host` | Host/IP del servidor | `-Dserver.host=192.168.1.10` |
| `server.port` | Puerto para clientes | `-Dserver.port=8080` |
| `p2p.enabled` | Habilitar P2P | `-Dp2p.enabled=true` |
| `p2p.port` | Puerto P2P (entre servidores) | `-Dp2p.port=9090` |
| `p2p.peers` | Servidores conocidos (separados por coma) | `-Dp2p.peers="host1:9090,host2:9091"` |
| `jdbc.url` | URL de la base de datos | `-Djdbc.url="jdbc:mysql://..."` |
| `jdbc.user` | Usuario de BD | `-Djdbc.user=root` |
| `jdbc.password` | Contraseña de BD | `-Djdbc.password=root` |
| `db.maxPoolSize` | Tamaño del pool de conexiones | `-Ddb.maxPoolSize=10` |

---

## 🔗 Configuración de Red P2P

### Topología Recomendada

Para 3 servidores en la misma máquina:

```
Servidor 1 (8080, P2P 9090)
    ↕
Servidor 2 (8081, P2P 9091) ← conoce a Servidor 1
    ↕
Servidor 3 (8082, P2P 9092) ← conoce a Servidor 1 y 2
```

- **Auto-descubrimiento**: El Servidor 3 aprenderá sobre el Servidor 2 automáticamente cuando se conecte al Servidor 1
- **Sincronización**: Los heartbeats mantienen el estado actualizado cada 20 segundos

### Verificar Conectividad P2P

1. Inicia los servidores
2. En cualquier servidor, ve a la pestaña **"Servidores"**
3. Deberías ver todos los demás servidores con estado **ONLINE**
4. Haz clic en **"Anunciar (HELLO)"** para forzar sincronización inmediata

---

## 🧪 Prueba Completa

### Paso 1: Iniciar los 3 servidores

```powershell
# Terminal 1
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor
mvn javafx:run -Dserver.port=8080 -Dp2p.port=9090 -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_1?connectTimeout=3000&socketTimeout=5000"

# Terminal 2
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor
mvn javafx:run -Dserver.port=8081 -Dp2p.port=9091 -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_2?connectTimeout=3000&socketTimeout=5000" -Dp2p.peers="localhost:9090"

# Terminal 3
cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor
mvn javafx:run -Dserver.port=8082 -Dp2p.port=9092 -Djdbc.url="jdbc:mysql://localhost:3306/chat_servidor_3?connectTimeout=3000&socketTimeout=5000" -Dp2p.peers="localhost:9090"
```

### Paso 2: Conectar clientes a diferentes servidores

```powershell
# Cliente 1 → Servidor 1 (puerto 8080)
cd c:\Users\monto\universidad\Arqui\cliente-servidor\cliente
mvn javafx:run -Dserver.host=localhost -Dserver.port=8080

# Cliente 2 → Servidor 2 (puerto 8081)
cd c:\Users\monto\universidad\Arqui\cliente-servidor\cliente
mvn javafx:run -Dserver.host=localhost -Dserver.port=8081
```

### Paso 3: Verificar comunicación P2P

1. Registra usuarios en cada servidor
2. En el Cliente 1, ve a "Conectados" - deberías ver usuarios de ambos servidores
3. Envía un mensaje desde Cliente 1 a un usuario del Servidor 2
4. El mensaje debe entregarse automáticamente vía P2P

---

## 🐛 Troubleshooting

### Error: "Puerto ya en uso"

Asegúrate de que cada servidor use puertos únicos:
- Servidor 1: `server.port=8080`, `p2p.port=9090`
- Servidor 2: `server.port=8081`, `p2p.port=9091`
- Servidor 3: `server.port=8082`, `p2p.port=9092`

### Error: "No se puede conectar a la BD"

Verifica que las bases de datos existan:
```sql
SHOW DATABASES LIKE 'chat_servidor_%';
```

### Servidores no se ven entre sí

1. Verifica logs: Busca `P2P escuchando en` y `P2P TX HELLO`
2. Revisa firewall local (debe permitir localhost)
3. Prueba HELLO manual desde UI: pestaña "Servidores" → "Anunciar (HELLO)"

### Usuarios remotos no aparecen

1. Verifica que `p2p.enabled=true` en todos los servidores
2. Revisa logs: Busca `P2P ACK: procesados X usuarios`
3. Espera 20-30 segundos para que el heartbeat sincronice

---

## 💡 Tips

✅ **System properties** es más conveniente - no requiere editar archivos  
✅ Usa `p2p.peers` solo en algunos servidores - el resto se auto-descubre  
✅ Los heartbeats cada 20s mantienen todo sincronizado automáticamente  
✅ Puedes mezclar servidores en diferentes máquinas y en la misma máquina  

---

## 📝 Ejemplo de Script PowerShell

Guarda como `iniciar-3-servidores.ps1`:

```powershell
# Script para iniciar 3 servidores en paralelo

$servidor1 = Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor; mvn javafx:run -Dserver.port=8080 -Dp2p.port=9090 -Djdbc.url='jdbc:mysql://localhost:3306/chat_servidor_1?connectTimeout=3000&socketTimeout=5000'"
) -PassThru

Start-Sleep -Seconds 10

$servidor2 = Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor; mvn javafx:run -Dserver.port=8081 -Dp2p.port=9091 -Djdbc.url='jdbc:mysql://localhost:3306/chat_servidor_2?connectTimeout=3000&socketTimeout=5000' -Dp2p.peers='localhost:9090'"
) -PassThru

Start-Sleep -Seconds 10

$servidor3 = Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd c:\Users\monto\universidad\Arqui\cliente-servidor\servidor; mvn javafx:run -Dserver.port=8082 -Dp2p.port=9092 -Djdbc.url='jdbc:mysql://localhost:3306/chat_servidor_3?connectTimeout=3000&socketTimeout=5000' -Dp2p.peers='localhost:9090'"
) -PassThru

Write-Host "✅ 3 servidores iniciados"
Write-Host "Servidor 1: Puerto 8080 (P2P 9090)"
Write-Host "Servidor 2: Puerto 8081 (P2P 9091)"
Write-Host "Servidor 3: Puerto 8082 (P2P 9092)"
```

Ejecuta con:
```powershell
.\iniciar-3-servidores.ps1
```
