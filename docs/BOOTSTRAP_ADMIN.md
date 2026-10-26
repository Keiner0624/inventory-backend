# Primer administrador: preparación y autorización

Revisión del 24 de octubre de 2026 (Lima). Código corregido y cuenta inicial **creada tras la aprobación explícita del propietario**. El login real y el JWT se verificaron; el bootstrap queda desactivado.

## Creación autorizada y verificación final

El propietario respondió expresamente «Sí, crear ADMIN y comprobar login» para localhost:1433 / LM_Medical_supplies. Después de comprobar de nuevo DB_NAME(), V3 success=1 y cero usuarios/asignaciones, se ejecutó el bootstrap real una sola vez usando los valores existentes del entorno local. No se inventó ni mostró la contraseña.

La consulta SQL independiente posterior confirma **un usuario activo, una asignación ADMIN y tres roles** en LM_Medical_supplies; se comprobó el formato BCrypt sin seleccionar ni mostrar su hash. POST /api/auth/login devolvió **HTTP 200** con las credenciales reales configuradas. El JwtDecoder de la aplicación verificó firma, issuer, audience, tipo access, subject correspondiente, ROLE_ADMIN y expiración. Otra petición con contraseña incorrecta devolvió **HTTP 401**. No se imprimieron tokens ni cuerpos de login.

La ejecución usó un servidor temporal en 127.0.0.1 y un puerto aleatorio, cerrado automáticamente después de verificar. Para utilizar Postman en el puerto habitual 8080, iniciar normalmente InventoryBackendApplication con dev y JWT activo. El bootstrap ya está desactivado: no volver a activarlo para hacer login.

Se dejó BOOTSTRAP_ADMIN_ENABLED=false en IntelliJ y se retiraron de esa ejecución las variables AUTHORIZED, DATABASE, NOMBRE, APELLIDO, CORREO y PASSWORD del bootstrap. Se conservaron las credenciales SQL y la clave JWT. Eliminar las variables iniciales del proceso normal no modifica la contraseña almacenada en BCrypt.

Inventario: las 11 tablas y 26 filas originales de la principal conservan sus metadatos, recuentos y SHA-256. Solo cambiaron usuarios (0→1) y usuario_rol (0→1). Roles, sesiones e historial de Flyway no cambiaron. Test y la temporal mantienen cero usuarios/asignaciones, tres roles y sus datos idénticos. V1, V2 y V3 conservan sus hashes; no se ejecutó ninguna migración.

Evidencias sin secretos: `target/admin-bootstrap-verification/20261024-authorized-creation/` contiene authorization.json, application.log, creation-and-login.json, snapshots finales y verification.json. El verificador manual reside fuera de src y no se incorporó a la suite automatizada que podría crear cuentas por accidente.

## Causa comprobada

El archivo real es `security/AdminInicialBootstraps.java`. Sí era un `@Component`, implementaba `ApplicationRunner` y tenía `@Profile("dev")`, pero su condición consultaba `app.bootstrap.admin.enable`. `application-dev.properties` define `app.bootstrap.admin.enabled=${BOOTSTRAP_ADMIN_ENABLED:false}`. La diferencia `enable`/`enabled` impedía registrar el runner y explica que Spring arrancara sin mostrar el mensaje del bootstrap.

Las variables de credenciales estaban presentes en la ejecución principal de IntelliJ y el perfil era dev. Se revisó su presencia sin mostrar sus valores. Las consultas reales a SQL Server confirmaron el destino `LM_Medical_supplies`, V3 aplicada, cero usuarios y asignaciones y los roles ADMIN, EMPLEADO y ENCARGADO_ALMACEN. En ese estado un login de cuenta inexistente debe responder 401. No se encontró evidencia de un fallo de transacciones en el código original: la condición impedía que llegara a ejecutarse.

Antes de corregir la condición se cambió únicamente `BOOTSTRAP_ADMIN_ENABLED` a `false` en la configuración local de IntelliJ, conservando sus credenciales. Así una recompilación no activa accidentalmente la creación. La clave JWT existente cumplía el mínimo de 32 bytes decodificados; la contraseña inicial existente cumplía la política de longitud. No se generó ni inventó una contraseña inicial.

## Correcciones y límites

