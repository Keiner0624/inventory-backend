# Fase 12: persistencia de seguridad

## Estado verificado: fase 12.9 (25 de octubre de 2026)

La suite de regresión ejecutó 139 casos sin SQL y 37 con SQL Server real, incluidos reutilización, rollback y tres carreras de refresh. Las escrituras se limitaron a fixtures en LM_Medical_Test; LM_Medical_supplies permanece idéntica. Ver [FASE_12_9_SEGURIDAD_TESTS.md](FASE_12_9_SEGURIDAD_TESTS.md) para comandos, hallazgos, archivos y evidencias. Las secciones fechadas siguientes conservan el estado histórico de sus entregas.


## Estado actual del bootstrap y login (24 de octubre de 2026)

Se corrigió la condición enable/enabled del primer administrador y se separó su persistencia transaccional. El bootstrap queda desactivado y exige aprobación y destino explícitos antes de escribir en LM_Medical_supplies. Tras la aprobación explícita del propietario se creó una cuenta activa con ADMIN únicamente en LM_Medical_supplies. Las otras dos bases conservan cero usuarios/asignaciones. Todas mantienen V3, tres roles e inventario e historial intactos. El bootstrap está desactivado y sus credenciales iniciales se retiraron de la ejecución normal.

Ver [BOOTSTRAP_ADMIN.md](BOOTSTRAP_ADMIN.md) para causa, archivos, controles, 49 pruebas correctas e instrucciones de activación y desactivación. El login y JWT ya están implementados: se comprobó HTTP 401 real contra SQL Server para un usuario inexistente y HTTP 200 con JWT válido en MockMvc con repositorios simulados. Después de crear la cuenta autorizada se verificó también el login HTTP 200 real, firma y claims del JWT y rechazo HTTP 401 de una contraseña incorrecta. El servidor temporal de verificación se cerró; iniciar normalmente la API para usar el puerto 8080.

La configuración duplicada JwtSecurity quedó sin beans; JwtConfig es la única activa con app.jwt.enabled. AuthController usa la misma condición. Los permisos de SecurityConfig se conservaron; dev ahora escucha en 127.0.0.1. Exigir JWT a inventario, autorización por roles/almacén y refresh siguen pendientes. Las secciones siguientes conservan el diseño y las evidencias históricas anteriores a este bootstrap y al login.



Verificación realizada el 18 de octubre de 2026, hora de Lima.

## Alcance y estado

Esta entrega incorpora exclusivamente entidades JPA, repositorios y la migración V3 para usuarios, roles y sesiones renovables. No implementa autenticación, JWT, registro, endpoints de refresh ni un administrador real. Conserva la configuración actual de Spring Security y toda la lógica de inventario.

**V3 está aplicada en `LM_Medical_Flyway_Prueba`, `LM_Medical_Test` y `LM_Medical_supplies`.** La aplicación en las dos bases principales se realizó posteriormente con autorización expresa y respaldos confirmados para ambas. Las migraciones automáticas de `dev` y `test` continúan desactivadas; Hibernate conserva `ddl-auto=validate`.

**Compatibilidad de despliegue:** las cuatro tablas nuevas ya existen en las dos bases principales. Se verificó el arranque del contexto actual con Hibernate validate: 1/1 en test y 1/1 en dev, sin migraciones automáticas ni escrituras de inventario. La limitación inicial por tablas ausentes quedó resuelta; no se desactivó la validación ni se excluyeron entidades.

## Inspección y arquitectura

Se revisaron POM, properties, migraciones, configuración de conexiones, entidades, repositorios, servicios, controladores, seguridad y pruebas existentes. El proyecto usa Java 21, Spring Boot 4.1.1, Hibernate 7.4.5.Final y Flyway 12.4.0. Las capas actuales son `controller`, `dto`, `service`, `repository`, `entity`, `domain`, `exception` y `config`.

Las nuevas clases respetan esas convenciones: constructores de dominio, getters Lombok, constructor protegido para JPA y `JpaRepository`. No se añadieron dependencias, servicios vacíos, interfaces de servicios ni relaciones bidireccionales. Las relaciones son lazy, sin cascadas de eliminación. No se modificaron controladores ni servicios de entradas, salidas, FEFO, idempotencia o transferencias.

## Modelo y relaciones

