# 🐛 ANÁLISIS DEL ERROR: Conflicto de IDs en Canal Multi-Servidor

## 📋 Descripción del Problema

### Escenario:
1. **Servidor A** (8080) tiene al **Usuario 1** conectado
2. **Servidor B** (8081) tiene al **Usuario 2** conectado  
3. Usuario 1 crea un **Canal** e invita a Usuario 2
4. Usuario 2 **acepta** la invitación

### ❌ Problema Actual:
Cuando un usuario acepta la invitación, el sistema guarda en la tabla `canal_usuarios`:
- ✅ `id_canal` 
- ✅ `id_usuario`
- ❌ **NO diferencia usuarios con el mismo ID de diferentes servidores**

### 🔥 El Error Crítico:

La tabla `canal_usuarios` tiene esta estructura:

```sql
CREATE TABLE canal_usuarios (
    id_canal INT NOT NULL,
    id_usuario INT NOT NULL,
    usuario_servidor_host VARCHAR(255) DEFAULT NULL,
    usuario_servidor_puerto INT DEFAULT NULL,
    aceptado BOOLEAN DEFAULT FALSE,
    fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_canal, id_usuario),  -- ⚠️ AQUÍ ESTÁ EL PROBLEMA
    FOREIGN KEY (id_canal) REFERENCES canales(id),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
);
```

**La clave primaria es:** `PRIMARY KEY (id_canal, id_usuario)`

**Esto significa:**
- Si Usuario ID=1 del Servidor A se une al Canal 5
- Y Usuario ID=1 del Servidor B también intenta unirse al Canal 5
- **💥 COLISIÓN:** Solo uno puede existir en la BD

### 🎯 Impacto:

1. **Usuarios con el mismo ID de diferentes servidores no pueden estar en el mismo canal**
2. **El último en aceptar sobrescribe al anterior**
3. **Se pierde información de miembros del canal**
4. **Los mensajes no llegan a todos los miembros correctos**

---

## ✅ LA SOLUCIÓN

### Opción 1: Modificar la Clave Primaria (RECOMENDADA)

Cambiar la tabla para que la clave primaria incluya el servidor:

```sql
-- 1. Eliminar la FK que depende de la PK
ALTER TABLE canal_usuarios DROP FOREIGN KEY canal_usuarios_ibfk_1;
ALTER TABLE canal_usuarios DROP FOREIGN KEY canal_usuarios_ibfk_2;

-- 2. Eliminar la PK actual
ALTER TABLE canal_usuarios DROP PRIMARY KEY;

-- 3. Agregar columna ID autoincremental
ALTER TABLE canal_usuarios ADD COLUMN id INT PRIMARY KEY AUTO_INCREMENT FIRST;

-- 4. Crear índice único que considere el servidor
ALTER TABLE canal_usuarios ADD UNIQUE KEY uk_canal_usuario_servidor (
    id_canal, 
    id_usuario, 
    usuario_servidor_host(191),  -- Limitar longitud para el índice
    usuario_servidor_puerto
);

-- 5. Restaurar FKs
ALTER TABLE canal_usuarios 
    ADD FOREIGN KEY fk_canal_usuarios_canal (id_canal) REFERENCES canales(id);
    
ALTER TABLE canal_usuarios 
    ADD FOREIGN KEY fk_canal_usuarios_usuario (id_usuario) REFERENCES usuarios(id);
```

### Opción 2: Clave Compuesta Completa (Alternativa)

```sql
ALTER TABLE canal_usuarios DROP FOREIGN KEY canal_usuarios_ibfk_1;
ALTER TABLE canal_usuarios DROP FOREIGN KEY canal_usuarios_ibfk_2;
ALTER TABLE canal_usuarios DROP PRIMARY KEY;

-- PK compuesta que incluye servidor (permite NULL para usuarios locales)
ALTER TABLE canal_usuarios ADD PRIMARY KEY (
    id_canal, 
    id_usuario,
    IFNULL(usuario_servidor_host, ''),
    IFNULL(usuario_servidor_puerto, 0)
);
```

---

## 📝 Archivos a Modificar

### 1. `ConexionBD.java`

Actualizar el DDL de creación de tabla y agregar migración:

**Ubicación:** `servidor/src/main/java/com/universidad/chat/servidor/config/ConexionBD.java`

**Cambios necesarios:**

```java
// LÍNEA ~149: Modificar creación de tabla
String crearCanalUsuarios = """
    CREATE TABLE IF NOT EXISTS canal_usuarios (
        id INT PRIMARY KEY AUTO_INCREMENT,
        id_canal INT NOT NULL,
        id_usuario INT NOT NULL,
        usuario_servidor_host VARCHAR(255) DEFAULT NULL,
        usuario_servidor_puerto INT DEFAULT NULL,
        aceptado BOOLEAN DEFAULT FALSE,
        fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uk_canal_usuario_servidor (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto),
        FOREIGN KEY (id_canal) REFERENCES canales(id),
        FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
    )
    """;

// LÍNEA ~260: Agregar migración en verificarYMigrarEsquema()
try {
    // Migración: Modificar PK de canal_usuarios para soportar usuarios multi-servidor
    migrarPrimariaKeyCanal usuarios(conn);
    logger.info("Migración de PK de canal_usuarios completada");
} catch (Exception e) {
    logger.warn("No se pudo migrar PK de canal_usuarios: {}", e.getMessage());
}
```

**Agregar nuevo método:**

