-- V1: esquema inicial verificado contra LM_Medical_Test.
-- Solo DDL; sin datos ni seleccion de una base de datos.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE TABLE [dbo].[almacenes] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [codigo] VARCHAR(20) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [nombre] NVARCHAR(120) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [ubicacion] NVARCHAR(200) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [activo] BIT NOT NULL CONSTRAINT [DF_almacenes_activo] DEFAULT ((1))
);

CREATE TABLE [dbo].[categorias] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [nombre] NVARCHAR(100) COLLATE Modern_Spanish_CI_AS NOT NULL
);

CREATE TABLE [dbo].[detalles_salida] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [operacion_clave] NVARCHAR(36) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [lote_id] BIGINT NOT NULL,
    [codigo_lote] NVARCHAR(50) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [cantidad_retirada] INT NOT NULL,
    [stock_restante] INT NOT NULL
);

CREATE TABLE [dbo].[detalles_transferencia] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [transferencia_id] BIGINT NOT NULL,
    [lote_id] BIGINT NOT NULL,
    [cantidad] INT NOT NULL
);

CREATE TABLE [dbo].[existencias] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [lote_id] BIGINT NOT NULL,
    [almacen_id] BIGINT NOT NULL,
    [cantidad] INT NOT NULL CONSTRAINT [DF_existencias_cantidad] DEFAULT ((0))
);

CREATE TABLE [dbo].[lotes] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [producto_id] BIGINT NOT NULL,
    [codigo] NVARCHAR(50) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [fecha_vencimiento] DATE NULL
);

CREATE TABLE [dbo].[movimientos_inventario] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [lote_id] BIGINT NOT NULL,
    [almacen_id] BIGINT NOT NULL,
    [tipo] VARCHAR(15) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [cantidad] INT NOT NULL,
    [observacion] NVARCHAR(250) COLLATE Modern_Spanish_CI_AS NULL,
    [fecha] DATETIME2(7) NOT NULL
);

CREATE TABLE [dbo].[operaciones_salida] (
    [clave] NVARCHAR(36) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [producto_id] BIGINT NOT NULL,
    [almacen_id] BIGINT NOT NULL,
    [cantidad] INT NOT NULL,
    [observacion] NVARCHAR(250) COLLATE Modern_Spanish_CI_AS NULL
);

CREATE TABLE [dbo].[productos] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [codigo] NVARCHAR(30) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [nombre] NVARCHAR(150) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [descripcion] NVARCHAR(500) COLLATE Modern_Spanish_CI_AS NULL,
    [precio_compra] DECIMAL(12,2) NOT NULL,
    [precio_venta] DECIMAL(12,2) NOT NULL,
    [categoria_id] BIGINT NOT NULL,
    [activo] BIT NOT NULL CONSTRAINT [DF__productos__activ__5DCAEF64] DEFAULT ((1))
);

CREATE TABLE [dbo].[proveedores] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ruc] VARCHAR(11) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [razon_social] NVARCHAR(150) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [correo] NVARCHAR(150) COLLATE Modern_Spanish_CI_AS NULL,
    [telefono] VARCHAR(20) COLLATE Modern_Spanish_CI_AS NULL,
    [activo] BIT NOT NULL CONSTRAINT [DF_proveedores_activo] DEFAULT ((1))
);

CREATE TABLE [dbo].[transferencias] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [clave_idempotencia] VARCHAR(36) COLLATE Modern_Spanish_CI_AS NOT NULL,
    [producto_id] BIGINT NOT NULL,
    [origen_id] BIGINT NOT NULL,
    [destino_id] BIGINT NOT NULL,
    [cantidad_total] INT NOT NULL,
    [observacion] NVARCHAR(250) COLLATE Modern_Spanish_CI_AS NULL,
    [creada_en] DATETIME2(7) NOT NULL CONSTRAINT [DF__transfere__cread__74AE54BC] DEFAULT (sysutcdatetime())
);