- `AdminInicialBootstraps`: condición `enabled`, desactivada por defecto y limitada a dev. Exige autorización independiente, destino explícito y JWT activo. Delega la creación y solo anuncia éxito después de que el servicio confirme la transacción.
- `AdminInicialService`: único servicio de persistencia del bootstrap, con `@Transactional(rollbackFor=Exception.class)`. Antes de escribir ejecuta `SELECT DB_NAME()` y exige `LM_Medical_supplies`. Valida variables, política de contraseña y entidad, recupera o crea el rol ADMIN y guarda usuario y asignación con flush.
- Un bloqueo `sp_getapplock` exclusivo, propiedad de la transacción, serializa dos ejecuciones cooperantes. Antes de pedirlo se lee la tabla usuarios y se verifica `@@TRANCOUNT`: una consulta escalar `SELECT DB_NAME()` no inicia por sí sola una transacción implícita en SQL Server. No se gestiona un COMMIT independiente ni se usa SET NOCOUNT ON.
- Si ya existe un usuario con ADMIN, incluso inactivo, se omite la creación. Un correo ocupado sin ADMIN se rechaza: no se promueve ni se reemplaza una cuenta. Un error al asignar el rol revierte también el usuario y cualquier rol recién creado. La UNIQUE del correo y la PK compuesta siguen siendo la defensa de integridad SQL.
- No se imprimen correos, contraseñas, hashes ni tokens. Los errores de validación informan nombres de variables o un mensaje genérico. No hay endpoint de bootstrap.
- `UsuarioRolRepository`: se añadió únicamente `existsByRol_Codigo` para detectar administradores existentes.
- `JwtConfig` conserva la validación HS256, issuer, audience, token_type y expiración. La clase duplicada `JwtSecurity`, que usaba `enable`, `audince` y `token-type`, queda obsoleta y sin anotaciones Spring ni beans. Se conserva solo como nombre de clase de compatibilidad; la configuración activa única es JwtConfig.
- `AuthController` se registra cuando `app.jwt.enabled=true`, igual que JwtConfig. Si JWT está desactivado, login no está disponible; no se genera una clave insegura ni se omiten validaciones para arrancar.
- `SecurityConfig` conserva exactamente sus permisos previos. Su permitAll del perfil dev no protege todavía el inventario. `server.address=127.0.0.1` en dev limita el servidor embebido al equipo local. No deben añadirse redirecciones de puertos ni proxies que lo publiquen.

## Variables y activación de una sola vez

Las propiedades se resuelven desde variables de entorno de **la configuración que se ejecuta**. IntelliJ no transmite las variables de una configuración Application a una configuración JUnit independiente.

| Variable | Uso |
|---|---|
| `BOOTSTRAP_ADMIN_ENABLED` | Activa el runner; valor predeterminado false. |
| `BOOTSTRAP_ADMIN_AUTHORIZED` | Confirma la escritura ya aprobada por el propietario; predeterminado false. |
| `BOOTSTRAP_ADMIN_DATABASE` | Confirmación exacta del destino: LM_Medical_supplies; predeterminado vacío. |
| `BOOTSTRAP_ADMIN_NOMBRE`, `BOOTSTRAP_ADMIN_APELLIDO` | Obligatorios; hasta 100 caracteres. |
| `BOOTSTRAP_ADMIN_CORREO` | Obligatorio, válido, normalizado y sin duplicados. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Obligatoria, al menos 12 caracteres y como máximo 72 bytes UTF-8; sin truncar ni quitar espacios de la contraseña. |
| `JWT_ENABLED`, `JWT_SECRET_BASE64` | JWT activo y clave Base64 que decodifique al menos 32 bytes. |
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales existentes del DataSource. |

No configurar contraseñas en archivos versionados, argumentos de JVM, migraciones ni historial de comandos. Los valores actuales no se incluyen en este documento. La configuración `.idea` local está ignorada; debe protegerse porque puede contener secretos del operador.

**Solo después de la aprobación explícita:**

1. En IntelliJ, seleccionar InventoryBackendApplication con Java 21 y `SPRING_PROFILES_ACTIVE=dev`.
2. Confirmar destino `jdbc:sqlserver://localhost:1433;databaseName=LM_Medical_supplies;encrypt=true;trustServerCertificate=true`, V3 success=1 y ausencia de otro ADMIN. Flyway y SQL init deben permanecer desactivados.
3. Mantener las credenciales iniciales existentes en el entorno local y establecer `BOOTSTRAP_ADMIN_ENABLED=true`, `BOOTSTRAP_ADMIN_AUTHORIZED=true` y `BOOTSTRAP_ADMIN_DATABASE=LM_Medical_supplies`. No enviar la contraseña al chat ni registrarla en consola.
4. Iniciar una sola vez. El mensaje de creación se emite después del commit. Si aparece un error, detenerse y revisar la causa; no repetir con otras credenciales ni desactivar protecciones.
5. Inmediatamente después, poner `BOOTSTRAP_ADMIN_ENABLED=false`, quitar `BOOTSTRAP_ADMIN_AUTHORIZED` y `BOOTSTRAP_ADMIN_DATABASE` y retirar las variables de credenciales del bootstrap de la ejecución normal. Reiniciar la aplicación con JWT activo.
6. Verificar mediante consultas de solo lectura que existe una cuenta activa y su asignación ADMIN, sin seleccionar password_hash. Comprobar el login real con la contraseña suministrada localmente, validar la firma y claims del token sin imprimirlo y conservar solo el resultado de la comprobación.

