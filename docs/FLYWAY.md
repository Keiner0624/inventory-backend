# Flyway: configuración y verificación

## V3 aplicada en Test y desarrollo: estado actual

El 18 de octubre de 2026 el propietario autorizó expresamente V3 en ambas bases y confirmó respaldos recientes y verificados: «Ambas bases; respaldos verificados». Se aplicó primero en `LM_Medical_Test`; después de verificar historial, datos y arranque, se aplicó en `LM_Medical_supplies`.

Antes de ejecutar se verificaron el destino real con las mismas credenciales SQL del plugin, los permisos, los hashes auditados de V1/V2/V3 y la ausencia de objetos de seguridad previos. Ambas tenían BASELINE 1 y V2 correctos; V3 era la única pendiente. No se repitió baseline ni se utilizó repair.

Se ejecutó `flyway:migrate` con `target=3`, `validateOnMigrate=true`, `baselineOnMigrate=false` y `cleanDisabled=true`, seguido de `flyway:validate` y `flyway:info`. Se aplicó exactamente una migración por base. El historial independiente confirma:

| Base | Versión / tipo / resultado | Checksum | Fecha de registro (Lima) |
|---|---|---:|---|
| `LM_Medical_Test` | 3 / SQL / success=1 | -1733036323 | 2026-10-18 18:51:39.870 |
| `LM_Medical_supplies` | 3 / SQL / success=1 | -1733036323 | 2026-10-18 18:52:35.287 |

En ambas, `flyway:info` muestra versión actual 3, BASELINE 1 conservado, V1 Ignored (Baseline), V2 y V3 Success, sin pendientes. El esquema nuevo coincide con el probado en la base temporal: usuarios, roles, usuario_rol y sesiones_refresh. Cada base tiene 15 tablas de aplicación, tres roles iniciales y cero usuarios, asignaciones y sesiones.

Se compararon antes/después los SHA-256 y recuentos de datos ordenados por clave, identificadores y fechas de las tablas originales y todas sus definiciones de columnas, claves, restricciones e índices: **las 11 tablas y 59 columnas de inventario permanecen intactas**, con 0 filas en Test y las mismas 26 en desarrollo. Los registros anteriores de Flyway también se conservaron sin cambios.

`InventoryBackendApplicationTests.contextLoads` pasó 1/1 en test y 1/1 en dev con sus destinos fijos, Hibernate validate, Flyway desactivado y SQL init desactivado. Esta comprobación no insertó usuarios ni datos de inventario y no ejecutó la suite completa ni pruebas HTTP. La incompatibilidad previa por ausencia de las cuatro tablas de seguridad queda resuelta en ambas bases.

No se cambiaron código Java, POM, properties, configuración de seguridad ni archivos de migración. V1, V2 y V3 mantienen sus hashes auditados. Las migraciones automáticas continúan desactivadas en dev y test. No se ejecutaron operaciones de limpieza ni se creó un administrador. Login, JWT y permisos siguen pendientes de la siguiente fase.

Evidencias por destino, sin credenciales ni contenido de filas:

- `LM_Medical_Test`: `target/flyway-v3-application/LM_Medical_Test/20261018-185052-203/`.
- `LM_Medical_supplies`: `target/flyway-v3-application/LM_Medical_supplies/20261018-185050-465/`.

Incluyen autorización, hashes, snapshots antes/después, logs de Flyway/Hibernate, consulta SQL independiente, resultado de prueba de arranque y verification.json. La base temporal ya tenía V3 y no se conectó ni modificó en esta aplicación. Los avisos existentes de prefijos Maven HTTP 401 y del agente Mockito no impidieron los BUILD SUCCESS.

Las verificaciones iniciales siguientes describen el estado anterior a esta aplicación autorizada.



## Fase 12: verificación inicial de V3, antes de aplicarla en las bases principales

El 18 de octubre de 2026 se implementó `V3__usuarios_roles_seguridad.sql` y se aplicó exclusivamente en `LM_Medical_Flyway_Prueba`, después de verificar V2. Historial independiente: version=3, type=SQL, success=1, checksum=-1733036323, installed_on=2026-10-18 18:43:23.913. `flyway:validate` correcto; `flyway:info` muestra V1, V2 y V3 Success, sin pendientes. Las 57 pruebas seleccionadas pasaron, incluidas restricciones SQL, JPA/Hibernate validate y regresión de inventario con rollback.

`LM_Medical_Test` y `LM_Medical_supplies` conservan BASELINE 1 y V2; no se ejecutó V3 en ellas. Se compararon antes/después sus datos, estructura e historial: idénticos (0 y 26 filas de negocio, respectivamente). Dev y test mantienen Flyway desactivado por defecto. POM, properties, SecurityConfig, V1 y V2 conservan sus hashes.

**El código con las nuevas entidades necesita V3 para arrancar con Hibernate validate. No iniciar esta nueva versión contra las bases principales hasta autorizar y aplicar V3 por separado.** Los arranques satisfactorios descritos en las secciones históricas siguientes corresponden al código anterior a estas entidades.

V3 crea usuarios, roles, usuario_rol y sesiones_refresh, e inserta solo tres roles; no crea usuarios ni implementa autenticación. Ver [SEGURIDAD.md](SEGURIDAD.md) para el modelo, archivos, pruebas, diseño de autenticación y estado actual. Evidencias en `target/security-v3-verification/`.

Las secciones siguientes registran intervenciones anteriores y no sustituyen este estado actual.


Revisión realizada el 18 de octubre de 2026, hora de Lima.

## Prueba pendiente de Existencia: resuelta

En una tarea posterior a V2, el 18 de octubre de 2026, se corrigió `ExistenciaTest.noDebePermitirRetirarMasDelStockDisponible`. Se reprodujo primero el fallo: esperaba IllegalStateException, mientras la entidad rechazaba correctamente el argumento con IllegalArgumentException. Se ajustó únicamente el tipo esperado en la prueba y se conservó la comprobación de que el stock permanece en 100 tras solicitar retirar 150. La implementación de Existencia y el manejo existente de la API (HTTP 409 para IllegalArgumentException) no cambiaron.

Verificación con Java 21: `ExistenciaTest` 6/6, `SelectorLotesFefoTest` 5/5 y `SalidaInventarioServiceTest` 1/1; 12 pruebas, cero fallos ni errores, BUILD SUCCESS. No se conectó a bases de datos ni se ejecutó Flyway. No se ejecutó la suite completa de integración. Las referencias posteriores al fallo de Existencia describen resultados históricos anteriores a esta corrección.

Comando: `.\mvnw.cmd -B test '-Dtest=ExistenciaTest,SelectorLotesFefoTest,SalidaInventarioServiceTest'`.

## V2 aplicada en LM_Medical_supplies: verificación más reciente

