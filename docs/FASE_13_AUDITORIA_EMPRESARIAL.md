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

Ejecución aislada completa: hacer push a una rama `feature/**`, abrir una pull request a main, o seleccionar la rama en **Actions → Backend CI → Run workflow**. No ejecutar manualmente los scripts de preparación CI contra SQL Server local. Para una ejecución manual del perfil SQL local debe existir previamente V4 en `LM_Medical_Test`, con credenciales externas y autorización para fixtures; no activa Flyway automáticamente.

Fuentes de diseño: [propagación transaccional de Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).

## Evidencia de validación

Validación local con Java 21: **179 pruebas unitarias/MockMvc aprobadas**, sin fallos ni omisiones (153 anteriores y 26 nuevas). **17 pruebas Python de los controles CI aprobadas**. Ambos workflows validados con actionlint; sintaxis Bash verificada. La revisión Gitleaks de candidatos e historial y el cotejo con secretos locales conocidos no encontraron exposiciones. V1–V3 y la configuración de perfiles permanecen intactos.

La ejecución real [Backend CI 40449391425](https://github.com/Keiner0624/inventory-backend/actions/runs/40449391425), commit `5629994a77dbca2722d5e4e73bce8dfd36b665d9`, terminó **success**. Los tres jobs aprobaron: unitarias/MockMvc, SQL Server aislado y barrera obligatoria.

- **179 pruebas Java unitarias/MockMvc aprobadas**, sin fallos, errores ni omisiones.
- **55 pruebas reales SQL Server aprobadas**, sin fallos, errores ni omisiones: 37 anteriores de seguridad y 18 de auditoría. El contrato confirmó todos los casos requeridos, incluidos tres escenarios de competencia por stock, una carrera de idempotencia y las tres carreras anteriores de refresh.
- **17 pruebas Python de infraestructura aprobadas**; el caso nuevo verifica que los diagnósticos no publican mensajes ni datos sensibles.
- Flyway verificó V1–V4 y las 16 tablas de negocio en la base efímera. Las pruebas verificaron `version=4`, `type=SQL`, `success=1`, permisos de solo inserción/consulta en auditoría y reversión real de escrituras.
- La eliminación del contenedor y base temporales terminó correctamente. Ninguna base local fue modificada.

La primera ejecución SQL detectó tres problemas de montaje de las pruebas: la preparación del spy atravesaba el proxy transaccional MANDATORY fuera de una transacción, y el contexto de autenticación preparado para llamadas de servicio contaminaba el escenario HTTP. Se configura el spy sobre su objeto destino conservando el proxy durante la operación real, y se limpia el contexto antes del login HTTP. No se modificó producción para resolver estos fallos, ni se eliminaron assertions o escenarios. Las pruebas de rollback provocan escrituras/errores SQL reales dentro de la misma transacción.

Los reportes públicos mantienen ocultos mensajes y stacks. En caso de fallo solo incluyen nombres de excepciones de una lista permitida y líneas del archivo de la prueba ya identificado; no vuelcan contenido de solicitudes ni credenciales.

## Alcance pendiente deliberado

No incluye auditoría de intentos fallidos, login/logout, el bootstrap inicial sin un actor autenticado, trabajos automáticos, exportación, retención/purga, firmas criptográficas ni acciones SQL realizadas fuera de la aplicación. No reconstruye eventos históricos anteriores a V4. No realiza despliegues ni merge a main.

El workflow Backend CI también se ejecuta en pushes a ramas feature/**. Esto permite validar ramas para revisión aunque la integración de GitHub no tenga permiso para crear pull requests (403 observado). No modifica permisos, credenciales, controles de pruebas ni la rama main.

## Inventario de archivos entregados

Creados (15):

- `docs/FASE_13_AUDITORIA_EMPRESARIAL.md`
- `src/main/java/com/lmmedical/inventory/controller/AuditoriaController.java`
- `src/main/java/com/lmmedical/inventory/dto/auditoria/AuditoriaPaginaResponse.java`
- `src/main/java/com/lmmedical/inventory/dto/auditoria/DetalleAuditoria.java`
- `src/main/java/com/lmmedical/inventory/dto/auditoria/EventoAuditoriaResponse.java`
- `src/main/java/com/lmmedical/inventory/entity/EventoAuditoria.java`
- `src/main/java/com/lmmedical/inventory/enums/AccionAuditoria.java`
- `src/main/java/com/lmmedical/inventory/enums/EntidadAuditoria.java`
- `src/main/java/com/lmmedical/inventory/repository/EventoAuditoriaRepository.java`
- `src/main/java/com/lmmedical/inventory/service/AuditoriaConsultaService.java`
- `src/main/java/com/lmmedical/inventory/service/AuditoriaService.java`
- `src/main/resources/db/migration/V4__crear_auditoria_empresarial.sql`
- `src/test/java/com/lmmedical/inventory/controller/AuditoriaHttpTest.java`
- `src/test/java/com/lmmedical/inventory/integration/AuditoriaSqlServerTest.java`
- `src/test/java/com/lmmedical/inventory/service/AuditoriaServiceTest.java`

Modificados (19):

- `.github/workflows/backend-ci.yml`
- `pom.xml`
- `scripts/ci/reports.py`
- `scripts/ci/sqlserver.sh`
- `scripts/ci/test-plan.json`
- `scripts/ci/tests/test_ci_tools.py`
- `src/main/java/com/lmmedical/inventory/security/SecurityConfig.java`
- `src/main/java/com/lmmedical/inventory/service/AlmacenService.java`
- `src/main/java/com/lmmedical/inventory/service/CategoryService.java`
- `src/main/java/com/lmmedical/inventory/service/InventarioService.java`
- `src/main/java/com/lmmedical/inventory/service/LoteService.java`
- `src/main/java/com/lmmedical/inventory/service/ProductService.java`
- `src/main/java/com/lmmedical/inventory/service/ProveedorService.java`
- `src/main/java/com/lmmedical/inventory/service/SalidaInventarioService.java`
- `src/main/java/com/lmmedical/inventory/service/TransferenciaService.java`
- `src/main/java/com/lmmedical/inventory/service/UsuarioService.java`
- `src/test/java/com/lmmedical/inventory/service/SalidaInventarioServiceTest.java`
- `src/test/java/com/lmmedical/inventory/service/UsuarioAdministracionTest.java`
- `src/test/java/com/lmmedical/inventory/service/UsuarioServiceRolesTest.java`

Rama publicada: [feature/auditoria-empresarial](https://github.com/Keiner0624/inventory-backend/tree/feature/auditoria-empresarial). La integración no permitió abrir la PR (403); puede abrirse desde [la comparación con main](https://github.com/Keiner0624/inventory-backend/compare/main...feature/auditoria-empresarial). No se hizo merge.
