# Cierre de sesión desde Postman

## Corrección del 2026-10-25

El código tenía el servicio de cierre de sesión, pero faltaba exponerlo en `AuthController`. Además, `SecurityConfig` solo permitía sin access token los POST de login y refresh: logout caía en la regla general de inventario, que exige autenticación y rol ADMIN o ENCARGADO_ALMACEN. Un refresh token nuevo en el JSON no satisface esa regla.

Se añadió `POST /api/auth/logout`, que valida `RefreshTokenRequest`, llama a `AuthSessionService.logout` y responde **204 No Content**, sin cuerpo y con `Cache-Control: no-store`.

La ruta de logout utiliza el refresh token del cuerpo para identificar la sesión. No exige rol ni access token. Solo para ese método y esa ruta, el resolvedor de Bearer omite el encabezado Authorization: un access token heredado, inválido o vencido no impide cerrar la sesión. Los filtros JWT y los permisos de las demás rutas se conservan.

## Petición

Después de reiniciar el backend con el código corregido:

- Método: `POST`.
- URL: `http://localhost:8080/api/auth/logout`.
- Authorization en Postman: `No Auth`.
- Body: `raw`, tipo `JSON`; encabezado `Content-Type: application/json`.

```json
{
  "refreshToken": "PEGA_AQUI_EL_REFRESH_TOKEN_MAS_RECIENTE"
}
```

Si se utilizó `/api/auth/refresh`, usar el nuevo `refreshToken` de esa respuesta. No pegar el access token, el prefijo `Bearer` ni el hash almacenado en SQL Server en este campo.

Un cierre correcto devuelve 204 sin JSON. La renovación con el refresh de la sesión cerrada debe devolver 401. El servicio conserva su comportamiento idempotente: repetir el cierre o presentar un token de formato válido que ya no existe puede devolver 204. Un formato de token incorrecto devuelve 401; un campo vacío o ausente devuelve 400.

## Verificación

`AuthLogoutHttpTest` ejecuta la cadena real de Spring Security, el controlador y los servicios con JWT firmados y repositorios simulados. Comprueba cierre de EMPLEADO sin access token; cierre tras rotación; encabezado Bearer inválido; JWT realmente vencido; idempotencia; aislamiento entre familias; rechazo posterior de acceso y renovación; validación del cuerpo; y conservación de permisos en otras rutas.

También se actualizaron `AuthLoginFlowTest` y `RefreshTokenServiceTest` para las firmas actuales del controlador y del servicio, que habían cambiado desde la revisión anterior.

Estas pruebas no conectan a SQL Server ni crean o revocan sesiones reales. La persistencia y la concurrencia de cierre contra SQL Server no se han comprobado en esta corrección. No se modificaron migraciones ni credenciales.
