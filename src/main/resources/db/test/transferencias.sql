IF DB_NAME() <> N'LM_Medical_Test'
    THROW 50001, N'Este script solo puede ejecutarse en LM_Medical_Test.', 1;

IF OBJECT_ID(N'dbo.transferencias', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.transferencias (
        id BIGINT IDENTITY(1, 1) NOT NULL,
        clave_idempotencia VARCHAR(36) NOT NULL,
        producto_id BIGINT NOT NULL,
        origen_id BIGINT NOT NULL,
        destino_id BIGINT NOT NULL,
        cantidad_total INT NOT NULL,
        observacion NVARCHAR(250) NULL,
        creada_en DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_transferencias PRIMARY KEY (id),
        CONSTRAINT UQ_transferencias_clave UNIQUE (clave_idempotencia),
        CONSTRAINT FK_transferencias_producto FOREIGN KEY (producto_id) REFERENCES dbo.productos(id),
        CONSTRAINT FK_transferencias_origen FOREIGN KEY (origen_id) REFERENCES dbo.almacenes(id),
        CONSTRAINT FK_transferencias_destino FOREIGN KEY (destino_id) REFERENCES dbo.almacenes(id),
        CONSTRAINT CK_transferencias_cantidad CHECK (cantidad_total > 0),
        CONSTRAINT CK_transferencias_almacenes CHECK (origen_id <> destino_id)
    )
END;

IF OBJECT_ID(N'dbo.detalles_transferencia', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.detalles_transferencia (
        id BIGINT IDENTITY(1, 1) NOT NULL,
        transferencia_id BIGINT NOT NULL,
        lote_id BIGINT NOT NULL,
        cantidad INT NOT NULL,
        CONSTRAINT PK_detalles_transferencia PRIMARY KEY (id),
        CONSTRAINT FK_detalles_transferencia_transferencia FOREIGN KEY (transferencia_id)
            REFERENCES dbo.transferencias(id),
        CONSTRAINT FK_detalles_transferencia_lote FOREIGN KEY (lote_id) REFERENCES dbo.lotes(id),
        CONSTRAINT CK_detalles_transferencia_cantidad CHECK (cantidad > 0)
    )
END;
