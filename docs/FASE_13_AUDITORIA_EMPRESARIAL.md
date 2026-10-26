# Fase 13 — Auditoría empresarial

Implementación para una instalación por empresa, sin multi-tenancy. Rama: `feature/auditoria-empresarial`.

## Arquitectura y transacciones

Los servicios de negocio invocan `AuditoriaService.registrar` dentro de su transacción existente. El servicio exige `Propagation.MANDATORY`: no funciona fuera de una transacción ni abre una transacción independiente. Inserta mediante el mismo `EntityManager` y no oculta errores. Un fallo al guardar el evento impide confirmar la operación; una reversión posterior revierte también el evento. No se utilizan tareas asíncronas, listeners `AFTER_COMMIT` ni `REQUIRES_NEW`.

El actor se obtiene del `SecurityContext`, se resuelve con `UsuarioRepository` y debe seguir activo. El cliente no puede enviar un actor arbitrario. La identidad legible se guarda como instantánea del correo; no hay asociaciones JPA ni claves foráneas a entidades de negocio que condicionen la conservación histórica. El filtro JWT existente sigue verificando firma, usuario activo, roles vigentes y sesión vigente.

Se mantienen los bloqueos pesimistas, las claves idempotentes y el orden FEFO existentes. La salida genera un evento por movimiento/lote. Una transferencia genera las salidas por lote, las entradas en destino y un evento resumen con origen, destino, producto, cantidad y lotes. Estos son eventos distintos previstos, confirmados juntos. Una repetición de la misma clave devuelve la respuesta existente antes de llegar a los puntos de auditoría.

## Persistencia y V4

`V4__crear_auditoria_empresarial.sql` crea únicamente `dbo.eventos_auditoria`:

| Columna | Tipo | Uso |
|---|---|---|
| id | bigint identity | Identificador del evento |
| fecha | datetimeoffset(7) | Instante UTC; CHECK exige offset cero |
| usuario_id | bigint | ID histórico del actor |
| usuario_identificador | varchar(254) | Correo del actor al registrar |
| accion | varchar(40) | Acción del catálogo de enums |
| entidad | varchar(30) | Tipo de entidad afectada |
| entidad_id | bigint | Referencia de negocio |
| almacen_id | bigint nullable | Almacén asociado; origen en el resumen de transferencia |
| descripcion | nvarchar(200) | Texto fijo de la acción |
| detalles | nvarchar(max) nullable | JSON de campos seleccionados; CHECK `ISJSON` |

Cinco índices permiten consultar fecha, usuario, acción, entidad/ID y almacén; todos terminan en fecha e ID descendentes. Los IDs deben ser positivos. No modifica V1–V3 ni tablas existentes. La migración falla si encuentra una tabla previa sin esta versión registrada.

`EventoAuditoria` es `@Immutable`, no tiene setters, sus columnas no se actualizan y `@PreRemove` rechaza el borrado. El repositorio solo expone consultas, sin `save`, `delete` ni operaciones masivas. V4 añade `DENY UPDATE, DELETE` sobre esta tabla a `public`: protege cuentas normales incluso si pertenecen a `db_datawriter`. Un propietario de la base o `sysadmin` conserva privilegios administrativos; esta solución no pretende ser un registro criptográfico resistente al administrador de SQL Server. La cuenta de aplicación debe tener SELECT/INSERT, sin privilegios de propietario/DDL. CI comprueba estos permisos efectivos.

**No se aplica V4 a ninguna base local durante esta fase.** La aplicación de esta rama requiere V4 antes de iniciarse con `ddl-auto=validate`. Las migraciones automáticas siguen desactivadas por defecto en dev/test. No iniciar esta rama contra una base que todavía tenga V3: Hibernate rechazará la tabla faltante. Para validar sin modificar bases locales, usar el contenedor de GitHub Actions. La aplicación futura a una base existente requiere verificar destino, respaldo y autorización específica; no está incluida en esta publicación.

## Operaciones registradas

| Operación | Eventos / detalles |
|---|---|
| Productos | Creación, actualización con valores seleccionados antes/después (código, nombre, categoría, precios, estado), desactivación |
| Entradas | Un evento por movimiento; producto, lote, cantidad y almacén; entidad_id referencia el movimiento |
| Salidas FEFO | Un evento por lote retirado; referencias reales de movimiento |
| Transferencias | Movimientos origen/destino y resumen con transferenciaId, almacenes, cantidades y lotes |
| Usuarios | Creación, activación/desactivación y roles antes/después |
| Categorías | Creación y actualización con nombre anterior/posterior |
| Proveedores | Creación, actualización de razón social y desactivación; sin contactos ni RUC en detalles |
| Almacenes | Creación, actualización de código/nombre y desactivación; sin copiar ubicación |
| Lotes | Creación con referencia al producto y fecha de vencimiento |

Desactivar una entidad ya inactiva, repetir un estado y reasignar exactamente los mismos roles no genera eventos adicionales. No se crean operaciones de negocio inexistentes.

