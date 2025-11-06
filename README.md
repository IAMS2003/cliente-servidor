# Chat Universidad - Sistema Cliente-Servidor

Sistema de chat para la comunidad académica de la Universidad, implementado en Java 21 con JDBC y JavaFX.

## Estructura del Proyecto

```
cliente-servidor/
├── cliente/          # Aplicación cliente (JavaFX + H2Database)
├── servidor/         # Aplicación servidor (MySQL)
├── docs/             # Documentación
└── requisitos.txt    # Requisitos completos del proyecto
```

## Tecnologías

- **Java 21**
- **JavaFX** (interfaz gráfica del cliente)
- **JDBC** (acceso a bases de datos)
- **H2Database** (cliente)
- **MySQL** (servidor)
- **TCP-IP** (protocolo propietario de comunicación)
- **Maven** (gestión de dependencias)

## Requisitos Previos

- JDK 21 o superior
- Maven 3.6 o superior
- MySQL 8.0 o superior (para el servidor)

## Compilación

### Cliente
```powershell
mvn -f cliente/pom.xml clean compile
```

### Servidor
```powershell
mvn -f servidor/pom.xml clean compile
```

## Ejecución

### Iniciar Servidor
```powershell
mvn -f servidor/pom.xml exec:java
```

O usar el script:
```powershell
.\iniciar-servidor.ps1
```

### Ejecutar Cliente
```powershell
mvn -f cliente/pom.xml javafx:run
```

### Prueba del Protocolo TCP-IP
```powershell
mvn -f cliente/pom.xml exec:java "-Dexec.mainClass=com.universidad.chat.cliente.app.PruebaProtocolo"
```

O usar el script:
```powershell
.\prueba-cliente.ps1
```

## Estado de Desarrollo

✅ Estructura del proyecto creada  
✅ Configuración de Maven y dependencias  
✅ Protocolo TCP-IP propietario implementado  
✅ Comunicación cliente-servidor básica funcionando  
🔄 Modelo de datos y DAOs (en progreso)  
⏳ Lógica de negocio del servidor  
⏳ Lógica de negocio del cliente  
⏳ Interfaz gráfica (JavaFX)  
⏳ Patrones de diseño (Factory, Observer, Object Pool)  
⏳ Pruebas unitarias

## Protocolo de Comunicación

El sistema utiliza un protocolo propietario sobre TCP-IP:

- **Puerto**: 8080
- **Estructura del mensaje**: `[Versión(1)][Tipo(1)][Longitud(4)][IdUsuario(4)][Cuerpo(n)]`
- **Tipos de mensaje**: Registro, Autenticación, Texto, Audio, Canal, Notificación, etc.

Ver `requisitos.txt` para detalles completos del protocolo.

## Documentación

- `requisitos.txt`: Requisitos funcionales completos
- `docs/arquitectura.md`: Arquitectura del sistema
- `cliente/README.md`: Documentación del cliente
- `servidor/README.md`: Documentación del servidor

## Licencia

Proyecto académico - Universidad