| Tabla / entidad | Propósito y restricciones |
|---|---|
| `dbo.usuarios` / `Usuario` | ID bigint identity; nombre y apellido Unicode obligatorios; correo ASCII normalizado a minúsculas y único; password_hash; activo; creación y actualización UTC. |
| `dbo.roles` / `Rol` | ID bigint identity; código único en mayúsculas; nombre Unicode obligatorio. |
| `dbo.usuario_rol` / `UsuarioRol` | PK compuesta `(usuario_id, rol_id)` y dos FK. Evita asignaciones duplicadas y huérfanas. |
| `dbo.sesiones_refresh` / `SesionRefresh` | UUID por emisión, FK a usuario, UUID de familia, hash único, creación, expiración y revocación opcional en UTC. |

Un usuario puede tener varios roles y varias sesiones. `UsuarioRol.Id` es una clave embebida con igualdad por sus dos identificadores. Las FK no eliminan registros en cascada.

La aplicación normaliza el correo con `strip()` y `Locale.ROOT`. Las consultas normalizan el parámetro, conservando una comparación directa sobre la columna indexada. La collation binaria y el CHECK rechazan valores sin normalizar insertados directamente por SQL; UNIQUE resuelve también carreras entre inserciones. Jakarta `@Email` valida el formato en JPA; el CHECK SQL solo exige una estructura básica y caracteres ASCII imprimibles. No acredita la propiedad del correo: la verificación por correo queda pendiente.

Todas las fechas nuevas son `DATETIMEOFFSET(7)` con offset cero, representadas por `OffsetDateTime` y almacenamiento nativo de zona en Hibernate. Los constructores normalizan a UTC y microsegundos para conservar la igualdad al volver de JDBC. La actualización del usuario se mantiene mediante `@PreUpdate`; para futuras escrituras SQL directas deberá actualizarse también `fecha_actualizacion` explícitamente. El CHECK exige actualización >= creación. Una sesión expira después de crearse; su revocación no puede ser anterior a su creación y puede registrarse después de expirar.

Se añadió `familia_id` a la propuesta inicial para identificar la cadena de rotaciones y permitir revocar todas sus emisiones si se detecta reutilización. No implementa por sí sola la rotación ni su control de concurrencia.

## Migración V3

Archivo: `src/main/resources/db/migration/V3__usuarios_roles_seguridad.sql`.

La migración crea solamente las cuatro tablas nuevas. Define 4 PK, 3 UNIQUE, 3 FK, 11 CHECK, 4 DEFAULT y 3 índices auxiliares, además de los índices que respaldan PK y UNIQUE. Los índices auxiliares cubren la relación inversa rol/usuario, las sesiones por usuario/familia y la expiración. Todos los nombres de restricciones son explícitos y estables.

Antes de crear objetos, V3 falla si ya existe cualquiera de las cuatro tablas sin su migración registrada. No adopta silenciosamente un esquema parcial. Flyway ejecuta el archivo como una migración transaccional en SQL Server; `SET XACT_ABORT ON` asegura el aborto ante errores SQL. No contiene instrucciones de limpieza ni cambios a tablas anteriores. No utiliza `SET NOCOUNT ON`, para conservar los recuentos de actualización que necesita Hibernate en conexiones reutilizadas.

Se insertan solamente estos tres roles de referencia:

| Código | Responsabilidad propuesta para la siguiente fase |
|---|---|
| `ADMIN` | Administrar usuarios y asignaciones de roles y supervisar inventario. |
| `ENCARGADO_ALMACEN` | Operar inventario y transferencias según el almacén autorizado. |
| `EMPLEADO` | Consultar el inventario y realizar únicamente las operaciones que autorice la política final. |

Las responsabilidades aún no se aplican a endpoints. La asignación de almacenes y la matriz exacta de permisos requieren diseño posterior; un rol global no demuestra acceso a un almacén concreto.

## Contraseñas y exposición JSON

`Usuario` recibe un hash ya calculado; nunca calcula ni almacena una contraseña en texto plano. El formato inicial es `{bcrypt}` seguido de un BCrypt válido en su estructura, con coste mínimo 12. Se eligió BCrypt porque Spring Security ya lo proporciona sin dependencias adicionales y el prefijo permite identificar el algoritmo. El CHECK y la validación Java rechazan texto sin ese formato; no prueban que una cadena haya sido producida criptográficamente por un encoder. El futuro servicio será el único encargado de generar los hashes con `PasswordEncoder`.