Si se vuelve a activar por error tras crear un ADMIN, el bloqueo y la comprobación del rol evitan crear otro. Esto no sustituye desactivar el mecanismo ni constituye un procedimiento de recuperación de administradores.

## Pruebas y estado antes de la escritura autorizada

| Pruebas | Correctas |
|---|---:|
| `DatabaseConfigurationTest` | 9 |
| `AdminBootstrapLockSqlServerTest` | 1 |
| `AuthLoginReadOnlySqlServerTest` | 1 |
| `AdminInicialBootstrapTest` | 6 |
| `AdminInicialServiceTest` | 13 |
| `AuthLoginFlowTest` | 5 |
| `JwtConfigTest` | 4 |
| `JwtServiceTest` | 4 |
| `PasswordEncoderConfigTest` | 1 |
| `AuthServiceTest` | 5 |
| **Total seleccionado** | **49/49; cero fallos, errores u omitidas** |

BUILD SUCCESS con Java 21, bootstrap y Flyway forzados a false. No se ejecutó la suite completa. La primera prueba de bloqueo detectó la ausencia de transacción SQL efectiva y motivó la lectura previa y el control de @@TRANCOUNT; la ejecución final pasó.

| Base | Usuarios / asignaciones / roles | Inventario |
|---|---|---|
| `LM_Medical_supplies` | 0 / 0 / 3 | 26 filas originales, intactas |
| `LM_Medical_Test` | 0 / 0 / 3 | 0 filas originales, intactas |
| `LM_Medical_Flyway_Prueba` | 0 / 0 / 3 | 0 filas originales, intactas |

Evidencias sin credenciales ni contenido de filas: `target/admin-bootstrap-verification/20261024-preparation/`.


Las pruebas unitarias simulan creación y rollback: sus mensajes de éxito corresponden a mocks y **no acreditan una escritura real en la principal**. La prueba con SQL Server comprueba el bloqueo y su liberación por rollback únicamente en la base temporal; no crea usuarios. El rollback de creación/asignación se verifica mediante el interceptor transaccional Spring y un gestor simulado. No se presenta como una prueba de escritura real en SQL Server.

La prueba HTTP real arranca un servidor local en un puerto aleatorio con el perfil dev, bootstrap forzado a false, Flyway desactivado y Hibernate validate. Consulta `DB_NAME()` y envía un usuario inexistente aleatorio: debe obtener 401 sin crear datos. El éxito HTTP 200, BCrypt y emisión/validación JWT se prueban en MockMvc con repositorios simulados y los componentes reales de autenticación. **La posterior verificación autorizada de la cuenta real devolvió HTTP 200 y validó el JWT, según el estado final anterior.**

Las 11 tablas de inventario, datos e historial se comparan antes/después mediante recuentos, SHA-256 y metadatos. No se ejecutaron migraciones, operaciones de limpieza ni pruebas antiguas que modifican inventario. V1, V2 y V3 mantienen sus hashes.

## Archivos

Modificados: `security/AdminInicialBootstraps.java`, `repository/UsuarioRolRepository.java`, `controller/AuthController.java`, `security/JwtSecurity.java`, `application-dev.properties`, `.idea/workspace.xml` (solo activación local) y `docs/SEGURIDAD.md`.

Creados: `security/AdminInicialService.java`, este documento y las pruebas `AdminInicialBootstrapTest`, `AdminInicialServiceTest`, `AuthLoginFlowTest`, `JwtConfigTest`, `AdminBootstrapLockSqlServerTest` y `AuthLoginReadOnlySqlServerTest`. No se modificaron entidades, PasswordEncoderConfig, UsuarioDetailsService, AuthenticationConfig, AuthService, JwtConfig, JwtService, SecurityConfig, POM ni migraciones.

## Protección JWT pendiente

El login verifica credenciales a través de AuthService → AuthenticationManager → DaoAuthenticationProvider → UsuarioDetailsService → SQL Server y PasswordEncoder; JwtService emite el access token. Esto no autentica automáticamente las demás rutas.

La siguiente fase debe configurar el recurso OAuth2 con el JwtDecoder existente, extraer las autoridades de roles y requerir autenticación en inventario. Después se acordará la autorización por rol y almacén, aún fuera de esta entrega. Se necesitan pruebas HTTP de acceso anónimo, firma, issuer, audience, expiración, roles y alcance por almacén, además de CORS, HTTPS y la decisión de CSRF según el transporte. La rotación de refresh continúa pendiente. No se amplió permitAll para resolver el login.

Referencias: [transacciones declarativas Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html), [sp_getapplock](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-getapplock-transact-sql?view=sql-server-ver17), [transacciones implícitas SQL Server](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-implicit-transactions-transact-sql?view=sql-server-ver17).