```java
/**
 * Migrar la clave primaria de canal_usuarios para soportar usuarios multi-servidor.
 */
private void migrarPrimaryKeyCanalUsuarios(Connection conn) throws SQLException {
    // Verificar si ya tiene la estructura nueva (columna 'id')
    if (hasColumn(conn, "canal_usuarios", "id")) {
        logger.debug("La tabla canal_usuarios ya tiene la columna 'id', migración no necesaria");
        return;
    }
    
    logger.info("Iniciando migración de tabla canal_usuarios...");
    
    try (var stmt = conn.createStatement()) {
        // 1. Eliminar FKs existentes
        dropAllForeignKeysForTable(conn, "canal_usuarios");
        
        // 2. Eliminar PK actual
        stmt.execute("ALTER TABLE canal_usuarios DROP PRIMARY KEY");
        
        // 3. Agregar columna ID
        stmt.execute("ALTER TABLE canal_usuarios ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST");
        
        // 4. Crear índice único con servidor
        stmt.execute("""
            ALTER TABLE canal_usuarios ADD UNIQUE KEY uk_canal_usuario_servidor (
                id_canal, 
                id_usuario, 
                usuario_servidor_host, 
                usuario_servidor_puerto
            )
            """);
        
        // 5. Restaurar FKs
        stmt.execute("ALTER TABLE canal_usuarios ADD FOREIGN KEY (id_canal) REFERENCES canales(id)");
        stmt.execute("ALTER TABLE canal_usuarios ADD FOREIGN KEY (id_usuario) REFERENCES usuarios(id)");
        
        logger.info("✓ Migración de canal_usuarios completada exitosamente");
    }
}

/**
 * Verificar si una columna existe en una tabla.
 */
private boolean hasColumn(Connection conn, String tableName, String columnName) {
    String query = """
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() 
        AND TABLE_NAME = ? 
        AND COLUMN_NAME = ?
        """;
    
    try (var ps = conn.prepareStatement(query)) {
        ps.setString(1, tableName);
        ps.setString(2, columnName);
        try (var rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    } catch (Exception e) {
        logger.debug("Error verificando columna {}.{}: {}", tableName, columnName, e.getMessage());
        return false;
    }
}
```

### 2. `CanalDAO.java` - Verificar Queries

**Ubicación:** `servidor/src/main/java/com/universidad/chat/servidor/model/CanalDAO.java`

Los queries ya están correctos porque ya usan las columnas `usuario_servidor_host` y `usuario_servidor_puerto` en los WHERE. Solo verificar que **todos los métodos** las incluyan.

**Verificar especialmente:**
- ✅ `agregarUsuarioAceptado()` - Ya correcto
- ✅ `esMiembroAceptadoConServidor()` - Ya correcto  
- ✅ `existeRelacionConServidor()` - Ya correcto
- ✅ `obtenerMiembrosConServidor()` - Ya correcto

---

## 🧪 Prueba del Fix

### Script de Prueba SQL

```sql
-- Verificar estructura actual
DESCRIBE canal_usuarios;

-- Probar insertar usuarios con mismo ID de diferentes servidores
INSERT INTO canal_usuarios (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado) 
VALUES (1, 1, 'localhost', 8080, TRUE);

INSERT INTO canal_usuarios (id_canal, id_usuario, usuario_servidor_host, usuario_servidor_puerto, aceptado) 
VALUES (1, 1, 'localhost', 8081, TRUE);

-- Verificar que ambos existen
SELECT * FROM canal_usuarios WHERE id_canal = 1 AND id_usuario = 1;
-- Debería mostrar 2 filas (una para cada servidor)
```

### Prueba Funcional

1. Iniciar Servidor A (puerto 8080)
2. Iniciar Servidor B (puerto 8081)
3. Conectar Usuario 1 a Servidor A
4. Conectar Usuario 2 a Servidor B
5. Usuario 1 crea canal "Test Multi-Server"
6. Usuario 1 invita a Usuario 2
7. Usuario 2 acepta
8. ✅ Verificar en BD que ambos usuarios están en el canal
9. ✅ Enviar mensaje en el canal y verificar que ambos lo reciben

---

## 📊 Impacto y Consideraciones

### ✅ Beneficios:
- Usuarios con mismo ID de diferentes servidores pueden coexistir en canales
- Escalabilidad mejorada
- No se pierden miembros del canal
- Mensajes llegan a todos los miembros correctos

### ⚠️ Consideraciones:
- **Requiere migración de datos existentes**
- **Reiniciar todos los servidores después del cambio**
- **Limpiar datos de prueba inconsistentes** antes de aplicar

### 🔄 Plan de Migración:

1. **Backup de BD:**
   ```bash
   mysqldump -u root -p chat_servidor_1 > backup_antes_migracion.sql
   ```

2. **Aplicar cambios a ConexionBD.java**

3. **Reiniciar servidor** (la migración se ejecuta automáticamente)

4. **Verificar logs** para confirmar migración exitosa

5. **Probar funcionalidad** con múltiples servidores

---

## 🔗 Referencias

- Tabla afectada: `canal_usuarios`
- Archivos modificados:
  - `servidor/src/main/java/com/universidad/chat/servidor/config/ConexionBD.java`
  - Verificar: `servidor/src/main/java/com/universidad/chat/servidor/model/CanalDAO.java`
  - Verificar: `servidor/src/main/java/com/universidad/chat/servidor/service/CanalService.java`

---

**Fecha de análisis:** Noviembre 7, 2025  
**Estado:** ⚠️ Bug crítico identificado - Requiere fix inmediato  
**Prioridad:** 🔴 ALTA