La política inicial del futuro encoder será coste 12, sujeto a medición en el servidor real. El formato acepta costes 12–31 para hashes existentes; el cliente nunca podrá elegir el coste ni enviar un hash como sustituto de la contraseña. BCrypt tiene límite de 72 bytes: el futuro flujo deberá rechazar entradas que lo superen, sin truncarlas silenciosamente. La recomendación de OWASP favorece Argon2id para sistemas nuevos; debe evaluarse antes de cerrar la autenticación. Adoptarlo requerirá una migración adicional que amplíe el CHECK y la validación, más su encoder y dependencia. No se presenta el esquema actual como independiente del algoritmo.

`passwordHash` y `tokenHash` llevan `@JsonIgnore`; una prueba con el serializador Jackson de esta versión comprueba que no aparecen ni sus nombres ni valores. Las asociaciones también se omiten donde evitarían cargar usuarios y roles accidentalmente. La futura API utilizará DTO explícitos; no debe recibir ni devolver entidades de persistencia.

No deben registrarse contraseñas, hashes, cabeceras Authorization ni tokens en logs, excepciones o auditorías. No se añadieron credenciales al código, SQL ni documentación. Las pruebas generan valores aleatorios y sus transacciones no dejan usuarios ni sesiones persistidos.

## Refresh tokens: diseño futuro

El hash almacenado será SHA-256 de un token opaco generado con `SecureRandom`, con al menos 256 bits aleatorios. La columna acepta exactamente 64 caracteres hexadecimales en minúsculas y es UNIQUE. SHA-256 se propone para tokens de alta entropía; no se usa para contraseñas. Esta entrega solo admite hashes ya calculados y no emite tokens.

Cada emisión tendrá un UUID propio y compartirá `familia_id` con sus sucesoras. En el futuro refresh, una sola transacción deberá localizar el hash, comprobar usuario activo, expiración y revocación, consumir la emisión anterior mediante bloqueo o actualización condicional y crear la siguiente. Dos peticiones concurrentes no deben obtener dos renovaciones válidas. Una reutilización detectada deberá revocar la familia, incluidos sus descendientes, con auditoría sin tokens. Se necesitan pruebas de concurrencia, reutilización, expiración, logout y usuario inactivo antes de publicar ese flujo. La documentación de OAuth 2.0 Security BCP respalda la rotación y la detección de reutilización; no se afirma que ya estén implementadas.

La revocación de refresh no invalida por sí misma un JWT de acceso ya emitido. La política deberá combinar expiración corta con el mecanismo de invalidación que exija el negocio. El repositorio actual solo consulta por hash: los métodos transaccionales de consumo y revocación de familia se implementarán junto al servicio de autenticación, cuando puedan probarse de extremo a extremo.

## Spring Security y futura autenticación

El hallazgo actual está en `security/SecurityConfig.java`: solo se aplica al perfil `dev`, desactiva CSRF y configura `anyRequest().permitAll()`. Por tanto, en ese perfil todos los endpoints de inventario quedan accesibles sin autenticación. Los otros perfiles no utilizan esa cadena explícita y dependen de la autoconfiguración de Spring Security en un contexto web. Las pruebas de persistencia utilizan contexto sin servidor web y no demuestran autorización HTTP.

Las rutas expuestas incluyen `/api/productos`, `/api/categorias`, `/api/proveedores`, `/api/almacenes`, `/api/lotes`, `/api/inventarios/entradas`, `/api/inventarios/salidas`, `/api/inventarios/almacenes/{almacenId}/existencias` y `/api/transferencias`, según los métodos que declara cada controlador. Esta entrega no modifica sus permisos ni elimina la seguridad existente.

En la siguiente fase se deberá definir una cadena de seguridad explícita y coherente entre perfiles: abrir solo las rutas de autenticación que lo requieran y autenticar las demás, con autorización por rol y pertenencia al almacén. Se usará el soporte estándar de Spring Security para verificar JWT y evitar filtros personalizados innecesarios. Las autoridades seguirán una convención única entre códigos y prefijo ROLE_.

El diseño propuesto usa JWT de acceso de duración corta, inicialmente 5–15 minutos sujeto a necesidades del negocio; validación de firma, algoritmo permitido explícitamente, issuer, audience y tiempos; claves fuera del repositorio con rotación y `kid`. No se incluirán datos sensibles en sus claims. Login deberá verificar usuario activo y contraseña con `PasswordEncoder`, devolver errores que no permitan enumerar correos y disponer de límites de intentos y auditoría.

