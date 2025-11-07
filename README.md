# Chat Universidad - Sistema Cliente-Servidor

Sistema de chat completo para la comunidad académica de la Universidad, implementado en Java 21 con arquitectura cliente-servidor TCP/IP, persistencia dual (MySQL + H2), interfaz JavaFX y funcionalidades multimedia (audio con transcripción).

## 🌟 Características Principales

- ✅ **Mensajería en tiempo real** - Chat directo entre usuarios y canales grupales
- ✅ **Mensajes de audio** - Grabación, transmisión y transcripción automática con VOSK
- ✅ **Envío de archivos** - Compartir documentos entre usuarios y en canales
- ✅ **Persistencia dual** - H2 local por usuario (cliente) + MySQL centralizada (servidor)
- ✅ **Sincronización automática** - Historial completo sincronizado al login
- ✅ **Canales públicos y privados** - Con sistema de invitaciones
- ✅ **Interfaz gráfica moderna** - JavaFX con CSS personalizado
- ✅ **Reportes PDF** - Estadísticas de usuarios, mensajes y actividad
- ✅ **Conexión multi-máquina** - Configuración de red flexible
- ✅ **Monitoreo en tiempo real** - Dashboard del servidor con estadísticas

## 📁 Estructura del Proyecto

```
cliente-servidor/
├── cliente/                          # Aplicación cliente (JavaFX + H2)
│   ├── src/main/java/               # Código fuente del cliente
│   │   ├── app/                     # Aplicaciones principales
│   │   ├── dao/                     # Acceso a datos H2
│   │   ├── model/                   # Modelos del dominio
│   │   ├── service/                 # Servicios y lógica de negocio
│   │   ├── ui/                      # Interfaz JavaFX
│   │   └── util/                    # Utilidades y helpers
│   ├── src/main/resources/          # Recursos
│   │   ├── css/                     # Estilos CSS
│   │   └── config.network.properties # Configuración de red (empaquetado)
│   ├── config.network.properties    # Configuración local (no se commitea)
│   ├── config.network.properties.example # Plantilla de configuración
│   ├── data/                        # Bases de datos H2 por usuario
│   └── README_CONFIG.md             # Guía de configuración
├── servidor/                        # Aplicación servidor (MySQL)
│   ├── src/main/java/               # Código fuente del servidor
│   │   ├── dao/                     # Acceso a datos MySQL
│   │   ├── model/                   # Modelos del dominio
│   │   ├── service/                 # Servicios TCP y lógica
│   │   ├── ui/                      # Dashboard JavaFX
│   │   └── util/                    # Utilidades y reportes
│   ├── uploads/                     # Archivos subidos
│   ├── vosk-models/                 # Modelos de transcripción
│   └── crear-bd.sql                 # Script de base de datos
├── docs/                            # Documentación adicional
├── .gitignore                       # Exclusiones de Git
├── ejecutar-cliente.ps1             # Script para ejecutar cliente
├── iniciar-servidor.ps1             # Script para ejecutar servidor
├── GUIA_CONEXION_RED.md            # Guía de conexión en red
├── GUIA_MVN_JAVAFX_RUN.md          # Guía para desarrollo con Maven
└── README.md                        # Este archivo
```

## 🛠️ Tecnologías

### Backend
- **Java 21** - Lenguaje base
- **Maven** - Gestión de dependencias y build
- **JDBC** - Acceso a bases de datos
- **MySQL 8.0** - Base de datos centralizada (servidor)
- **H2 Database** - Base de datos embebida por usuario (cliente)
- **TCP/IP** - Protocolo de comunicación propietario
- **Gson** - Serialización JSON

### Frontend
- **JavaFX 21** - Framework de interfaz gráfica
- **CSS** - Estilos personalizados

### Multimedia
- **VOSK** - Motor de reconocimiento de voz offline
- **Java Sound API** - Captura y reproducción de audio

### Reportes
- **Apache PDFBox** - Generación de reportes PDF
- **iText** - Exportación de datos

### Logging
- **SLF4J** - Abstracción de logging
- **Logback** - Implementación de logs

## 📋 Requisitos Previos

- **JDK 21 o superior**
- **Maven 3.6 o superior**
- **MySQL 8.0 o superior** (solo para el servidor)
- **Red local o WiFi** (para conexión multi-máquina)

## 🚀 Instalación y Configuración

### 1. Configurar Base de Datos (Servidor)

```sql
-- Crear la base de datos
CREATE DATABASE chat_universidad;

-- Ejecutar el script de tablas
SOURCE servidor/crear-bd.sql;
```

### 2. Configurar Conexión MySQL (Servidor)

Edita `servidor/src/main/resources/config.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/chat_universidad
db.user=root
db.password=tu_password
```

### 3. Descargar Modelo VOSK (Opcional - para transcripción de audio)

1. Descarga el modelo español desde: https://alphacephei.com/vosk/models
2. Extrae en `servidor/vosk-models/vosk-model-small-es-0.42/`

### 4. Compilar el Proyecto