El propietario solicitó aplicar V2 exclusivamente en `LM_Medical_supplies` y confirmó un respaldo reciente y verificado. Se ejecutó el 18 de octubre de 2026, hora de Lima, con target=2 y sin activar migraciones automáticas.

### Resultado comprobado

- Destino real comprobado con la misma cuenta SQL de Maven: `jdbc:sqlserver://localhost:1433;databaseName=LM_Medical_supplies;encrypt=true;trustServerCertificate=true`.
- Antes: BASELINE 1 correcto y V2 como única pendiente; sin fallos ni datos incompatibles. Se verificaron los hashes auditados, permisos y las definiciones de las cuatro restricciones ya existentes.
- `flyway:migrate`, con `target=2`, `validateOnMigrate=true`, `baselineOnMigrate=false` y `cleanDisabled=true`: se aplicó una sola migración, V2. `flyway:validate` posterior correcto y `flyway:info` en versión 2, Success, sin pendientes. Resultado `BUILD SUCCESS`.
- Consulta independiente: `installed_rank=2`, `version=2`, `type=SQL`, `script=V2__restricciones_integridad.sql`, `success=1`, checksum `-735586558`, fecha `2026-10-18 17:42:30.713`.
- V2 reconoció `UQ_transferencia_lote`, `CK_detalle_cantidad`, `CK_detalle_stock` y `CK_operacion_cantidad`. No se crearon duplicados ni se alteraron las restricciones existentes. Los CHECK están habilitados y confiables, sin NOT FOR REPLICATION; el índice UNIQUE mantiene exactamente sus dos columnas clave, está habilitado, no tiene filtro ni IGNORE_DUP_KEY.
- **Esquema de negocio idéntico antes/después**: 11 tablas, 59 columnas, 11 PK, 8 UNIQUE, 15 FK, 11 CHECK y 5 DEFAULT. Se conservaron identificadores y fechas de las tablas, nombres y definiciones de columnas y restricciones e índices. No se eliminó ni recreó ningún objeto de negocio.
- **Las 26 filas de negocio se conservaron**; coinciden todos los recuentos y SHA-256 por tabla, después también del arranque de verificación. No se ejecutó DML sobre datos de negocio.
- El registro BASELINE 1 conserva todos sus valores, incluida su fecha. No se repitió baseline ni se utilizó repair en esta base.
- `InventoryBackendApplicationTests.contextLoads` con perfil dev, URL fija a principal, Hibernate validate, Flyway desactivado y SQL init desactivado: **1/1 correcta**. No se ejecutaron pruebas que insertan o eliminan datos.
- V1 y V2 mantienen sus hashes auditados. No se cambiaron código, POM, properties ni scripts SQL. Solo se actualizó este documento y se guardaron evidencias locales.

Evidencias sin credenciales ni contenido de filas en `target/flyway-v2-supplies-application/20261018-174008-163/`: autorización, snapshots de datos/metadatos antes y después, consulta SQL, logs de Flyway y Hibernate y `verification.json`.

### Estado de migraciones según las verificaciones registradas

| Base | Historial |
|---|---|
| `LM_Medical_supplies` | BASELINE 1 conservado y V2 SQL success=1, comprobados en esta intervención |
| `LM_Medical_Test` | BASELINE 1 conservado y V2 SQL success=1, comprobados en la intervención anterior |
| `LM_Medical_Flyway_Prueba` | V1 SQL y V2 SQL success=1, comprobados durante la implementación |
| `LM_Medical_Flyway_V2_Equivalentes` | V1 SQL y V2 SQL success=1, comprobados durante las pruebas de reglas existentes |

**En esta intervención solo se conectó y modificó LM_Medical_supplies.** Las migraciones automáticas continúan desactivadas en dev y test. Ambas bases existentes tienen V2 registrada; cualquier versión posterior requiere una autorización nueva. Las secciones siguientes conservan la evidencia histórica de las intervenciones previas.

## V2 aplicada en LM_Medical_Test: intervención anterior

El propietario confirmó un respaldo reciente y verificado y autorizó expresamente **solo V2 en LM_Medical_Test, target=2**. Se ejecutó el 18 de octubre de 2026, hora de Lima, sin activar Flyway automático en dev/test.

### Resultado comprobado

- Destino confirmado con la misma cuenta SQL usada por Maven: `jdbc:sqlserver://localhost:1433;databaseName=LM_Medical_Test;encrypt=true;trustServerCertificate=true`.
- Antes: BASELINE 1 correcto, sin fallos; V2 como única pendiente. Se confirmaron permisos, hashes de V1/V2 y ausencia de las cuatro incompatibilidades de datos.
- Se ejecutó `flyway:info`, `flyway:migrate`, `flyway:validate` y `flyway:info`, con target=2, esquema dbo, `validateOnMigrate=true`, `baselineOnMigrate=false` y `cleanDisabled=true`. Solo se ejecutó una migración: V2.
- `migrate` y la validación posterior terminaron con `BUILD SUCCESS`; `info` muestra versión 2, estado Success y ninguna migración pendiente.
- Una consulta independiente confirmó `installed_rank=2`, `version=2`, `type=SQL`, `success=1`, checksum `-735586558`, fecha `2026-10-18 17:28:02.540`.
- El registro BASELINE 1 conserva todos sus valores, incluida su fecha. No se repitió baseline ni se utilizó repair en Test.
- Se incorporaron `UQ_transferencia_lote`, `CK_detalle_cantidad`, `CK_detalle_stock` y `CK_operacion_cantidad`. Los CHECK están habilitados, confiables y sin NOT FOR REPLICATION. La UNIQUE es no filtrada, está habilitada, tiene IGNORE_DUP_KEY=OFF y sus columnas clave son transferencia_id y lote_id.
- Las **11 tablas y 59 columnas originales** mantienen identificadores, fechas de creación, tipos y nulabilidad. Se conservaron las PK, FK, DEFAULT, CHECK, UNIQUE e índices preexistentes. Se agregó una UNIQUE y tres CHECK; las fechas de modificación de sus tres tablas reflejan el DDL esperado.
- Las 11 tablas tenían **0 filas antes** y mantienen **0 después**. Coinciden los SHA-256 de datos por tabla. No se ejecutó DML sobre datos de negocio ni se eliminaron tablas o columnas.
- `InventoryBackendApplicationTests.contextLoads`, perfil test y conexión fija a Test: **1/1 correcta**, Hibernate validate, Flyway desactivado y SQL init desactivado. No se ejecutaron pruebas que creen o borren datos en Test.
- V1 y V2 conservan sus SHA-256 auditados. No se modificó código, POM, properties ni scripts SQL durante esta aplicación.