`DetalleAuditoria` es una interfaz sellada con records específicos; no acepta mapas arbitrarios ni DTOs de solicitudes. No se registran contraseñas, hashes, JWT, refresh tokens, credenciales SQL, headers, observaciones, descripciones libres de productos o cuerpos HTTP completos. Los códigos y nombres de catálogos son campos de negocio seleccionados; deben usarse para datos de negocio, nunca para secretos.

## API protegida

Solo ADMIN puede acceder. Los demás roles reciben 403, y una autenticación ausente/inválida recibe 401. Incluso ADMIN tiene denegados POST/PUT/PATCH/DELETE en estas rutas. No existen endpoints de escritura.

- `GET /api/auditoria`: `page=0`, `size=20`, máximo 100; orden fijo `fecha DESC, id DESC`.
- Filtros opcionales: `usuarioId`, `accion`, `entidad`, `entidadId`, `almacenId`, `desde`, `hasta`. Se combinan con AND. Fechas ISO-8601 con offset, límites inclusivos, rango ordenado y años admitidos por SQL Server. Para `+00:00`, codificar `+` como `%2B`; también se acepta `Z`.
- `GET /api/auditoria/{id}`: detalle; 404 si no existe, 400 para un ID no positivo.
- Filtros inválidos o tamaños fuera de 1–100 se rechazan con 400. No se admite orden arbitrario.

Ejemplo: `GET /api/auditoria?usuarioId=7&accion=ENTRADA_REGISTRADA&desde=2026-10-01T00:00:00Z&size=20`.

```json
{
  "eventos": [{
    "id": 120,
    "fecha": "2026-10-26T02:00:00Z",
    "usuarioId": 7,
    "usuarioIdentificador": "operador@example.invalid",
    "accion": "ENTRADA_REGISTRADA",
    "entidad": "MOVIMIENTO_INVENTARIO",
    "entidadId": 81,
    "almacenId": 2,
    "descripcion": "Entrada de inventario registrada",
    "detalles": {"productoId": 9, "loteId": 18, "cantidad": 25}
  }],
  "totalElementos": 1,
  "pagina": 0,
  "tamanio": 20,
  "totalPaginas": 1
}
```

## Pruebas y CI

Las pruebas anteriores se conservan; tres unitarias solo adaptan la nueva dependencia del constructor. Se agregan `AuditoriaServiceTest`, `AuditoriaHttpTest` y `AuditoriaSqlServerTest`.

La integración real comprueba entradas, FEFO, transferencia, reintentos idempotentes, reversión después de insertar realmente, error SQL al insertar auditoría, fallo obligatorio al final de una transferencia, cambios de roles/estado, catálogos, filtros y páginas, protección del historial, permisos efectivos y carreras de inventario e idempotencia. Cada escenario utiliza fixtures exclusivos, verifica `DB_NAME()=LM_Medical_Test`, y en CI verifica además el puerto efímero, nombre del contenedor y marcador de propiedad. No elimina fixtures ni datos existentes. No sustituye SQL Server por H2.

El perfil Maven `security-sqlserver` incorpora la nueva suite y conserva `SecuritySqlServerTest`. CI prepara V1–V4 con `target=4`, exige cuatro migraciones SQL exitosas y 16 tablas de negocio. La cuenta de pruebas conserva permisos limitados. El contrato `scripts/ci/test-plan.json` exige los casos nuevos además de todos los anteriores; omisiones, fallos y pruebas saltadas hacen fallar el pipeline. Los artefactos continúan saneados, sin propiedades, logs ni stacks con datos de ejecución.

Ejecución local sin SQL:

```powershell
$env:JAVA_HOME = '<ruta a JDK 21>'
.\mvnw.cmd -B --no-transfer-progress test
python -m unittest discover -s scripts/ci/tests -v
```

Ejecución aislada completa: abrir una pull request de esta rama a main, o seleccionar la rama en **Actions → Backend CI → Run workflow**. No ejecutar manualmente los scripts de preparación CI contra SQL Server local. Para una ejecución manual del perfil SQL local debe existir previamente V4 en `LM_Medical_Test`, con credenciales externas y autorización para fixtures; no activa Flyway automáticamente.

Fuentes de diseño: [propagación transaccional de Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).

## Evidencia de validación

Validación local con Java 21: **179 pruebas unitarias/MockMvc aprobadas**, sin fallos ni omisiones (153 anteriores y 26 nuevas). **16 pruebas Python de los controles CI aprobadas**. Ambos workflows validados con actionlint; sintaxis Bash verificada. La revisión Gitleaks de candidatos e historial y el cotejo con secretos locales conocidos no encontraron exposiciones. V1–V3 y la configuración de perfiles permanecen intactos.

La suite SQL prevista comprende **55 casos**: 37 anteriores de seguridad y 18 nuevos de auditoría, incluidos tres escenarios de competencia por stock y uno de idempotencia concurrente. Su resultado real se documentará después de ejecutar el contenedor temporal en la pull request; compilar esas pruebas localmente no demuestra su aprobación.

## Alcance pendiente deliberado

No incluye auditoría de intentos fallidos, login/logout, el bootstrap inicial sin un actor autenticado, trabajos automáticos, exportación, retención/purga, firmas criptográficas ni acciones SQL realizadas fuera de la aplicación. No reconstruye eventos históricos anteriores a V4. No realiza despliegues ni merge a main.
