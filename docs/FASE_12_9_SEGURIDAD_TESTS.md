# Fase 12.9 — Suite de regresión de seguridad

Fecha: 25 de octubre de 2026. Proyecto existente `inventory-backend`, Java 21.0.12.1, Spring Boot 4.1.1, Spring Security 7.1.1 y SQL Server 17.

## Resultado

| Ejecución | Clases | Casos ejecutados | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|---:|
| Unitarias y MockMvc sin SQL | 22 | 139 | 0 | 0 | 0 |
| Integración real SQL Server | 1 | 37 | 0 | 0 | 0 |
| Total de casos distintos | 23 | 176 | 0 | 0 | 0 |

Se añadieron **55 casos nuevos**: 10 del conversor JWT, 8 de administración y 37 de integración. Los parámetros y las tres repeticiones de concurrencia cuentan como casos ejecutables. Las ejecuciones de diagnóstico y las repeticiones de una suite no se suman al total de casos distintos.

Los informes finales están en `target/security-phase129/unit/` y `target/security-phase129/sqlserver/`. El resumen de integridad y resultados está en `target/security-phase129/evidence.json`.

## Archivos creados

| Archivo | Responsabilidad |
|---|---|
| `src/test/java/com/lmmedical/inventory/security/RolesActualesJwtConverterTest.java` | Roles actuales frente a claims antiguos, `sid` inválido o ausente, usuario inactivo o inexistente y familia inválida. |
| `src/test/java/com/lmmedical/inventory/service/UsuarioAdministracionTest.java` | Protección de la propia cuenta, condición de último ADMIN, actor sin permisos y roles inválidos o ausentes del catálogo. |
| `src/test/java/com/lmmedical/inventory/integration/SecuritySqlServerTest.java` | HTTP con filtros reales, JPA, transacciones y bloqueos reales, exclusivamente en Test. |
| `scripts/test/Invoke-SecurityTests.ps1` | Java 21, credenciales presentes cuando corresponden, selección de suite y comprobación de resultados. |
| `docs/FASE_12_9_SEGURIDAD_TESTS.md` | Alcance, comandos, hallazgos y evidencias. |

## Archivos modificados y correcciones

- `RolesActualesJwtConverter.java`: se sustituyó `assert sid != null` por una validación explícita. La prueba nueva reprodujo un `AssertionError` con aserciones Java activas; ahora el caso produce un error de autenticación y HTTP 401. Los permisos siguen leyéndose de la base de datos en cada petición.
- `SecurityConfig.java`: la cadena de seguridad se aplica a todos los contextos web servlet, en lugar de activarse exclusivamente en `dev`. Antes, otro perfil no cargaba la matriz de permisos de la API. Se conservaron las reglas, la autenticación JWT y las rutas públicas concretas de login, refresh y logout. El comportamiento se verificó en un contexto web `prod` sin base de datos y con la aplicación real en perfil `test`.
- `AuthController.java`: las respuestas 401 de autenticación también llevan `Cache-Control: no-store`.
- `AuthLogoutHttpTest.java`: constructor actualizado con el `EntityManager` que exige el código actual; el perfil `prod` prueba que la cadena de seguridad no depende de `dev`.
- `RefreshTokenServiceTest.java`: constructor actualizado con `EntityManager` simulado. No se cambió la lógica productiva de refresh.
- `pom.xml`: la ejecución normal excluye las integraciones antiguas y el arranque completo por defecto, que requieren bases y algunas ejecutan DDL, migraciones o limpieza. El perfil explícito `security-sqlserver` ejecuta únicamente `SecuritySqlServerTest`. Las carpetas de informes quedan configuradas en Maven.
- `docs/SEGURIDAD.md`: enlace al estado actual; se preservó la documentación histórica.

No se reconstruyeron servicios, no se cambiaron las credenciales ni se modificaron los archivos de migraciones.

## Cobertura comprobada