El objetivo independiente `flyway:validate` previo señaló V2 pendiente como no aplicada, comportamiento observado de Flyway 12. No se ignoraron patrones para evitarlo: el orden documentado se corrigió a info, migrate con validación activa, validate e info. La validación estricta posterior fue correcta.

Solo se actualizó este documento y se generaron evidencias locales sin credenciales en `target/flyway-v2-test-application/20261018-172745-400/`: autorización, snapshots antes/después, consulta SQL, logs de Flyway/Hibernate y `verification.json`.

**Durante esa intervención de Test no se conectó ni modificó LM_Medical_supplies. Su aplicación posterior con autorización independiente y respaldo confirmado consta en la verificación más reciente al inicio.** Las migraciones automáticas continúan desactivadas en ambos perfiles. Las secciones siguientes describen la implementación y verificaciones anteriores, cuando Test todavía estaba en BASELINE 1.

## V2: implementación y verificación previa del 18 de octubre de 2026

### Objetivo y archivos

`src/main/resources/db/migration/V2__restricciones_integridad.sql` unifica las cuatro reglas de integridad que ya tenía la principal. No modifica V1 ni datos, columnas, PK, FK o restricciones existentes. Flyway administra el DDL; Hibernate conserva `ddl-auto=validate`.

| Regla | Tabla | Definición |
|---|---|---|
| `UQ_transferencia_lote` | `dbo.detalles_transferencia` | UNIQUE (`transferencia_id`, `lote_id`) |
| `CK_detalle_cantidad` | `dbo.detalles_salida` | `cantidad_retirada > 0` |
| `CK_detalle_stock` | `dbo.detalles_salida` | `stock_restante >= 0` |
| `CK_operacion_cantidad` | `dbo.operaciones_salida` | `cantidad > 0` |

Archivos de esta intervención:

- `src/main/resources/db/migration/V2__restricciones_integridad.sql`.
- `src/test/java/com/lmmedical/inventory/integration/V2MigrationSqlServerTest.java`.
- `src/test/java/com/lmmedical/inventory/integration/V2AppliedSqlServerTest.java`.
- `src/test/java/com/lmmedical/inventory/integration/V2ExistingConstraintsSqlServerTest.java`.
- Este documento.

No se cambiaron entidades, servicios, repositorios, POM, properties ni el script de baseline. No se activaron migraciones automáticas en dev/test y no se repitió ningún baseline.

### Validación previa y reconocimiento de reglas existentes

Antes del primer `ALTER TABLE`, V2 comprueba duplicados por transferencia/lote, cantidad retirada <= 0, stock restante < 0 y operaciones con cantidad <= 0. Los errores 51002–51005 identifican el dato incompatible y detienen la migración sin corregir ni eliminar filas. Se exige visibilidad de metadatos y se comprueban las tablas y las columnas INT NOT NULL de V1.

Para UNIQUE se inspeccionan las columnas clave reales de `sys.index_columns` y `sys.columns`, los índices y las restricciones. Una clave única no filtrada sobre las dos columnas es equivalente aunque cambien su nombre, orden, ASC/DESC o columnas INCLUDE. Debe estar habilitada, no ser hipotética y tener `IGNORE_DUP_KEY=OFF`. Un índice filtrado, con otras columnas clave o con `IGNORE_DUP_KEY` no sustituye esta regla. Un nombre estable ya ocupado por otra definición produce un error explícito.

Para CHECK se comparan la tabla, la columna referenciada mediante dependencias de SQL Server, la expresión normalizada y los indicadores `is_disabled`, `is_not_trusted` e `is_not_for_replication`. Se reconocen espacios, saltos, corchetes, paréntesis, operandos conmutados y límites enteros equivalentes (`>0`/`>=1`, `>=0`/`>-1`). No se intenta demostrar equivalencia de SQL arbitrario: una expresión desconocida que ya afecte la columna provoca una revisión explícita cuando no hay una regla equivalente acreditada. Esto evita aceptar reglas más débiles o crear duplicados por una equivalencia dudosa.

Los CHECK nuevos se crean con `WITH CHECK ADD CONSTRAINT`. Si una regla equivalente está deshabilitada, no es confiable o usa NOT FOR REPLICATION, V2 se detiene; no la habilita ni modifica silenciosamente. Las reglas correctas con otro nombre se conservan. Los conflictos de nombres o definiciones se revisan antes de crear ninguna regla.

Flyway mantiene la ejecución transaccional normal de SQL Server; V2 no contiene COMMIT ni gestiona una transacción independiente. `XACT_ABORT ON` aborta ante errores. Las pruebas verificaron la reversión completa de DDL y datos de escenarios fallidos mediante transacciones JDBC, sin afectar las tablas originales.

### Estado al terminar la implementación inicial, antes de aplicar V2 en Test

Todas las conexiones se comprobaron mediante `DB_NAME()`. La instancia de la aplicación es `DESKTOP-C6E0VMG\UTPDB`, accesible en `localhost,1433`. El nombre real de la primera temporal es `LM_Medical_Flyway_prueba`; las comprobaciones de los nombres autorizados admiten solo diferencias de mayúsculas.

| Base | Historial verificado | Tablas / columnas | Filas de negocio | V2 |
|---|---|---|---:|---|
| `LM_Medical_Flyway_Prueba` | V1 SQL y V2 SQL, ambas success=1 | 11 / 59 | 0 | Aplicada |
| `LM_Medical_Flyway_V2_Equivalentes` | V1 SQL y V2 SQL, ambas success=1 | 11 / 59 | 0 | Aplicada sobre reglas existentes |
| `LM_Medical_Test` | Solo versión 1, BASELINE, success=1 | 11 / 59 | 0 | No aplicada |
| `LM_Medical_supplies` | Solo versión 1, BASELINE, success=1 | 11 / 59 | 26 | No aplicada |

En la primera temporal V2 se registró a las **16:25:29.967** y en la adicional a las **16:35:36.470**, hora de Lima. Su checksum final es **-735586558**. No hay migraciones fallidas ni pendientes en las dos temporales. Las llamadas posteriores a `migrate()` ejecutaron cero migraciones, sin repetir V2.

Las dos temporales terminaron con 11 PK, 8 UNIQUE, 15 FK, 11 CHECK y 5 DEFAULT. La comparación funcional con la principal es idéntica. Se conservaron las 11 tablas y 59 columnas originales de la primera temporal, sus identificadores y fechas de creación, las PK, FK, restricciones e índices preexistentes. Solo se incorporaron una UNIQUE y tres CHECK; las fechas de modificación de esas tablas pueden reflejar el DDL añadido.

Desarrollo y Test recibieron únicamente consultas. Se compararon antes/después metadatos, identificadores, fechas, recuentos, SHA-256 de las filas ordenadas por PK y el historial completo: **idénticos**. Los datos de las pruebas se revirtieron con sus transacciones. No se eliminaron tablas o datos preexistentes ni se ejecutaron operaciones de limpieza.