Todo acceso deberá usar HTTPS. Para un navegador se definirá primero si habrá BFF o acceso directo a la API. Si el refresh viaja en cookie, deberá ser HttpOnly, Secure y con SameSite apropiado, y se necesitará protección CSRF de las operaciones correspondientes; no se heredará sin revisión la desactivación actual de CSRF. Se revisarán CORS, almacenamiento del token y exposición a XSS antes de integrar el frontend. `trustServerCertificate=true` es configuración local existente y requiere una decisión de certificados distinta antes de producción.

## Primer administrador: propuesta, sin implementación

Se propone un comando local de bootstrap de un solo uso, habilitado expresamente y fuera del arranque normal y de la API pública. Antes de ejecutarlo se verificará destino, autorización, respaldo y que no existe un administrador. La comprobación y creación tendrán una sola transacción con bloqueo para impedir dos administradores iniciales por ejecuciones concurrentes.

El operador suministrará correo y contraseña mediante entrada interactiva oculta o un gestor de secretos; no habrá valores predeterminados ni argumentos que expongan la contraseña en el historial o lista de procesos. El comando validará la contraseña, la codificará con el encoder acordado y asignará el rol ADMIN existente. Solo registrará el identificador creado y el evento de auditoría, nunca secretos. Si ya existe un administrador, se detendrá; no restablecerá contraseñas ni otorgará roles silenciosamente. Después se retirará el permiso de bootstrap y se habilitará el proceso administrativo normal. No se ha creado ningún usuario real ni implementado este comando.

## Pruebas realizadas

Se seleccionaron clases concretas para evitar ejecutar las integraciones antiguas que escriben y limpian datos en `LM_Medical_Test`.

| Pruebas | Resultado |
|---|---:|
| `SeguridadEntityTest`: normalización, validación, hashes ocultos en JSON, UTC, familia y revocación | 8/8 |
| `V3DdlSqlServerTest`: cuatro tablas y roles, correos y códigos únicos, asignaciones, FK, hashes, identificadores, expiración, revocación y UTC | 20/20 |
| `SeguridadPersistenciaSqlServerTest`: Hibernate validate, repositorios, asignaciones, sesiones, auditoría UTC e historial | 4/4 |
| `FlywayCheckSqlServerTest`: mismo DataSource, tablas originales, segunda migración sin cambios y flujo completo de inventario | 3/3 |
| `V2AppliedSqlServerTest`: historial y restricciones V2 conservados con versiones posteriores | 1/1 |
| `DatabaseConfigurationTest` | 9/9 |
| `ExistenciaTest` | 6/6 |
| `SelectorLotesFefoTest` | 5/5 |
| `SalidaInventarioServiceTest` | 1/1 |
| **Total seleccionado** | **57/57, cero fallos, errores u omitidas** |

Los 20 casos DDL crearon esquemas únicos dentro de transacciones en la base temporal, ejecutaron una copia en memoria de V3 dirigida a esos esquemas y revirtieron todo. Se comprobó la ausencia de esos esquemas antes de la aplicación real. No se borraron tablas con instrucciones de limpieza. Las pruebas JPA y el flujo de inventario también revierten sus datos. SQL Server puede consumir valores identity aun con rollback: no se garantiza una secuencia sin huecos en la base temporal.

Comandos ejecutados desde la raíz, con Java 21 y credenciales reales disponibles únicamente en el entorno del proceso:

```powershell
$env:V3_SQLSERVER_TESTS = 'true'
.\mvnw.cmd -B test '-Dtest=SeguridadEntityTest,V3DdlSqlServerTest,ExistenciaTest,SelectorLotesFefoTest,SalidaInventarioServiceTest,DatabaseConfigurationTest'
# Solo contra LM_Medical_Flyway_Prueba: el perfil flyway-check aplica las pendientes.
.\mvnw.cmd -B test '-Dtest=SeguridadPersistenciaSqlServerTest,FlywayCheckSqlServerTest,V2AppliedSqlServerTest'
.\mvnw.cmd -B flyway:validate flyway:info '-Dflyway.url=jdbc:sqlserver://localhost:1433;databaseName=LM_Medical_Flyway_Prueba;encrypt=true;trustServerCertificate=true'
```

