# Corrección de errores de usuarios y validación

Se corrigieron dos errores 500 del registro:

- `CambiarEstadoRequest.activo` era Boolean con NotBlank, válido solo para texto. Ahora usa NotNull: true y false son válidos; null o un campo ausente devuelven 400.
- La consulta JPQL esperaba :usuarioIds, pero el repositorio declaraba @Param("usuariosIds"). Ahora usa el mismo nombre y se verificó contra SQL Server con una colección no vacía y con un ID sin asignaciones.

El error de Content-Type conserva HTTP 415 y ahora explica cómo enviarlo correctamente. En Postman seleccionar Body → raw → JSON y Content-Type: application/json. Para PATCH /api/usuarios/{id}/estado:

```json
{"activo": false}
```

Enviar también el Bearer token válido del administrador. El 401 por credenciales rechazadas es un resultado de autenticación esperado; no se eliminaron validaciones ni se modificaron permisos. La regla que impide desactivar la propia cuenta se conserva y puede producir 409 después de validar el JSON.

El aviso de PageImpl se resolvió con WebPaginationConfiguration y VIA_DTO. Los listados que devuelven Page (productos, categorías, almacenes, proveedores y existencias por almacén) ahora usan la representación de Spring Data:

```json
{
  "content": [],
  "page": {"size": 20, "totalElements": 0, "totalPages": 0, "number": 0}
}
```

Los clientes deben leer los metadatos dentro de page. GET /api/usuarios mantiene su DTO existente: usuarios, totalElementos, pagina, tamano y totalPaginas.

Se añadieron UsuarioValidacionHttpTest (6 casos) y UsuariosConsultaSqlServerTest (4 casos de solo lectura). AuthLoginFlowTest pasó sus 5 casos. Total: 15/15, sin fallos, errores ni omitidas; BUILD SUCCESS con Java 21. Se actualizaron dos pruebas antiguas que todavía llamaban a Usuario.desactivar() para usar cambiarEstado(false). No se ejecutó toda la suite.

Las pruebas de estado usan un servicio simulado y no desactivan cuentas reales. Las de listado y paginación consultan LM_Medical_supplies, verifican SELECT DB_NAME() y utilizan transacciones de solo lectura. El bootstrap, Flyway y SQL init permanecieron desactivados. Las capturas antes/después coinciden exactamente: usuarios, roles, inventario, metadatos e historial intactos. SecurityConfig y V1, V2 y V3 conservan sus hashes.

Evidencias sin credenciales ni contenido de filas: target/usuarios-errores-verificacion/.

Referencia: [Spring Data: representación JSON de Page](https://docs.spring.io/spring-data/commons/reference/repositories/core-extensions.html#core.web.pageables).