```powershell
# Compilar servidor
cd servidor
mvn clean package

# Compilar cliente
cd ..\cliente
mvn clean package
```

## 🎯 Ejecución

### Servidor

**Opción 1: Con Maven (desarrollo)**
```powershell
cd servidor
mvn exec:java
```

**Opción 2: Con JAR (producción)**
```powershell
cd servidor
java -jar target/chat-servidor-1.0-SNAPSHOT.jar
```

**Opción 3: Script PowerShell**
```powershell
.\iniciar-servidor.ps1
```

### Cliente (Misma Máquina)

**Opción 1: Con Maven (desarrollo)**
```powershell
cd cliente
mvn javafx:run
```

**Opción 2: Con JAR (producción)**
```powershell
cd cliente
java -jar target/chat-cliente-1.0-SNAPSHOT.jar
```

### Cliente (Otra Máquina en Red)

#### 1. Configurar la IP del servidor

Crea `cliente/config.network.properties`:

```properties
chat.server.host=192.168.1.5  # IP del servidor
chat.server.port=8080
chat.timeout.seconds=10
```

O copia el ejemplo:
```powershell
cd cliente
copy config.network.properties.example config.network.properties
# Edita el archivo con la IP correcta
```

#### 2. Ejecutar el cliente

**Opción A: Con archivo de configuración**
```powershell
cd cliente
mvn javafx:run
```

**Opción B: Forzar IP por línea de comandos (recomendado)**
```powershell
cd cliente
mvn javafx:run -Dchat.server.host=192.168.1.5 -Dchat.server.port=8080
```

**Opción C: Script automatizado**
```powershell
.\ejecutar-cliente.ps1 -ServerHost 192.168.1.5
```

#### 3. Verificar conexión

Al iniciar, busca en los logs:
```
ClienteProtocoloService usando servidor 192.168.1.5:8080 (timeout=10s)
```

## 🌐 Configuración de Red

### Obtener IP del Servidor

```powershell
# En la máquina servidor
ipconfig
# Busca tu IP en WiFi o Ethernet (ej: 192.168.1.5)
```

### Verificar Conectividad

```powershell
# Desde la máquina cliente
ping 192.168.1.5
Test-NetConnection -ComputerName 192.168.1.5 -Port 8080
```

### Firewall (si es necesario)

```powershell
# En el servidor (como Administrador)
New-NetFirewallRule -DisplayName "Chat Server 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

Ver `GUIA_CONEXION_RED.md` para troubleshooting detallado.

## 📡 Protocolo de Comunicación

El sistema utiliza un **protocolo propietario sobre TCP/IP**:

### Estructura del Mensaje
```
[Versión(1)][Tipo(1)][Longitud(4)][IdUsuario(4)][Cuerpo(n)]
```

### Tipos de Mensaje
- `0x01` - REGISTRO
- `0x02` - AUTENTICACION
- `0x03` - MENSAJE_TEXTO
- `0x04` - MENSAJE_AUDIO
- `0x05` - MENSAJE_CANAL
- `0x06` - CREACION_CANAL
- `0x07` - SOLICITUD_UNION_CANAL
- `0x08` - RESPUESTA_CANAL
- `0x09` - NOTIFICACION
- `0x0A` - SOLICITUD_LISTA
- `0x0B` - ENVIO_ARCHIVO
- `0x0C` - CIERRE_SESION

### Detalles de Implementación
- **Puerto**: 8080
- **Codificación**: UTF-8 para texto, Base64 para binarios
- **Conexión**: Persistente con heartbeat
- **Serialización**: JSON (Gson) para payloads complejos

## 💾 Persistencia

### Servidor (MySQL)
- **Base de datos centralizada**: `chat_universidad`
- **Tablas principales**: usuarios, canales, mensajes, miembros_canal, invitaciones_canal
- **Relaciones**: Integridad referencial con ON DELETE CASCADE

### Cliente (H2)
- **Base de datos por usuario**: `cliente/data/chat_usuario_{id}.mv.db`
- **Sincronización**: Al login, descarga mensajes faltantes del servidor
- **Deduplicación**: Por `id_servidor` (UNIQUE constraint)
- **Display**: Lee estrictamente de la DB local

### Flujo de Sincronización
1. Usuario inicia sesión
2. Cliente solicita usuarios y canales
3. Servidor envía historial completo al usuario recién conectado
4. Cliente persiste en H2 solo mensajes nuevos (dedupe por id_servidor)
5. Mensajes en tiempo real se persisten inmediatamente
6. Al abrir un chat, se lee desde H2 local

## 🎨 Interfaz de Usuario

### Cliente
- **Login**: Autenticación de usuarios
- **Chat Principal**: Lista de usuarios/canales y área de mensajes
- **Grabación de Audio**: Con visualización de forma de onda
- **Envío de Archivos**: Drag & drop y explorador
- **Notificaciones**: En tiempo real

### Servidor (Dashboard)
- **Usuarios Conectados**: Lista en tiempo real
- **Estadísticas**: Mensajes, usuarios activos, canales
- **Reportes PDF**: Usuarios, mensajes, actividad
- **Control**: Detener servidor, broadcast, desconectar usuarios

## 📊 Reportes

El servidor genera reportes PDF con:
- Lista de usuarios con IP:Puerto de conexión
- Estadísticas de mensajes por tipo
- Actividad por canal
- Historial de conexiones

Ubicación: `servidor/reportes/`

## 🔧 Desarrollo

### Estructura de Código

#### Cliente
- `ClienteApp` - Punto de entrada JavaFX
- `ClienteTCP` - Socket TCP con listeners
- `ClienteProtocoloService` - API asíncrona (CompletableFuture)
- `ChatView` - Controlador de UI principal
- `SincronizacionService` - Lógica de sincronización
- `MensajeDAO` / `UsuarioDAO` / `CanalDAO` - Acceso a H2

#### Servidor
- `ServidorTCPIntegrado` - Servidor TCP principal
- `ServidorDashboardView` - UI del dashboard
- `MensajeService` / `UsuarioService` / `CanalService` - Lógica de negocio
- `AudioTranscriptionService` - Transcripción con VOSK
- `PDFExporter` - Generación de reportes

### Compilar sin Tests

```powershell
mvn clean package -DskipTests
```

### Logs de Debug

```powershell
# Cliente
mvn javafx:run -Dorg.slf4j.simpleLogger.defaultLogLevel=debug

