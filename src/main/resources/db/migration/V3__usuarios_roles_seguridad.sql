-- Fase 12: solo persistencia. No crea usuarios ni credenciales.
-- Flyway/SQL Server mantienen una transaccion para toda la migracion.
SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.usuarios') IS NOT NULL OR OBJECT_ID(N'dbo.roles') IS NOT NULL
   OR OBJECT_ID(N'dbo.usuario_rol') IS NOT NULL OR OBJECT_ID(N'dbo.sesiones_refresh') IS NOT NULL
    THROW 51100, 'V3: existe un objeto de seguridad sin esta migracion registrada; revisa el esquema antes de continuar.', 1;

CREATE TABLE dbo.usuarios (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_usuarios PRIMARY KEY,
    nombre NVARCHAR(100) NOT NULL,
    apellido NVARCHAR(100) NOT NULL,
    correo VARCHAR(254) COLLATE Latin1_General_100_BIN2 NOT NULL,
    password_hash VARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
    activo BIT NOT NULL CONSTRAINT DF_usuarios_activo DEFAULT (1),
    fecha_creacion DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_usuarios_fecha_creacion DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')),
    fecha_actualizacion DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_usuarios_fecha_actualizacion DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')),
    CONSTRAINT UQ_usuarios_correo UNIQUE (correo),
    CONSTRAINT CK_usuarios_nombre CHECK (LEN(LTRIM(RTRIM(nombre))) > 0),
    CONSTRAINT CK_usuarios_apellido CHECK (LEN(LTRIM(RTRIM(apellido))) > 0),
    CONSTRAINT CK_usuarios_correo CHECK (
        correo = LOWER(correo) AND DATALENGTH(correo) = DATALENGTH(LTRIM(RTRIM(correo)))
        AND correo NOT LIKE '%[^!-~]%' AND correo LIKE '%_@_%'),
    -- Formato DelegatingPasswordEncoder, BCrypt con coste >=12. Nunca texto plano.
    CONSTRAINT CK_usuarios_password_hash CHECK (
        DATALENGTH(password_hash) = 68 AND LEN(password_hash) = 68 AND LEFT(password_hash, 8) = '{bcrypt}'
        AND SUBSTRING(password_hash, 9, 4) IN ('$2a$', '$2b$', '$2y$')
        AND SUBSTRING(password_hash, 13, 2) NOT LIKE '%[^0-9]%'
        AND TRY_CONVERT(INT, SUBSTRING(password_hash, 13, 2)) BETWEEN 12 AND 31
        AND SUBSTRING(password_hash, 15, 1) = '$'
        AND SUBSTRING(password_hash, 16, 53) NOT LIKE '%[^./A-Za-z0-9]%'),
    CONSTRAINT CK_usuarios_fechas_utc CHECK (
        DATEPART(TZOFFSET, fecha_creacion) = 0 AND DATEPART(TZOFFSET, fecha_actualizacion) = 0
        AND fecha_actualizacion >= fecha_creacion)
);

CREATE TABLE dbo.roles (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_roles PRIMARY KEY,
    codigo VARCHAR(50) COLLATE Latin1_General_100_BIN2 NOT NULL,
    nombre NVARCHAR(100) NOT NULL,
    CONSTRAINT UQ_roles_codigo UNIQUE (codigo),
    CONSTRAINT CK_roles_codigo CHECK (
        codigo LIKE '[A-Z]%' AND codigo NOT LIKE '%[^A-Z0-9_]%'
        AND DATALENGTH(codigo) BETWEEN 1 AND 50 AND DATALENGTH(codigo) = LEN(codigo)),
    CONSTRAINT CK_roles_nombre CHECK (LEN(LTRIM(RTRIM(nombre))) > 0)
);

CREATE TABLE dbo.usuario_rol (
    usuario_id BIGINT NOT NULL,
    rol_id BIGINT NOT NULL,
    CONSTRAINT PK_usuario_rol PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT FK_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES dbo.usuarios(id),
    CONSTRAINT FK_usuario_rol_rol FOREIGN KEY (rol_id) REFERENCES dbo.roles(id)
);
CREATE INDEX IX_usuario_rol_rol_usuario ON dbo.usuario_rol (rol_id, usuario_id);

CREATE TABLE dbo.sesiones_refresh (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_sesiones_refresh PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    familia_id UNIQUEIDENTIFIER NOT NULL,
    token_hash VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    fecha_creacion DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_sesiones_refresh_fecha_creacion DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')),
    fecha_expiracion DATETIMEOFFSET(7) NOT NULL,
    fecha_revocacion DATETIMEOFFSET(7) NULL,
    CONSTRAINT FK_sesiones_refresh_usuario FOREIGN KEY (usuario_id) REFERENCES dbo.usuarios(id),
    CONSTRAINT UQ_sesiones_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT CK_sesiones_refresh_token_hash CHECK (DATALENGTH(token_hash) = 64 AND LEN(token_hash) = 64 AND token_hash NOT LIKE '%[^0-9a-f]%'),
    CONSTRAINT CK_sesiones_refresh_periodo CHECK (fecha_expiracion > fecha_creacion),
    CONSTRAINT CK_sesiones_refresh_revocacion CHECK (fecha_revocacion IS NULL OR fecha_revocacion >= fecha_creacion),
    CONSTRAINT CK_sesiones_refresh_fechas_utc CHECK (
        DATEPART(TZOFFSET, fecha_creacion) = 0 AND DATEPART(TZOFFSET, fecha_expiracion) = 0
        AND (fecha_revocacion IS NULL OR DATEPART(TZOFFSET, fecha_revocacion) = 0))
);
CREATE INDEX IX_sesiones_refresh_usuario_familia ON dbo.sesiones_refresh (usuario_id, familia_id);
CREATE INDEX IX_sesiones_refresh_expiracion ON dbo.sesiones_refresh (fecha_expiracion);

-- Catalogo de referencia, sin asignaciones y sin administrador predeterminado.
INSERT INTO dbo.roles (codigo, nombre) VALUES
    ('ADMIN', N'Administrador'),
    ('ENCARGADO_ALMACEN', N'Encargado de almacén'),
    ('EMPLEADO', N'Empleado');