ALTER TABLE [dbo].[almacenes] ADD CONSTRAINT [PK__almacene__3213E83F237A8954] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[almacenes] ADD CONSTRAINT [UQ_almacenes_codigo] UNIQUE NONCLUSTERED ([codigo] ASC);
ALTER TABLE [dbo].[categorias] ADD CONSTRAINT [PK__categori__3213E83F07667492] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[categorias] ADD CONSTRAINT [UQ__categori__72AFBCC617CC955F] UNIQUE NONCLUSTERED ([nombre] ASC);
ALTER TABLE [dbo].[detalles_salida] ADD CONSTRAINT [PK_detalles_salida] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[detalles_transferencia] ADD CONSTRAINT [PK_detalles_transferencia] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[existencias] ADD CONSTRAINT [PK__existenc__3213E83F19C0D81F] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[existencias] ADD CONSTRAINT [UQ_existencias_lote_almacen] UNIQUE NONCLUSTERED ([lote_id] ASC, [almacen_id] ASC);
ALTER TABLE [dbo].[lotes] ADD CONSTRAINT [PK__lotes__3213E83FB58B383D] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[lotes] ADD CONSTRAINT [UQ_lotes_producto_codigo] UNIQUE NONCLUSTERED ([producto_id] ASC, [codigo] ASC);
ALTER TABLE [dbo].[movimientos_inventario] ADD CONSTRAINT [PK__movimien__3213E83F133E6B1B] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[operaciones_salida] ADD CONSTRAINT [PK_operaciones_salida] PRIMARY KEY CLUSTERED ([clave] ASC);
ALTER TABLE [dbo].[productos] ADD CONSTRAINT [PK__producto__3213E83F88CC860F] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[productos] ADD CONSTRAINT [UQ__producto__40F9A206D4736533] UNIQUE NONCLUSTERED ([codigo] ASC);
ALTER TABLE [dbo].[proveedores] ADD CONSTRAINT [PK__proveedo__3213E83F01FE42B9] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[proveedores] ADD CONSTRAINT [UQ_proveedores_ruc] UNIQUE NONCLUSTERED ([ruc] ASC);
ALTER TABLE [dbo].[transferencias] ADD CONSTRAINT [PK_transferencias] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[transferencias] ADD CONSTRAINT [UQ_transferencias_clave] UNIQUE NONCLUSTERED ([clave_idempotencia] ASC);
ALTER TABLE [dbo].[detalles_salida] WITH CHECK ADD CONSTRAINT [FK_detalle_lote] FOREIGN KEY ([lote_id]) REFERENCES [dbo].[lotes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[detalles_salida] WITH CHECK ADD CONSTRAINT [FK_detalle_operacion] FOREIGN KEY ([operacion_clave]) REFERENCES [dbo].[operaciones_salida] ([clave]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[detalles_transferencia] WITH CHECK ADD CONSTRAINT [FK_detalles_transferencia_lote] FOREIGN KEY ([lote_id]) REFERENCES [dbo].[lotes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[detalles_transferencia] WITH CHECK ADD CONSTRAINT [FK_detalles_transferencia_transferencia] FOREIGN KEY ([transferencia_id]) REFERENCES [dbo].[transferencias] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[existencias] WITH CHECK ADD CONSTRAINT [FK_existencias_almacenes] FOREIGN KEY ([almacen_id]) REFERENCES [dbo].[almacenes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[existencias] WITH CHECK ADD CONSTRAINT [FK_existencias_lotes] FOREIGN KEY ([lote_id]) REFERENCES [dbo].[lotes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lotes] WITH CHECK ADD CONSTRAINT [FK_lotes_productos] FOREIGN KEY ([producto_id]) REFERENCES [dbo].[productos] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[movimientos_inventario] WITH CHECK ADD CONSTRAINT [FK_movimientos_almacenes] FOREIGN KEY ([almacen_id]) REFERENCES [dbo].[almacenes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[movimientos_inventario] WITH CHECK ADD CONSTRAINT [FK_movimientos_lotes] FOREIGN KEY ([lote_id]) REFERENCES [dbo].[lotes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[operaciones_salida] WITH CHECK ADD CONSTRAINT [FK_operacion_almacen] FOREIGN KEY ([almacen_id]) REFERENCES [dbo].[almacenes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[operaciones_salida] WITH CHECK ADD CONSTRAINT [FK_operacion_producto] FOREIGN KEY ([producto_id]) REFERENCES [dbo].[productos] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[productos] WITH CHECK ADD CONSTRAINT [FK_producto_Categoria] FOREIGN KEY ([categoria_id]) REFERENCES [dbo].[categorias] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[transferencias] WITH CHECK ADD CONSTRAINT [FK_transferencias_destino] FOREIGN KEY ([destino_id]) REFERENCES [dbo].[almacenes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[transferencias] WITH CHECK ADD CONSTRAINT [FK_transferencias_origen] FOREIGN KEY ([origen_id]) REFERENCES [dbo].[almacenes] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[transferencias] WITH CHECK ADD CONSTRAINT [FK_transferencias_producto] FOREIGN KEY ([producto_id]) REFERENCES [dbo].[productos] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[detalles_transferencia] WITH CHECK ADD CONSTRAINT [CK_detalles_transferencia_cantidad] CHECK ([cantidad]>(0));
ALTER TABLE [dbo].[existencias] WITH CHECK ADD CONSTRAINT [CK_existencias_cantidad] CHECK ([cantidad]>=(0));
ALTER TABLE [dbo].[movimientos_inventario] WITH CHECK ADD CONSTRAINT [CK_movimientos_cantidad] CHECK ([cantidad]>(0));
ALTER TABLE [dbo].[movimientos_inventario] WITH CHECK ADD CONSTRAINT [CK_movimientos_tipo] CHECK ([tipo]='SALIDA' OR [tipo]='ENTRADA');
ALTER TABLE [dbo].[productos] WITH CHECK ADD CONSTRAINT [CK_precios] CHECK ([precio_compra]>=(0) AND [precio_venta]>(0));
ALTER TABLE [dbo].[proveedores] WITH CHECK ADD CONSTRAINT [CK_proveedores_ruc] CHECK (len([ruc])=(11) AND NOT [ruc] like '%[^0-9]%');
ALTER TABLE [dbo].[transferencias] WITH CHECK ADD CONSTRAINT [CK_transferencias_almacenes] CHECK ([origen_id]<>[destino_id]);
ALTER TABLE [dbo].[transferencias] WITH CHECK ADD CONSTRAINT [CK_transferencias_cantidad] CHECK ([cantidad_total]>(0));