Evidencias sin credenciales ni contenido de filas: `target/flyway-v2/verification.json`, snapshots `*-v2-before-*.json` y `*-v2-after-*.json`; informes JUnit en `target/surefire-reports`. Las evidencias de datos contienen recuentos y hashes, no las filas. Las dos bases temporales se conservaron y no se borraron.

SHA-256 final de V2: `64852F36325C73208F061F95C443CF137A43265D3D5D640B392ADADF9A284812`.

SHA-256 de V1, sin cambios: `EB14ED9B4D96CAFA18275B3612C45129153467CA5D8BC1C8F84C4B3FFBE91E6F`.

### Entidades JPA revisadas

- `DetalleTransferencia`: relaciones obligatorias a transferencia y lote, BIGINT compatibles, cantidad `int` no nula y constructor con cantidad positiva. La unicidad compuesta queda administrada por Flyway; no se duplicó mediante generación de esquema JPA.
- `DetalleOperacionSalida` (nombre real de DetalleSalida): relación obligatoria a operación, `loteId` escalar Long con FK SQL y cantidades `int` no nulas. Su constructor no valida cantidades. V2 protege también inserciones ajenas a los servicios. `codigoLote` no explicita en JPA la longitud SQL NVARCHAR(50); es una diferencia de metadatos anterior a V2. Hibernate acepta el mapeo actual. Se documenta sin introducir cambios ajenos a integridad V2.
- `OperacionSalida`: clave de 36 caracteres, IDs Long no nulos, cantidad `int`, observación de 250 y relación de detalles compatible. El constructor no valida cantidad positiva. No se modificó la lógica existente; los DTO/servicios y las reglas SQL conservan sus responsabilidades.

Hibernate 7.4.5, con Spring Boot 4.1.1 y Java 21.0.12.1, inició correctamente con `validate` después de V2 y utilizando el mismo DataSource que Flyway 12.4.0. Los CHECK y UNIQUE se comprobaron mediante catálogo y operaciones SQL; `Hibernate validate` por sí solo no demuestra todas esas reglas.

### Pruebas automatizadas y problemas encontrados

| Prueba | Resultado final |
|---|---|
| `V2MigrationSqlServerTest` | 19/19; esquemas aislados con V1 y rollback de esquema y filas |
| `V2AppliedSqlServerTest` | 1/1; historial V2, metadatos y Hibernate validate |
| `V2ExistingConstraintsSqlServerTest` | 1/1; Flyway reconoce reglas existentes, conserva IDs/definiciones y no repite V2 |
| `FlywayCheckSqlServerTest` | 3/3; DataSource compartido, segunda migración vacía, entradas, salidas y transferencias idempotentes con rollback |
| `DatabaseConfigurationTest` | 9/9 |
| `SelectorLotesFefoTest` | 5/5 |
| `SalidaInventarioServiceTest` | 1/1 |
| `ExistenciaTest` | 5 correctas y 1 fallo preexistente |

**39 comprobaciones pertinentes correctas.** Contando por separado las seis de Existencia: **45 casos, 44 correctos y 1 fallo conocido**. No se modificaron ni ocultaron sus expectativas: `noDebePermitirRetirarMasDelStockDisponible` espera IllegalStateException y recibe IllegalArgumentException. La suite completa no se declara verde. No se ejecutaron las pruebas que crean o borran datos en Test.

Las pruebas de V2 cubren datos incompatibles para las cuatro reglas, CHECK/UNIQUE rechazando inserciones, valores válidos y stock cero, nombres alternativos, columnas UNIQUE invertidas e INCLUDE, filtros, IGNORE_DUP_KEY y conflictos de expresiones o tabla. También comprueban que no queden cambios parciales en un caso fallido.

Durante el desarrollo se detectó y retiró `SET NOCOUNT ON`: su estado de sesión persistía en el pool y Hibernate recibía un recuento -1 al insertar. Se añadió una prueba que comprueba que V2 conserva el recuento JDBC. El ejecutor JDBC de pruebas consume todos los resultados/recuentos para recibir también los THROW posteriores en un lote.

Como V2 ya se había registrado en la primera temporal durante esa comprobación inicial, se utilizó **una sola vez `flyway:repair` exclusivamente en LM_Medical_Flyway_Prueba** para reconciliar el checksum de la versión todavía en desarrollo: de `132284551` a `-735586558`. No se repitió el DDL, no se cambió V1, no había migraciones fallidas que borrar y no se reparó ninguna base existente. La base adicional ejecutó directamente la V2 final sin repair. El archivo final queda congelado: cualquier cambio posterior corresponde a una nueva versión; no se debe usar repair para ocultar diferencias en otros entornos.

La primera consulta auxiliar de permisos de servidor usó una clase incorrecta y devolvió NULL; el diagnóstico inicial de falta de CREATE ANY DATABASE fue incorrecto. Se verificó con `HAS_PERMS_BY_NAME(NULL,NULL,'CREATE ANY DATABASE')`, que devolvió 1. No se otorgaron permisos ni cambiaron credenciales. Se creó la base adicional autorizada en la instancia de la aplicación después de comprobar que no existía allí. Los avisos 401 de repositorios Maven opcionales no impidieron compilar ni ejecutar las pruebas.

### Reproducir las comprobaciones

Usa Java 21 y las variables DB_USERNAME/DB_PASSWORD del proceso con credenciales reales; no las guardes en archivos. El perfil `flyway-check` apunta exclusivamente a la primera temporal y activa Flyway. Estas pruebas pueden ejecutar migraciones pendientes en las temporales, por lo que deben revisarse y autorizarse antes de cada futura versión.

```powershell
$env:V2_SQLSERVER_TESTS = 'true'
.\mvnw.cmd -B test '-Dtest=V2MigrationSqlServerTest,V2AppliedSqlServerTest,V2ExistingConstraintsSqlServerTest,FlywayCheckSqlServerTest,DatabaseConfigurationTest,SelectorLotesFefoTest,SalidaInventarioServiceTest'
# Fallo conocido, independiente y sin conexion a bases:
.\mvnw.cmd -B test '-Dtest=ExistenciaTest'
Remove-Item Env:V2_SQLSERVER_TESTS
```

La clase de reglas existentes solo acepta `LM_Medical_Flyway_V2_Equivalentes` en localhost:1433, verifica el nombre real y ausencia de datos antes de actuar y se detiene ante un estado inesperado. La base ya está preparada y no requiere recreación. Para reproducir desde cero en otro entorno desechable, un administrador autorizado debe crear previamente esa base vacía: la clase aplica V1 con target=1, agrega las cuatro reglas preexistentes con transacción y después migra a target=2. Si ya existe V2, verifica el historial sin recrear las reglas. No crea ni borra bases ni ejecuta clean.