| Área | Evidencia |
|---|---|
| Login | Credenciales válidas, contraseña incorrecta, correo inexistente, usuario inactivo o sin roles; tokens de acceso y refresh; ausencia de contraseñas y hashes en respuestas. |
| Persistencia de refresh | Usuario correcto, hash SHA-256 en SQL, formato de 43 caracteres, expiración de siete días y emisiones diferentes. El generador existente utiliza `SecureRandom` con 32 bytes. |
| JWT | Acceso válido; petición sin token; firma, expiración, `sid`, emisor, audiencia o tipo incorrectos; familia ajena; usuario desactivado; sesión expirada o revocada. |
| Permisos | ADMIN puede consultar y crear empleados; EMPLEADO y ENCARGADO no pueden consultar, crear, desactivar ni cambiar roles de usuarios. Cambiar roles afecta a la siguiente petición con el mismo JWT. |
| Administración | Auto desactivación y retirada del propio ADMIN rechazadas; roles inexistentes sin cambios parciales; condición de último ADMIN probada en unidad. |
| Rollback administrativo | Se provoca un fallo después de enviar a SQL Server una modificación de asignaciones de un fixture. Una consulta posterior confirma que los roles anteriores permanecen intactos. |
| Rotación | A se revoca, B conserva la familia y queda como única emisión activa. Tokens expirados, inexistentes, mal formados y usuarios desactivados reciben 401 sin nueva emisión. |
| Reutilización | Tras renovar A a B, reutilizar A recibe 401, confirma la revocación de la familia, impide renovar B y rechaza ambos access tokens. |
| Concurrencia | Tres carreras independientes con dos solicitudes del mismo refresh: exactamente un 200 y un 401, dos emisiones totales y ninguna activa al finalizar por la política de reutilización. |
| Logout | 204 sin cuerpo, revocación de la familia, JWT rechazado después, aislamiento entre sesiones y usuarios, idempotencia y nuevo login permitido. Los tests MockMvc existentes también cubren el refresh recién rotado y un Bearer inválido o vencido. |
| Caché | Respuestas de autenticación exitosas y fallidas con `no-store`; DTO sin secretos en `toString()`. |

### Transacciones y concurrencia reales

La clase SQL **no tiene `@Transactional` en el test**. Las peticiones atraviesan los proxies transaccionales de los servicios; las consultas de verificación se ejecutan después de terminar cada petición, fuera de su transacción. Así se comprueba que la revocación se confirmó y no solo que existe en una entidad en memoria.

El código actual ya contenía `noRollbackFor = ResponseStatusException.class` tanto en `AuthSessionService.renovar` como en `RefreshTokenService.renovar`, además de `EntityManager.refresh` tras los bloqueos. Se conservaron y se verificó su efecto en SQL Server. No hizo falta reimplementar la detección de reutilización.

La barrera de concurrencia espera a que ambos hilos completen la consulta real inicial antes de continuar. El observador de prueba llama al repositorio real y no cambia resultados, transacciones ni bloqueos. La serialización y el estado final dependen de SQL Server; no se simulan con Mockito. La espera tiene límites de tiempo y no usa `Thread.sleep`.

El único spy de repositorio en esta clase se usa para inyectar el fallo administrativo y comprobar rollback. En los demás escenarios delega a las operaciones reales.

## Protección de datos y estado final

- Destino de todas las escrituras de integración: **`localhost:1433 / LM_Medical_Test`**. URL fijada en el test y `DB_NAME()` comprobado antes de crear fixtures y antes de las escrituras y del cierre de fixtures.
- Hibernate permaneció en `validate`; Flyway, inicialización SQL y bootstrap ADMIN estuvieron desactivados.
- Los usuarios de prueba llevan un correo único `fase129-...@example.invalid`. Al finalizar se desactivan sus cuentas y se revocan sus sesiones, sin borrar los fixtures. Las modificaciones de asignaciones de roles solo afectaron a estos usuarios de prueba mediante el servicio existente.
- Quedaron **84 usuarios de prueba desactivados**, 82 asignaciones y **81 sesiones revocadas** entre los dos intentos SQL. Cero usuarios ajenos a la suite en Test. Los tres roles originales se conservaron.
- Se compararon antes y después las huellas de todas las filas de la base principal: **15 tablas, 45 filas de aplicación e historial de Flyway idénticos**. La base principal solo se consultó para esta verificación; no se ejecutaron tests de escritura en ella.
- En Test se conservaron las 15 tablas, los datos originales de inventario y roles y el historial V1–V3. Sus cambios corresponden a los fixtures de usuarios, asignaciones y sesiones.
- Los SHA-256 de V1, V2 y V3 coinciden con los iniciales. No se ejecutaron migraciones, `clean`, eliminación de tablas ni limpieza destructiva.