Los dos grupos de pruebas y los objetivos Maven terminaron con BUILD SUCCESS. `flyway:info` devuelve V1, V2 y V3 en estado Success, versión actual 3 y ninguna pendiente. Una segunda llamada a migrate ejecutó cero migraciones y conservó los objetos existentes. No se ejecutó la suite completa, ni pruebas HTTP de autorización, ni autenticación todavía inexistente.

Se actualizaron dos expectativas históricas: la comprobación de 11 tablas ahora cuenta explícitamente las de inventario, y la de V2 acepta una versión actual posterior pero sigue exigiendo su registro y restricciones. No se debilitaron las comprobaciones de inventario.

## Evidencia y estado de las bases

Antes se comprobó V2 en las tres bases mediante consultas de solo lectura. Después se compararon datos ordenados por clave con SHA-256 por tabla, recuentos, identificadores y fechas de las tablas, columnas, claves, restricciones, índices y módulos SQL. Los modelos originales completos coinciden. V1 y V2 conservan su contenido y checksum.

| Base | Historial final verificado | Datos y estructura |
|---|---|---|
| `LM_Medical_Flyway_Prueba` | V1 SQL + V2 SQL + V3 SQL, todos success=1 | 11 tablas de inventario intactas, 0 filas; cuatro tablas nuevas, 3 roles y 0 usuarios, asignaciones y sesiones. |
| `LM_Medical_Test` | BASELINE 1 + V2 SQL + V3 SQL, success=1 | 11 tablas de inventario intactas, 0 filas; cuatro tablas nuevas, 3 roles y 0 usuarios, asignaciones y sesiones. Historial anterior conservado. |
| `LM_Medical_supplies` | BASELINE 1 + V2 SQL + V3 SQL, success=1 | 11 tablas y 26 filas de inventario intactas; cuatro tablas nuevas, 3 roles y 0 usuarios, asignaciones y sesiones. Historial anterior conservado. |

No se conectó a `LM_Medical_Flyway_V2_Equivalentes` en esta entrega ni se creó otra base. No se cambiaron permisos SQL. Durante la implementación inicial las consultas a las bases principales fueron exclusivamente de lectura. La aplicación posterior autorizada añadió únicamente V3 en ambas, según el registro siguiente.

Registro independiente de V3 en la base temporal: installed_rank=3, version=3, type=SQL, script=`V3__usuarios_roles_seguridad.sql`, success=1, checksum=`-1733036323`, installed_on=`2026-10-18 18:43:23.913`. En Test se registró a las `2026-10-18 18:51:39.870` y en desarrollo a las `2026-10-18 18:52:35.287`, con el mismo script, versión, tipo, checksum y success=1.

SHA-256 de V3: `81923F520A35B274AC5FDB7CAD02880B4C58B81DB7BD6D241559DE589F90CB1B`.

Evidencias sin credenciales ni contenido de filas: `target/security-v3-verification/` contiene snapshots antes/después, resultado de comparación, informe SQL y los nueve reportes de pruebas seleccionadas. `target` no se versiona. No se modificaron POM, properties, SecurityConfig, V1 o V2; se verificaron sus hashes contra la captura inicial.

No hubo fallos en los grupos seleccionados. Maven mostró avisos HTTP 401 al descubrir prefijos en dos repositorios opcionales; no impidieron compilar, migrar ni validar y no son rechazos de credenciales SQL. Mockito mostró el aviso existente sobre la carga dinámica de su agente.

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

## Archivos de esta entrega y siguiente paso

Se crearon cuatro entidades, sus cuatro repositorios, V3, `SeguridadEntityTest`, `V3DdlSqlServerTest`, `SeguridadPersistenciaSqlServerTest` y este documento. Se modificaron solamente `FlywayCheckSqlServerTest`, `V2AppliedSqlServerTest` y `docs/FLYWAY.md` entre los archivos preexistentes.

La persistencia está comprobada en la base temporal y V3 ya está aplicada y validada en Test y desarrollo con sus autorizaciones y respaldos confirmados. El siguiente paso es implementar y probar encoder, bootstrap explícito, login, JWT y rotación de refresh transaccional, y acordar/aplicar la matriz de permisos de inventario. Crear tablas y roles no protege todavía los endpoints.

## Referencias de diseño

- [Spring Security: almacenamiento de contraseñas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html): PasswordEncoder y formato identificable del algoritmo.
- [OWASP: Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): elección del algoritmo y límites de BCrypt.
- [RFC 9700: OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html): protección de refresh tokens y detección de reutilización.