Los casos adversos construyen una copia de V1 en memoria con un esquema aleatorio dentro de una transacción de la primera temporal; el archivo V1 real no cambia. Cada caso revierte su esquema y datos, sin depender del orden ni de filas permanentes. No ejecutes la suite indiscriminadamente contra dev/Test: otras pruebas preexistentes escriben allí.

### Procedimiento de aplicación controlada por base

El procedimiento ya se ejecutó en Test y en LM_Medical_supplies con autorizaciones y respaldos confirmados por separado, según las verificaciones documentadas al inicio. Cada nueva aplicación requiere autorización específica por base, respaldo verificado actualizado, credenciales disponibles y una ventana adecuada para validar datos y bloquear el DDL.

1. Selecciona únicamente `LM_Medical_Test` o `LM_Medical_supplies` y confirma `SELECT DB_NAME()` antes de cualquier escritura.
2. Verifica BASELINE 1 correcto, ausencia de fallos y SHA-256 de los archivos. No repitas baseline.
3. Revisa las cuatro consultas de incompatibilidad descritas en el script V2 y los CHECK/UNIQUE existentes. Si hay datos inválidos o una definición ambigua, detente; la reparación de datos o restricciones exige un procedimiento aparte.
4. Ejecuta info y comprueba que solo V2 esté pendiente. El objetivo independiente validate de Flyway 12 señala una migración pendiente como no aplicada; ese aviso esperado no se corrige con repair ni ignorando patrones. Mantén validateOnMigrate=true: migrate valida el historial antes de aplicar la versión autorizada. Ejecuta validate independiente después de migrate. Mantén Flyway automático de dev/test desactivado.
5. Solo después de la autorización concreta, ejecuta migrate con target=2 para la base seleccionada y verifica mediante lecturas historial, reglas, columnas, filas y Hibernate validate.

```powershell
# SOLO TRAS AUTORIZACION EXPLICITA PARA ESA BASE Y RESPALDO VERIFICADO.
$database = 'LM_Medical_Test'
if ($database -cnotin @('LM_Medical_Test', 'LM_Medical_supplies')) { throw 'Destino no permitido' }
$url = 'jdbc:sqlserver://localhost:1433;databaseName=' + $database + ';encrypt=true;trustServerCertificate=true'
$flags = @('-B', ('-Dflyway.url=' + $url), '-Dflyway.target=2', '-Dflyway.defaultSchema=dbo', '-Dflyway.schemas=dbo', '-Dflyway.baselineOnMigrate=false', '-Dflyway.cleanDisabled=true', '-Dflyway.validateOnMigrate=true')
.\mvnw.cmd @flags 'flyway:info'
if ($LASTEXITCODE -ne 0) { throw 'info fallo' }
.\mvnw.cmd @flags 'flyway:migrate'
if ($LASTEXITCODE -ne 0) { throw 'Migracion fallida; revisar historial y transaccion antes de continuar' }
.\mvnw.cmd @flags 'flyway:validate'
if ($LASTEXITCODE -ne 0) { throw 'Validacion posterior fallida; no usar repair para forzarlo' }
.\mvnw.cmd @flags 'flyway:info'
```

Verificación posterior de solo lectura en la conexión ya confirmada:

```sql
SELECT DB_NAME() AS base_actual;
SELECT installed_rank, version, type, success, checksum, installed_on
FROM dbo.flyway_schema_history ORDER BY installed_rank;
SELECT name, definition, is_disabled, is_not_trusted, is_not_for_replication
FROM sys.check_constraints
WHERE parent_object_id IN (OBJECT_ID(N'dbo.detalles_salida'), OBJECT_ID(N'dbo.operaciones_salida'));
SELECT i.name, i.is_unique, i.has_filter, i.ignore_dup_key, i.is_disabled,
       c.name AS columna, ic.key_ordinal, ic.is_included_column
FROM sys.indexes i JOIN sys.index_columns ic
  ON ic.object_id=i.object_id AND ic.index_id=i.index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id=OBJECT_ID(N'dbo.detalles_transferencia') AND i.is_unique=1
ORDER BY i.name, ic.key_ordinal, ic.index_column_id;
```

Se espera un registro adicional `version=2`, `type=SQL`, `success=1`, checksum `-735586558`, cuatro reglas equivalentes activas y confiables y ausencia de cambios en filas y columnas. Con reglas previas de otros nombres, verifica la definición real, no solo los nombres canónicos. La activación posterior de Flyway en dev/test sigue requiriendo autorización específica y revisión de versiones pendientes.

Las secciones siguientes conservan la evidencia histórica de V1 y los baselines; el estado actual de V2 es el indicado arriba.

## Baseline de LM_Medical_supplies: verificado (intervención anterior)

El 18 de octubre de 2026 a las 16:04:35, hora de Lima, se registró BASELINE 1 en `LM_Medical_supplies`, después de la nueva autorización expresa del propietario y su confirmación de un respaldo realizado y verificado. Esta es la misma base que el usuario escribió como `LM_Medical_Supplies`; `DB_NAME()` confirmó el nombre canónico con `supplies` en minúsculas. La operación anterior sobre Test no autorizaba esta base; se actuó después de la nueva solicitud y confirmación.

### Comprobaciones y resultado

- Antes de actuar se consultó el destino y el historial: `LM_Medical_supplies`, 11 tablas de negocio, sin `dbo.flyway_schema_history`.
- El esquema coincidía con la revisión previa: 59 columnas, 11 PK, 8 UNIQUE, 15 FK, 11 CHECK y 5 DEFAULT. Frente a V1, conserva una UNIQUE y tres CHECK adicionales, documentadas más abajo. No falta ningún elemento de V1 y no se alteraron esas restricciones.
- Se reutilizaron las credenciales locales existentes de IntelliJ únicamente en el proceso y Java 21.0.12.1; no se mostraron credenciales ni se cambiaron variables globales. El script comprobó permisos con la misma cuenta SQL utilizada por Maven; no se modificaron permisos.
- Se actualizó el script para aceptar exclusivamente las dos bases autorizadas, con nombre canónico y URL derivada de la lista permitida. `DB_NAME()` debe coincidir exactamente con el destino seleccionado. Las confirmaciones de respaldo y autorización, la revisión del historial y los controles de datos se mantienen.
- Comando ejecutado: `.\scripts\flyway\Invoke-Baseline.ps1 -Database LM_Medical_Supplies -BackupConfirmed -AuthorizeBaseline`.
- `flyway:baseline`: `BUILD SUCCESS`, `Successfully baselined schema with version: 1`.
- Consulta independiente de solo lectura: `installed_rank=1`, `version=1`, `type=BASELINE`, `success=1`, `installed_on=2026-10-18 16:04:35.807`.
- `flyway:info`: `BUILD SUCCESS`, `Schema version: 1`. V1 SQL figura como `Ignored (Baseline)`; el registro BASELINE figura como `Baseline`. Actualmente no hay migraciones pendientes.
- Comparación independiente de metadatos antes/después: idéntica. Las 11 tablas mantienen identificadores, fechas y estructura, incluidas las cuatro restricciones adicionales.
- Coinciden los recuentos y SHA-256 de las filas ordenadas por PK antes y después: **26 filas de negocio conservadas**.