# Servidor
mvn exec:java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

### Ubicación de Logs

- Cliente: Consola de Maven
- Servidor: Consola + `servidor/logs/` (si configurado)

## 🐛 Troubleshooting

### Cliente no conecta al servidor remoto

1. **Verifica IP del servidor:**
   ```powershell
   # En servidor
   ipconfig
   ```

2. **Prueba conectividad:**
   ```powershell
   # En cliente
   ping 192.168.1.5
   Test-NetConnection -ComputerName 192.168.1.5 -Port 8080
   ```

3. **Verifica configuración:**
   ```powershell
   # En cliente
   type config.network.properties
   ```

4. **Fuerza la IP:**
   ```powershell
   mvn javafx:run -Dchat.server.host=192.168.1.5
   ```

Ver `GUIA_CONEXION_RED.md` y `GUIA_MVN_JAVAFX_RUN.md` para más detalles.

### Base de datos H2 corrupta

```powershell
# Eliminar y recrear
cd cliente\data
del chat_usuario_*.mv.db
# Se recreará al próximo login
```

### Audio no transcribe

1. Verifica que el modelo VOSK esté en `servidor/vosk-models/`
2. Revisa los logs del servidor para errores de VOSK
3. El modelo debe ser compatible con español

### Mensajes duplicados

- Ya resuelto: La deduplicación por `id_servidor` evita duplicados
- Si persiste, verifica que la constraint UNIQUE esté en la tabla

## 📝 Estado del Proyecto

### ✅ Completado

- ✅ Arquitectura cliente-servidor TCP/IP
- ✅ Protocolo de comunicación propietario
- ✅ Persistencia dual (MySQL + H2 por usuario)
- ✅ Sincronización automática al login
- ✅ Deduplicación de mensajes
- ✅ Historial simétrico entre usuarios
- ✅ Mensajería de texto en tiempo real
- ✅ Mensajes de audio con transcripción
- ✅ Envío de archivos
- ✅ Canales públicos y privados
- ✅ Sistema de invitaciones
- ✅ Interfaz gráfica JavaFX completa
- ✅ Dashboard del servidor
- ✅ Reportes PDF
- ✅ Conexión multi-máquina
- ✅ Configuración de red flexible
- ✅ Logs con IP:Puerto de clientes
- ✅ Repositorio Git y GitHub

### 🔄 Mejoras Futuras (Opcionales)

- ⏳ Push simétrico a usuarios ya conectados
- ⏳ Cifrado de comunicaciones (TLS/SSL)
- ⏳ Autenticación con tokens JWT
- ⏳ Emojis y markdown en mensajes
- ⏳ Notificaciones de escritorio
- ⏳ Estado "escribiendo..."
- ⏳ Búsqueda de mensajes
- ⏳ Exportar conversaciones

## 🤝 Contribuir

Este es un proyecto académico. Para contribuir:

1. Fork el repositorio
2. Crea una rama (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -m 'Agrega nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

## 📄 Documentación Adicional

- `requisitos.txt` - Requisitos funcionales completos del proyecto
- `GUIA_CONEXION_RED.md` - Guía detallada de conexión en red
- `GUIA_MVN_JAVAFX_RUN.md` - Guía para desarrollo con Maven
- `SOLUCION_FIREWALL.md` - Troubleshooting de firewall
- `cliente/README_CONFIG.md` - Configuración del cliente
- `servidor/DIAGNOSTICO_TRANSCRIPCION.md` - Diagnóstico de transcripción de audio

## 👥 Autores

Proyecto académico - Universidad

## 📜 Licencia

Proyecto educativo - Todos los derechos reservados

## 🔗 Repositorio

GitHub: https://github.com/IAMS2003/cliente-servidor

---

**Versión**: 1.0  
**Última actualización**: Noviembre 2025
