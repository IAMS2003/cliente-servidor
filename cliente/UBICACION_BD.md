# Ubicación de la Base de Datos H2 del Cliente

## 📁 Dónde se crea la carpeta `data/`

La carpeta `data/` y el archivo de base de datos se crean **en el directorio de trabajo actual** desde donde ejecutas el cliente.

### Escenario 1: Ejecutar desde la carpeta `cliente/`

Si ejecutas:
```powershell
cd cliente
mvn javafx:run
# O
java -jar target/chat-cliente-1.0-SNAPSHOT.jar
```

La BD se crea en:
```
cliente/
├── data/
│   ├── chat_cliente.mv.db
│   └── chat_cliente.trace.db (solo si hay errores)
```

### Escenario 2: Ejecutar desde la raíz del proyecto

Si ejecutas:
```powershell
cd cliente-servidor
mvn -pl cliente javafx:run
```

La BD se crea en:
```
cliente-servidor/
├── data/
│   ├── chat_cliente.mv.db
│   └── chat_cliente.trace.db
```

### Escenario 3: Ejecutar desde VS Code o IDE

Depende de la configuración del IDE:
- **VS Code**: normalmente desde la raíz del workspace
- **IntelliJ IDEA**: configurable en Run Configuration
- **Eclipse**: configurable en Run Configuration

## ✅ Cambios realizados

He modificado `ConexionBD.java` para que **cree automáticamente** la carpeta `data/` si no existe, independientemente de dónde ejecutes el cliente.

Al iniciar el cliente verás en los logs:
```
[INFO] Directorio de BD creado: C:\...\cliente\data
[INFO] Configuración de BD cargada: jdbc:h2:./data/chat_cliente
[INFO] Conexión a BD establecida
```

## 🔍 Cómo encontrar tu base de datos

Si no sabes dónde se creó, busca en los logs del cliente la línea:
```
[INFO] Directorio de BD creado: [RUTA_COMPLETA]
```

O ejecuta este comando PowerShell desde la raíz del proyecto:
```powershell
Get-ChildItem -Recurse -Filter "chat_cliente.mv.db" | Select-Object FullName
```

## 🔧 Forzar ubicación absoluta (opcional)

Si quieres que la BD siempre se cree en el mismo lugar sin importar desde dónde ejecutes, cambia en `config.properties`:

```properties
# Ruta absoluta (ejemplo Windows)
jdbc.url=jdbc:h2:C:/datos/chat_cliente

# Ruta absoluta (ejemplo Linux/Mac)
jdbc.url=jdbc:h2:/home/usuario/datos/chat_cliente
```

## 🗑️ Limpiar la base de datos

Para empezar de cero, simplemente borra la carpeta `data/`:
```powershell
Remove-Item -Recurse -Force data
```

Al reiniciar el cliente, se creará una nueva BD vacía.