| Tabla | Filas antes | Filas después |
|---|---:|---:|
| `almacenes` | 3 | 3 |
| `categorias` | 5 | 5 |
| `detalles_salida` | 1 | 1 |
| `detalles_transferencia` | 1 | 1 |
| `existencias` | 2 | 2 |
| `lotes` | 2 | 2 |
| `movimientos_inventario` | 6 | 6 |
| `operaciones_salida` | 1 | 1 |
| `productos` | 3 | 3 |
| `proveedores` | 1 | 1 |
| `transferencias` | 1 | 1 |

Las evidencias sin credenciales ni contenido de filas están en `target/flyway-baseline/LM_Medical_supplies/20261018-160424-329/`: `target.json`, `before.json`, `after.json` y `history.json`.

Se modificaron únicamente `scripts/flyway/Invoke-Baseline.ps1` y este documento. Se verificó que el script rechaza destinos ajenos a la lista y la ausencia de confirmaciones antes de conectar. No se ejecutaron migraciones, V1, V2 ni operaciones sobre datos de negocio. El hash de V1 no cambió. `baseline-on-migrate=false`, `clean-disabled=true` y la desactivación automática de Flyway en dev/test se mantienen.

Ambas bases tienen BASELINE 1 registrado y quedan preparadas para futuras migraciones expresamente autorizadas. Una futura V2 debe reconocer las restricciones adicionales existentes en la principal y evitar crearlas por duplicado. Esta intervención no ejecutó el backend ni pruebas que escriban datos.

## Baseline de LM_Medical_Test: verificado (intervención anterior)

El 18 de octubre de 2026 a las 15:55:37, hora de Lima, se registró exclusivamente el baseline versión 1 en `LM_Medical_Test`. El propietario confirmó expresamente el respaldo verificado y autorizó esta operación. En esta intervención no se conectó ni modificó `LM_Medical_supplies`.

### Error manual y solución

La ejecución original desde PowerShell se detenía antes de conectar porque `DB_USERNAME` y `DB_PASSWORD` no existían en su entorno. Las credenciales estaban disponibles en la configuración local de IntelliJ, que no las transmite a una terminal independiente. Además, `JAVA_HOME` apuntaba a Java 26, aunque el proyecto usa Java 21.

Se usaron las credenciales existentes de IntelliJ únicamente en el proceso de ejecución, sin mostrarlas ni guardarlas en archivos del proyecto. Para ese proceso se seleccionó `C:\Users\User\.jdks\ms-21.0.12.1`. Al terminar se restauraron las variables anteriores; la configuración global del equipo no cambió. El Wrapper Maven 3.10.0 y el plugin Flyway 12.4.0 funcionaron sin modificar el POM ni los properties.

En esa intervención el script aceptaba únicamente `LM_Medical_Test`; exigía las dos confirmaciones y Java 21, verifica `DB_NAME()` mediante las mismas credenciales que Maven, comprueba permisos y revisa el historial antes de actuar. Si ya existe un BASELINE 1 correcto, lo conserva y solo consulta `flyway:info`. Si el historial es incompatible o contiene fallos, detiene la operación. También compara recuentos y SHA-256 de las filas ordenadas por PK, identificadores y fechas de las tablas antes y después. Durante la comprobación del script se corrigió la asignación de claves de `SqlConnectionStringBuilder` para PowerShell, usando nombres de conexión canónicos; ese error se produjo antes de abrir la conexión.

### Evidencia de la ejecución

- Destino efectivo: `jdbc:sqlserver://localhost:1433;databaseName=LM_Medical_Test;encrypt=true;trustServerCertificate=true`.
- Antes del comando: 11 tablas de negocio, esquema equivalente a V1 y sin `dbo.flyway_schema_history`.
- Se comprobaron permisos de lectura y definición del esquema, CREATE TABLE y ALTER/INSERT en dbo con la cuenta SQL utilizada por Flyway. No se cambiaron permisos.
- Comando ejecutado: `.\scripts\flyway\Invoke-Baseline.ps1 -Database LM_Medical_Test -BackupConfirmed -AuthorizeBaseline`.
- `flyway:baseline`: `BUILD SUCCESS`, `Successfully baselined schema with version: 1`.
- Consulta independiente de solo lectura: `installed_rank=1`, `version=1`, `type=BASELINE`, `success=1`, `installed_on=2026-10-18 15:55:37.730`.
- `flyway:info`: `BUILD SUCCESS`, `Schema version: 1`; V1 SQL aparece como `Ignored (Baseline)` y el registro BASELINE como `Baseline`. No hay migraciones pendientes actualmente.
- Esquema antes/después idéntico: 11 tablas, 59 columnas, 11 PK, 7 UNIQUE, 15 FK, 8 CHECK y 5 DEFAULT. Los identificadores y fechas de las tablas originales no cambiaron.
- Las 11 tablas ya tenían **0 filas antes** y conservaron **0 filas después**. Coinciden los recuentos y SHA-256; no se ejecutó ninguna operación sobre datos de negocio.
- Evidencias locales sin credenciales ni contenido de filas: `target/flyway-baseline/20261018-155522-770/before.json`, `after.json` y `history.json`. `target` está excluido de Git.
- El SHA-256 de V1 permanece `EB14ED9B4D96CAFA18275B3612C45129153467CA5D8BC1C8F84C4B3FFBE91E6F`.

Maven emitió avisos HTTP 401 al intentar descubrir prefijos en dos repositorios opcionales de Flyway. No son errores de autenticación de SQL Server: ambos objetivos Maven terminaron correctamente y el registro se verificó mediante SQL.

Solo se modificaron `scripts/flyway/Invoke-Baseline.ps1` y este documento. No se ejecutaron migraciones, V1, V2 ni operaciones de limpieza. `baseline-on-migrate=false` y `clean-disabled=true` siguen activos. `FLYWAY_TEST_ENABLED` sigue desactivado por defecto: la base está preparada para futuras migraciones, cuya ejecución y activación requieren autorización posterior.

## Verificación anterior de V1 (antes de este baseline)

