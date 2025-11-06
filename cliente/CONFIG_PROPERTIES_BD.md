# Verificación: Configuración desde Properties

## Cambio Implementado

✅ **Ahora la configuración de BD se lee desde `config.properties`**

### Antes:
```java
// URL hardcodeada
this.url = "jdbc:h2:./data/chat_usuario" + idUsuario;
this.usuario = "sa";
this.contrasena = "";
```

### Ahora:
```java
// Lee del config.properties:
// - jdbc.url = jdbc:h2:./data/chat_cliente
// - jdbc.user = sa
// - jdbc.password = 

// Extrae la base: jdbc:h2:./data/
// Completa con: chat_usuario{ID}
// Resultado: jdbc:h2:./data/chat_usuario1
```

---

## 🔍 Cómo Funciona

### 1. Al iniciar la aplicación:

```java
ConexionBD.getInstancia() 
  → new ConexionBD()
  → cargarDriver()
  → cargarConfiguracionBase()
     → Lee config.properties
     → Extrae urlBase = "jdbc:h2:./data/"
     → Guarda usuario = "sa"
     → Guarda contrasena = ""
```

### 2. Al hacer login con usuario ID=5:

```java
ConexionBD.getInstancia().inicializar(5, "usuario5")
  → this.url = urlBase + "chat_usuario5"
  → this.url = "jdbc:h2:./data/chat_usuario5"
  → crearDirectorioBD()
```

---

## 📋 config.properties

```properties
# Configuración de la base de datos H2 para el cliente
# La URL se completará automáticamente con: chat_usuario{ID}
# Ejemplo: jdbc:h2:./data/chat_usuario1, jdbc:h2:./data/chat_usuario2, etc.
jdbc.url=jdbc:h2:./data/chat_cliente
jdbc.user=sa
jdbc.password=
```

**Nota**: El nombre `chat_cliente` en la URL se ignora, solo se usa la ruta base `jdbc:h2:./data/`

---

## ✅ Ventajas

1. **Configuración centralizada**: Cambiar la ruta base se hace en un solo lugar
2. **Flexibilidad**: Se puede cambiar usuario/contraseña de BD sin recompilar
3. **Valores por defecto**: Si falta el properties, usa valores seguros
4. **Compatibilidad**: Sigue creando BD separadas por usuario

---

## 🧪 Prueba Rápida

### Probar con configuración por defecto:

```powershell
cd c:\Users\monto\universidad\Arqui\cliente-servidor\cliente
mvn javafx:run
# Login: usuario1 / password1
```

**Resultado esperado:**
```
[ConexionBD] Configuración base cargada - URL base: jdbc:h2:./data/, usuario: sa
[ConexionBD] BD inicializada para usuario 'usuario1' (ID: 1): jdbc:h2:./data/chat_usuario1
```

### Probar con configuración personalizada:

**Modificar `config.properties`:**
```properties
jdbc.url=jdbc:h2:./mi_carpeta_custom/datos_chat
jdbc.user=admin
jdbc.password=mipassword
```

**Reiniciar cliente y hacer login con usuario ID=2**

**Resultado esperado:**
```
[ConexionBD] Configuración base cargada - URL base: jdbc:h2:./mi_carpeta_custom/, usuario: admin
[ConexionBD] BD inicializada para usuario 'usuario2' (ID: 2): jdbc:h2:./mi_carpeta_custom/chat_usuario2
```

---

## 📁 Ubicación de Archivos

### Con configuración por defecto:
```
cliente/
└── data/
    ├── chat_usuario1.mv.db
    ├── chat_usuario2.mv.db
    └── chat_usuario3.mv.db
```

### Con configuración personalizada (ejemplo):
```
cliente/
└── mi_carpeta_custom/
    ├── chat_usuario1.mv.db
    ├── chat_usuario2.mv.db
    └── chat_usuario3.mv.db
```

---

## 🔧 Valores por Defecto

Si `config.properties` no se encuentra o tiene errores:

```java
urlBase = "jdbc:h2:./data/"
usuario = "sa"
contrasena = ""
```

---

## ✅ Compilación

```
✅ Sin errores
✅ JAR generado: cliente/target/chat-cliente-1.0-SNAPSHOT.jar
```

---

**Implementado**: ✅ Completado  
**Fecha**: 6 de noviembre de 2025