## Fallos encontrados durante la implementación

1. La compilación inicial falló por dos tests que seguían usando el constructor de tres parámetros de `RefreshTokenService`; el servicio real ahora exige cuatro. Se actualizaron los tests.
2. El test nuevo de `sid` ausente reprodujo un fallo de producción bajo `-ea`: `AssertionError` en lugar de rechazo de autenticación. Se corrigió sin relajar la validación.
3. El primer intento SQL ejecutó 37 casos y registró 36 errores, principalmente porque los fixtures usaban fechas con offset local en una base que exige UTC. También falló la sincronización de la carrera al intentar `callRealMethod()` sobre una consulta abstracta de Spring Data. Se corrigieron los fixtures a UTC y se reemplazó ese spy por el observador de consultas reales. No se alteraron restricciones ni migraciones.
4. Se fijaron explícitamente las carpetas de informes en el POM: el parámetro de consola empleado inicialmente no cambiaba por sí solo ese ajuste de Surefire.

Los informes de diagnóstico se conservaron en `target/security-phase129/initial-failures/`. La ejecución final no tiene fallos, errores ni casos omitidos. Los avisos de metadatos Maven HTTP 401, instrumentación de Mockito y configuración explícita del proveedor de autenticación no bloquearon las pruebas; no se ocultaron mediante cambios de seguridad.

## Ejecución reproducible

Desde la raíz del proyecto, con Java 21 y las credenciales SQL existentes configuradas como variables de entorno cuando se utilice SQL. No escribir contraseñas en comandos ni archivos versionados.

```powershell
# Sin conexiones SQL: unitarias y seguridad MockMvc.
.\scripts\test\Invoke-SecurityTests.ps1 -JavaHome 'C:\Users\User\.jdks\ms-21.0.12.1'

# Solo LM_Medical_Test; conserva fixtures desactivados y revocados.
.\scripts\test\Invoke-SecurityTests.ps1 -SqlServer -JavaHome 'C:\Users\User\.jdks\ms-21.0.12.1'
```

Equivalentes Maven, con `JAVA_HOME` apuntando a Java 21:

```powershell
.\mvnw.cmd -B test
.\mvnw.cmd -B test -Psecurity-sqlserver
```

En la verificación local, `DB_USERNAME` y `DB_PASSWORD` se cargaron temporalmente desde la configuración existente de IntelliJ, sin mostrarlas y restaurando el entorno al finalizar. La clave JWT de la suite SQL se genera para el proceso de prueba y no utiliza ni cambia la clave de la aplicación.

Si Test no existe, faltan credenciales, no coincide el esquema o `DB_NAME()` no es el autorizado, la ejecución falla antes de crear fixtures. No se prepara ni migra automáticamente la base. El script comprueba el código de salida de Maven y rechaza informes fallidos, vacíos u omitidos.

## Límites y siguiente fase

Las integraciones antiguas siguen en el repositorio y no se ejecutaron en esta fase: algunas apuntan a bases temporales de migraciones o realizan limpieza de datos. La ejecución normal ahora las mantiene fuera de la selección; no se afirma que hayan pasado. Las pruebas unitarias y MockMvc anteriores sí se ejecutaron junto con las nuevas.

La prueba `prod` valida la cadena web y los permisos, no un despliegue completo de producción. `DatabaseConfiguration` aún exige uno de los perfiles de base `dev`, `test` o `flyway-check`; falta definir formalmente la configuración y los certificados del entorno productivo.

El siguiente paso es incorporar ambos comandos a CI con un SQL Server de pruebas dedicado y las credenciales en el almacén de secretos de CI. Después conviene ampliar las carreras a logout frente a renovación y cambios simultáneos de roles o estado. Debe acordarse una política autorizada para la retención de fixtures antes de añadir cualquier limpieza que borre registros.