- V1 se ejecutó mediante Spring Boot y Flyway exclusivamente en `LM_Medical_Flyway_Prueba`.
- Justo antes de ejecutarla, esa base tenía **0 objetos de usuario**.
- Se crearon 11 tablas de negocio, 59 columnas, 11 PK, 7 restricciones UNIQUE, 15 FK, 8 CHECK y 5 DEFAULT.
- Flyway creó `dbo.flyway_schema_history`: versión `1`, tipo `SQL`, script `V1__esquema_inicial.sql`, checksum `-1451037639`, `success=1`.
- Hibernate completó `ddl-auto=validate` y Flyway usó exactamente el mismo DataSource de Spring Boot.
- Una segunda llamada a `migrate()` ejecutó **0 migraciones** y mantuvo los mismos identificadores y fechas de creación de las tablas.
- El flujo de entrada, salida idempotente y transferencia idempotente funcionó sobre V1. Sus datos se revirtieron; la base temporal terminó con **0 filas de negocio**.
- En `LM_Medical_supplies` y `LM_Medical_Test` solo se hicieron consultas y validaciones de arranque. No se ejecutaron baseline, migraciones, CREATE TABLE ni operaciones de datos. Los metadatos se compararon antes y después: sin cambios. Ninguna tiene `flyway_schema_history`.

No se utilizó ni eliminó la base temporal con nombre generado en la operación anterior.

## Causas de los errores

1. `application.properties` dirigía globalmente el backend a la base temporal.
2. `spring.flyway.url/user/password` creaban una conexión independiente. Al activar `test`, cambiaba el DataSource a `LM_Medical_Test`, pero Flyway conservaba la URL temporal global.
3. IntelliJ tenía credenciales en `spring.datasource.username/password`; eso podía permitir conectar al DataSource sin definir `DB_USERNAME/DB_PASSWORD`, que Flyway seguía intentando resolver por separado. También había configuraciones JUnit sin las variables.
4. El archivo estaba en `database/migration`, pero la ubicación configurada era `db/migration`.
5. `spring.sql.init.mode=always` ejecutaba scripts de creación de tablas además de Flyway.
6. Las bases existentes no tenían historial: necesitan un baseline explícito después de la revisión y autorización; una migración V1 normal no corresponde a un esquema ya creado.

## Archivos de la verificación anterior de V1

- `src/main/resources/application.properties`: configuración común, credenciales del entorno, un solo DataSource, Hibernate validate y SQL init desactivado.
- `src/main/resources/application-dev.properties`: URL de desarrollo y activación de Flyway pendiente del baseline.
- `src/main/resources/application-test.properties`: URL de pruebas y activación de Flyway pendiente del baseline.
- `src/main/resources/application-flyway-check.properties`: URL temporal y Flyway activo sin baseline automático.
- `src/main/resources/db/migration/V1__esquema_inicial.sql`: reubicado desde `database/migration`; su contenido no cambió en esta revisión.
- `src/main/java/com/lmmedical/inventory/config/DatabaseConfiguration.java`: validación temprana de credenciales, perfil único, base de destino y reutilización del DataSource.
- `src/test/java/com/lmmedical/inventory/config/DatabaseConfigurationTest.java`: nueve comprobaciones de configuración.
- `src/test/java/com/lmmedical/inventory/integration/FlywayCheckSqlServerTest.java`: tres pruebas sobre la base temporal.
- `pom.xml`: plugin Maven de Flyway para operaciones explícitas de mantenimiento. Las dependencias de la aplicación ya eran correctas y se conservaron.
- `scripts/flyway/Invoke-Baseline.ps1`: comando de baseline preparado en la revisión anterior; ejecutado y reforzado en la intervención documentada arriba.
- `.idea/workspace.xml`: configuración local, excluida por `.gitignore`; variables del entorno unificadas y ejecuciones dev/flyway-check separadas.
- Este documento.

Los antiguos SQL de `db/test` permanecen como archivos históricos, sin configuración que los ejecute. No se modificaron servicios de entradas, salidas, FEFO o transferencias ni se eliminaron pruebas.

SHA-256 de V1:

```text
EB14ED9B4D96CAFA18275B3612C45129153467CA5D8BC1C8F84C4B3FFBE91E6F
```

V1 contiene solo DDL, sin Markdown, USE, DROP DATABASE, DROP TABLE ni dependencia de datos existentes. No se debe editar después de aplicarla; los cambios posteriores pertenecen a V2 o superiores.

## Perfiles y credenciales

| Perfil | Base de datos | Flyway ahora |
|---|---|---|
| `dev` — predeterminado | `LM_Medical_supplies` | Desactivado por defecto; BASELINE 1 ya registrado |
| `test` | `LM_Medical_Test` | Desactivado por defecto; BASELINE 1 ya registrado |
| `flyway-check` | `LM_Medical_Flyway_Prueba` | Activado |

Hibernate utiliza `validate` y `spring.sql.init.mode=never` en los tres perfiles. `baseline-on-migrate=false` y `clean-disabled=true` permanecen configurados.

Se mantienen las versiones gestionadas por Spring Boot 4.1.1:

- `spring-boot-starter-flyway`: 4.1.1.
- `flyway-core` y `flyway-sqlserver`: 12.4.0.
- `mssql-jdbc`: 13.4.0.jre11.
- JDK usado en las comprobaciones: Java 21.0.12.1.

El plugin Maven usa las mismas propiedades de versión del padre. Sus dependencias viven en el classloader del plugin; no agregan una segunda copia a las dependencias de la aplicación.

### IntelliJ IDEA

1. Reabre el proyecto para cargar los cambios locales en las configuraciones de ejecución.
2. Abre **Run > Edit Configurations**.
3. En **Environment variables**, define `DB_USERNAME` y `DB_PASSWORD` con tus valores reales, sin envolverlos en comillas ni escribir `${DB_USERNAME}` como valor.
4. Para la API normal usa `SPRING_PROFILES_ACTIVE=dev`.
5. Para comprobar Flyway usa la configuración separada **InventoryBackendApplication - flyway-check**, con `SPRING_PROFILES_ACTIVE=flyway-check`.
6. En JUnit deja que `@ActiveProfiles` seleccione el perfil. La plantilla JUnit local tiene las dos variables; una configuración Application no transmite automáticamente sus variables a otra JUnit.
7. Elimina sobrescrituras antiguas como `spring.flyway.url/user/password` y `spring.datasource.username/password`; usa las variables `DB_*` comunes.

No hay credenciales reales en properties, POM, migraciones, pruebas ni este documento. En ejecución deben existir en el entorno de cada proceso. La validación inicial da un mensaje claro si faltan y no muestra sus valores.

No actives simultáneamente `dev`, `test` y `flyway-check`. Una sobrescritura de `spring.datasource.url` que apunte a la base de otro perfil se rechaza antes de crear el DataSource.

### Ejecución desde una terminal

