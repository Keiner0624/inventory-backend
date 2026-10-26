-- Inicializacion adicional de la base de pruebas; conserva las tablas existentes.
IF DB_NAME() <> N'LM_Medical_Test'
    THROW 50001, N'Este script solo puede ejecutarse en LM_Medical_Test.', 1;

IF OBJECT_ID(N'dbo.operaciones_salida', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.operaciones_salida (
        clave NVARCHAR(36) NOT NULL,
        producto_id BIGINT NOT NULL,
        almacen_id BIGINT NOT NULL,
        cantidad INT NOT NULL,
        observacion NVARCHAR(250) NULL,
        CONSTRAINT PK_operaciones_salida PRIMARY KEY (clave),
        CONSTRAINT FK_operacion_producto FOREIGN KEY (producto_id)
            REFERENCES dbo.productos (id),
        CONSTRAINT FK_operacion_almacen FOREIGN KEY (almacen_id)
            REFERENCES dbo.almacenes (id)
    )
END;

IF OBJECT_ID(N'dbo.detalles_salida', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.detalles_salida (
        id BIGINT IDENTITY(1, 1) NOT NULL,
        operacion_clave NVARCHAR(36) NOT NULL,
        lote_id BIGINT NOT NULL,
        codigo_lote NVARCHAR(50) NOT NULL,
        cantidad_retirada INT NOT NULL,
        stock_restante INT NOT NULL,
        CONSTRAINT PK_detalles_salida PRIMARY KEY (id),
        CONSTRAINT FK_detalle_operacion FOREIGN KEY (operacion_clave)
            REFERENCES dbo.operaciones_salida (clave),
        CONSTRAINT FK_detalle_lote FOREIGN KEY (lote_id)
            REFERENCES dbo.lotes (id)
    )
END;
