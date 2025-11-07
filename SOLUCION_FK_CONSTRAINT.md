# Solución: FK Constraint Violation en Mensajes Cross-Server

## Problema
Cuando un usuario envía audio a otro usuario en un servidor diferente, se genera un error de FK constraint:
```
Cannot add or update a child row: a foreign key constraint fails 
(chat_servidor.mensajes_log, CONSTRAINT mensajes_log_ibfk_2 
FOREIGN KEY (id_receptor) REFERENCES usuarios (id))
```

**Causa raíz:** El receptor remoto no está registrado en la base de datos local del servidor emisor.

## Solución Implementada

### 1. Modificación de Base de Datos
- **Columna afectada:** `mensajes_log.id_receptor`
- **Cambio:** Permitir valores `NULL` para mensajes cross-server
- **Migración automática:** Implementada en `ConexionBD.ensureColumnNullable()`
- **Migración manual:** Disponible en `migration_id_receptor_nullable.sql`

### 2. Validación en Código (MensajeLogDAO.java)
Agregado método `usuarioExiste()` que verifica si un usuario está registrado localmente:

```java
private boolean usuarioExiste(Connection conn, int idUsuario) {
    String sql = "SELECT 1 FROM usuarios WHERE id = ? LIMIT 1";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, idUsuario);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    } catch (SQLException e) {
        return false;
    }
}
```

**Lógica del método `registrar()`:**
- Si `id_emisor` o `id_receptor` > 0, verifica existencia con `usuarioExiste()`
- Si el usuario NO existe localmente, inserta `NULL` en lugar del ID
- Si el usuario SÍ existe, inserta el FK normalmente

### 3. Migración Automática (ConexionBD.java)
Nuevo método `ensureColumnNullable()`:
- Consulta `INFORMATION_SCHEMA.COLUMNS` para verificar si la columna permite NULL
- Si `IS_NULLABLE = 'NO'`, ejecuta `ALTER TABLE ... MODIFY COLUMN ... NULL`
- Se ejecuta automáticamente al iniciar el servidor

## Archivos Modificados

### servidor/src/main/java/com/universidad/chat/servidor/model/MensajeLogDAO.java
- ✅ Agregado método `usuarioExiste()` 
- ✅ Modificado método `registrar()` para validar FKs antes de insertar

### servidor/src/main/java/com/universidad/chat/servidor/config/ConexionBD.java
- ✅ Implementado método `ensureColumnNullable()` 
- ✅ Agregada migración automática para `id_receptor`
- ✅ Eliminadas llaves extra que causaban errores de sintaxis

### servidor/src/main/resources/db/migration_id_receptor_nullable.sql
- ✅ Script SQL manual para ejecutar la migración independientemente
- ✅ Incluye consultas de verificación antes/después

## Validación

### Build Status
```
[INFO] BUILD SUCCESS
[INFO] Total time:  2.691 s
```
✅ Compilación exitosa sin errores

### Comportamiento Esperado
1. **Mensaje local → local:** `id_receptor` se guarda con FK válido
2. **Mensaje local → remoto:** `id_receptor` se guarda como `NULL`
3. **Sin errores FK:** El sistema no falla al intentar guardar mensajes cross-server

## Pruebas Recomendadas

1. **Iniciar dos servidores** en máquinas diferentes
2. **Conectar un usuario** en cada servidor
3. **Enviar audio** desde Servidor A (usuario local) a Servidor B (usuario remoto)
4. **Verificar en ambas bases de datos:**
   - Servidor A: Registro en `mensajes_log` con evento `AUDIO_ENVIADO`, `id_emisor` válido, `id_receptor` NULL
   - Servidor B: Registro en `mensajes_log` con evento `AUDIO_RECIBIDO`, `id_emisor` NULL, `id_receptor` válido
5. **Sin errores:** Ambos servidores deben persistir el mensaje sin FK violations

## Migración Manual (Opcional)

Si prefieres ejecutar la migración manualmente sin reiniciar el servidor:

```bash
mysql -u usuario -p nombre_bd < servidor/src/main/resources/db/migration_id_receptor_nullable.sql
```

O directamente en MySQL:
```sql
ALTER TABLE mensajes_log MODIFY COLUMN id_receptor INT NULL;
```

## Próximos Pasos

1. ✅ Compilación exitosa
2. ⏳ Ejecutar servidor y verificar migración automática en logs
3. ⏳ Probar envío de audio cross-server
4. ⏳ Verificar persistencia en ambas bases de datos

## Notas Técnicas

- **Integridad referencial:** Se mantiene para usuarios locales
- **Mensajes cross-server:** Usan NULL para FKs de usuarios remotos
- **Retrocompatibilidad:** Los mensajes existentes no se afectan
- **Performance:** La validación `usuarioExiste()` es rápida (índice en PK)
