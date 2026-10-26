-- Historial de operaciones confirmadas. No altera las tablas de negocio.
SET XACT_ABORT ON;
IF OBJECT_ID(N'dbo.eventos_auditoria') IS NOT NULL
    THROW 51300, 'V4: existe eventos_auditoria sin esta migracion; revisar antes de continuar.', 1;

CREATE TABLE dbo.eventos_auditoria (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_eventos_auditoria PRIMARY KEY,
    fecha DATETIMEOFFSET(7) NOT NULL,
    usuario_id BIGINT NOT NULL,
    usuario_identificador VARCHAR(254) NOT NULL,
    accion VARCHAR(40) NOT NULL,
    entidad VARCHAR(30) NOT NULL,
    entidad_id BIGINT NOT NULL,
    almacen_id BIGINT NULL,
    descripcion NVARCHAR(200) NOT NULL,
    detalles NVARCHAR(MAX) NULL,
    CONSTRAINT CK_eventos_auditoria_utc CHECK (DATEPART(TZOFFSET, fecha) = 0),
    CONSTRAINT CK_eventos_auditoria_ids CHECK (usuario_id > 0 AND entidad_id > 0 AND (almacen_id IS NULL OR almacen_id > 0)),
    CONSTRAINT CK_eventos_auditoria_identidad CHECK (LEN(LTRIM(RTRIM(usuario_identificador))) > 0),
    CONSTRAINT CK_eventos_auditoria_json CHECK (detalles IS NULL OR ISJSON(detalles) = 1)
);
-- Sin FKs: conserva la identidad histórica independientemente del estado de los catálogos.
CREATE INDEX IX_eventos_auditoria_fecha ON dbo.eventos_auditoria (fecha DESC, id DESC);
CREATE INDEX IX_eventos_auditoria_usuario_fecha ON dbo.eventos_auditoria (usuario_id, fecha DESC, id DESC);
CREATE INDEX IX_eventos_auditoria_accion_fecha ON dbo.eventos_auditoria (accion, fecha DESC, id DESC);
CREATE INDEX IX_eventos_auditoria_entidad_fecha ON dbo.eventos_auditoria (entidad, entidad_id, fecha DESC, id DESC);
CREATE INDEX IX_eventos_auditoria_almacen_fecha ON dbo.eventos_auditoria (almacen_id, fecha DESC, id DESC);
-- Defensa adicional para cuentas de aplicación sin privilegios de propietario/sysadmin.
DENY UPDATE, DELETE ON OBJECT::dbo.eventos_auditoria TO public;