Configura Java 21 y las variables `DB_USERNAME`/`DB_PASSWORD` en el entorno de la terminal. Desde la raíz del proyecto:

```powershell
.\mvnw.cmd test -Dtest=FlywayCheckSqlServerTest
```

La base temporal ya contiene V1 como resultado de esta verificación: las ejecuciones posteriores validan el historial y no repiten CREATE TABLE. Para otra comprobación desde cero se requiere una base nuevamente vacía y una autorización separada; no hay limpieza automática.

## Comparación previa al baseline

### LM_Medical_Test

Coincide exactamente con el esquema construido por V1 en la base temporal: tablas, columnas, tipos, longitudes, nulabilidad, identidades, collations, defaults, claves, restricciones e índices asociados a las claves.

Comparación reconfirmada antes del comando autorizado; BASELINE 1 registrado y verificado el 18 de octubre de 2026.

### LM_Medical_supplies

Tiene las mismas 11 tablas y 59 columnas. Los tipos y definiciones de columnas coinciden. Conserva cuatro restricciones adicionales frente a V1:

| Tabla | Restricción adicional | Definición |
|---|---|---|
| `detalles_transferencia` | `UQ_transferencia_lote` | UNIQUE (`transferencia_id`, `lote_id`) |
| `detalles_salida` | `CK_detalle_cantidad` | `cantidad_retirada > 0` |
| `detalles_salida` | `CK_detalle_stock` | `stock_restante >= 0` |
| `operaciones_salida` | `CK_operacion_cantidad` | `cantidad > 0` |

Los sufijos de nombres automáticos de PK/default/UNIQUE también pueden diferir entre bases; se compararon por su definición. No se eliminaron ni debilitaron restricciones para conseguir que V1 pasara.

Estas diferencias se revisaron nuevamente y se conservaron al registrar el baseline autorizado de la principal. Una V2 aditiva puede incorporar las cuatro restricciones en las bases que no las tengan, verificando primero datos incompatibles y reconociendo las definiciones existentes. No se ha creado ni ejecutado V2 en esta revisión.

## Uso protegido del script de baseline

El baseline versión 1 registra la adopción del esquema existente y excluye V1 de las migraciones pendientes. La comparación de esquema debe hacerse previamente: el baseline no demuestra por sí mismo equivalencia con V1.

El script acepta exclusivamente `LM_Medical_Test` y `LM_Medical_supplies`. Requiere seleccionar explícitamente la base, confirmar su respaldo verificado y su autorización. Ambas bases ya tienen BASELINE 1; otra ejecución con las credenciales correctas conserva el registro existente y solo verifica el historial, las tablas y los datos y ejecuta `flyway:info`.

Para futuras ejecuciones manuales, define Java 21 y las credenciales reales en el proceso de PowerShell. Puedes introducirlas sin escribirlas en el historial:

```powershell
$env:JAVA_HOME = 'C:\Users\User\.jdks\ms-21.0.12.1'
$sqlCredential = Get-Credential -Message 'Credenciales SQL Server para LM_Medical_Test'
$env:DB_USERNAME = $sqlCredential.UserName
$env:DB_PASSWORD = $sqlCredential.GetNetworkCredential().Password
try {
    .\scripts\flyway\Invoke-Baseline.ps1 -Database LM_Medical_Test -BackupConfirmed -AuthorizeBaseline
} finally {
    Remove-Item Env:DB_USERNAME, Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable sqlCredential
}
```

Para consultar la principal mediante el mismo script, selecciona `-Database LM_Medical_supplies` en lugar de Test; no se cambia el destino mediante variables globales. Las confirmaciones siguen representando una autorización y un respaldo verificado reales para la base seleccionada. Si faltan permisos o el historial es incompatible, se debe resolver la causa; no se deben eliminar las protecciones ni editar el historial para forzar la operación.

Después de autorizar futuras migraciones se podrá activar `FLYWAY_TEST_ENABLED=true` para test o `FLYWAY_DEV_ENABLED=true` para dev, según el destino autorizado. Ambos permanecen desactivados. V2 se aplicó en las dos temporales, en Test y en LM_Medical_supplies con sus autorizaciones independientes; no quedan aplicaciones de V2 pendientes. No se debe activar la ejecución automática sin revisar primero las migraciones pendientes.

## Pruebas de la verificación anterior de V1 y límites

| Comprobación | Resultado |
|---|---|
| Limpieza de target y compilación de todos los fuentes principales y de prueba | Correcta |
| `DatabaseConfigurationTest` | 9/9 correctas |
| `SelectorLotesFefoTest` | 5/5 correctas |
| `SalidaInventarioServiceTest` | 1/1 correcta |
| `ExistenciaTest` | 5 correctas y 1 fallo preexistente |
| `FlywayCheckSqlServerTest` | 3/3 correctas sobre la base temporal |
| `InventoryBackendApplicationTests`, perfil dev | Correcta; Hibernate validate, sin migraciones ni DML |
| `InventoryBackendApplicationTests`, perfil test | Correcta; Hibernate validate, sin migraciones ni DML |
| `flyway:info` del plugin Maven en LM_Medical_Test, antes del baseline | Correcta; entonces V1 estaba pendiente, sin historial |
| Script de baseline sin confirmación ni autorización | Bloquea la operación correctamente |
| Comparación de metadatos antes/después en las bases existentes | Sin cambios |

En total: **26 ejecuciones de prueba, 25 correctas y 1 fallida**.

Fallo ajeno a Flyway: `ExistenciaTest.noDebePermitirRetirarMasDelStockDisponible` espera `IllegalStateException`, mientras `Existencia.retirar` lanza `IllegalArgumentException`. Se conservó el fallo para no cambiar la lógica ni las expectativas de negocio fuera del alcance pedido. Por eso la suite completa todavía no puede declararse verde.

No se ejecutaron las pruebas existentes de integración/concurrencia que crean y borran registros en `LM_Medical_Test`: esta revisión debía detenerse antes de modificar datos de bases existentes. Se sustituyó esa ejecución por el flujo de regresión con rollback en la base temporal y por validaciones de arranque de solo lectura en dev/test.

Flyway funciona en la base temporal y ambas bases existentes tienen BASELINE 1 verificado. Las cuatro restricciones adicionales de la principal se conservaron y deben tenerse en cuenta al diseñar V2. La ejecución de futuras migraciones queda fuera de esta intervención.

## Referencias

- [Spring Boot 4.1.1: inicialización de bases de datos](https://docs.spring.io/spring-boot/how-to/data-initialization.html): un solo mecanismo de migración y reutilización del DataSource por Flyway.
- [Flyway: baseline](https://documentation.red-gate.com/fd/baseline-277578867.html): adopción explícita de esquemas existentes en una versión inicial.
