-- V2: adopta las reglas existentes o las agrega sin modificar datos.
-- Flyway/SQL Server ejecutan este archivo en una unica transaccion.
SET XACT_ABORT ON;

IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION'), 0) <> 1
    THROW 51000, 'V2: se requiere VIEW DEFINITION para verificar las restricciones existentes.', 1;

DECLARE @transferencia int = OBJECT_ID(N'dbo.detalles_transferencia', N'U');
DECLARE @salida int = OBJECT_ID(N'dbo.detalles_salida', N'U');
DECLARE @operacion int = OBJECT_ID(N'dbo.operaciones_salida', N'U');
IF @transferencia IS NULL OR @salida IS NULL OR @operacion IS NULL
    THROW 51001, 'V2: faltan tablas requeridas del esquema V1.', 1;

-- Toda validacion se realiza antes del primer ALTER TABLE.
IF EXISTS (SELECT 1 FROM dbo.detalles_transferencia GROUP BY transferencia_id, lote_id HAVING COUNT_BIG(*) > 1)
    THROW 51002, 'V2: detalles_transferencia contiene duplicados de transferencia_id + lote_id. Corrige los datos mediante un procedimiento autorizado.', 1;
IF EXISTS (SELECT 1 FROM dbo.detalles_salida WHERE cantidad_retirada <= 0)
    THROW 51003, 'V2: detalles_salida contiene cantidad_retirada <= 0. No se modificaron datos.', 1;
IF EXISTS (SELECT 1 FROM dbo.detalles_salida WHERE stock_restante < 0)
    THROW 51004, 'V2: detalles_salida contiene stock_restante < 0. No se modificaron datos.', 1;
IF EXISTS (SELECT 1 FROM dbo.operaciones_salida WHERE cantidad <= 0)
    THROW 51005, 'V2: operaciones_salida contiene cantidad <= 0. No se modificaron datos.', 1;

-- La equivalencia UNIQUE depende de las columnas CLAVE reales, no del nombre
-- ni del orden ASC/DESC. Un filtro o IGNORE_DUP_KEY cambia el comportamiento.
DECLARE @uniqueCandidates TABLE (index_id int PRIMARY KEY, name sysname, usable bit);
INSERT INTO @uniqueCandidates
SELECT i.index_id, i.name,
       CONVERT(bit, CASE WHEN i.is_disabled = 0 AND i.is_hypothetical = 0
                             AND i.ignore_dup_key = 0 AND i.type IN (1, 2) THEN 1 ELSE 0 END)
FROM sys.indexes i
WHERE i.object_id = @transferencia AND i.is_unique = 1 AND i.has_filter = 0
  AND (SELECT COUNT(*) FROM sys.index_columns ic
       WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0) = 2
  AND (SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.columns c
         ON c.object_id = ic.object_id AND c.column_id = ic.column_id
       WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0
         AND c.name IN (N'transferencia_id', N'lote_id')) = 2;

IF EXISTS (SELECT 1 FROM sys.objects o WHERE o.schema_id = SCHEMA_ID(N'dbo')
           AND o.name = N'UQ_transferencia_lote'
           AND NOT (o.type = 'UQ' AND o.parent_object_id = @transferencia
                    AND EXISTS (SELECT 1 FROM sys.key_constraints k JOIN @uniqueCandidates u
                                  ON u.index_id = k.unique_index_id AND u.usable = 1
                                WHERE k.object_id = o.object_id)))
   OR EXISTS (SELECT 1 FROM sys.indexes i WHERE i.object_id = @transferencia
              AND i.name = N'UQ_transferencia_lote'
              AND NOT EXISTS (SELECT 1 FROM @uniqueCandidates u WHERE u.index_id = i.index_id AND u.usable = 1))
    THROW 51006, 'V2: UQ_transferencia_lote existe con otra tabla, columnas, filtro o comportamiento. No se sustituye ni elimina.', 1;
IF EXISTS (SELECT 1 FROM @uniqueCandidates WHERE usable = 0)
    THROW 51007, 'V2: existe un UNIQUE sobre transferencia_id + lote_id deshabilitado o con IGNORE_DUP_KEY. Requiere revision explicita.', 1;

DECLARE @rules TABLE (
    name sysname PRIMARY KEY, table_id int, column_name sysname,
    expression nvarchar(100), reverse_expression nvarchar(100),
    integer_expression nvarchar(100), reverse_integer_expression nvarchar(100),
    existing bit NOT NULL DEFAULT 0
);
INSERT INTO @rules (name, table_id, column_name, expression, reverse_expression, integer_expression, reverse_integer_expression)
VALUES
 (N'CK_detalle_cantidad', @salida, N'cantidad_retirada', N'cantidad_retirada>0', N'0<cantidad_retirada', N'cantidad_retirada>=1', N'1<=cantidad_retirada'),
 (N'CK_detalle_stock', @salida, N'stock_restante', N'stock_restante>=0', N'0<=stock_restante', N'stock_restante>-1', N'-1<stock_restante'),
 (N'CK_operacion_cantidad', @operacion, N'cantidad', N'cantidad>0', N'0<cantidad', N'cantidad>=1', N'1<=cantidad');

-- Las formas >=1 y >-1 solo son equivalentes para las columnas INT de V1.
IF EXISTS (SELECT 1 FROM @rules r WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns c WHERE c.object_id = r.table_id AND c.name = r.column_name
      AND c.system_type_id = TYPE_ID(N'int') AND c.is_nullable = 0 AND c.is_computed = 0))
    THROW 51008, 'V2: las cantidades deben conservar el tipo INT NOT NULL definido en V1.', 1;

DECLARE @checks TABLE (object_id int, name sysname, table_id int, column_id int, normalized nvarchar(max), usable bit);
INSERT INTO @checks
SELECT c.object_id, c.name, c.parent_object_id,
       (SELECT CASE WHEN COUNT(DISTINCT d.referenced_minor_id) = 1 THEN MIN(d.referenced_minor_id) END
        FROM sys.sql_expression_dependencies d
        WHERE d.referencing_id = c.object_id AND d.referenced_id = c.parent_object_id AND d.referenced_minor_id > 0),
       REPLACE(TRANSLATE(LOWER(c.definition), N'[]()' + NCHAR(9) + NCHAR(10) + NCHAR(13), N'       '), N' ', N''),
       CONVERT(bit, CASE WHEN c.is_disabled = 0 AND c.is_not_trusted = 0 AND c.is_not_for_replication = 0 THEN 1 ELSE 0 END)
FROM sys.check_constraints c
WHERE c.parent_object_id IN (@salida, @operacion);

DECLARE @conflict sysname;
SELECT TOP (1) @conflict = r.name FROM @rules r JOIN sys.objects o
  ON o.schema_id = SCHEMA_ID(N'dbo') AND o.name = r.name
WHERE NOT EXISTS (SELECT 1 FROM @checks c WHERE c.object_id = o.object_id AND c.table_id = r.table_id
    AND c.column_id = COLUMNPROPERTY(r.table_id, r.column_name, 'ColumnId')
    AND c.usable = 1 AND c.normalized COLLATE Latin1_General_100_BIN2
        IN (r.expression, r.reverse_expression, r.integer_expression, r.reverse_integer_expression));
IF @conflict IS NOT NULL
BEGIN
    DECLARE @message nvarchar(2048) = N'V2: conflicto en ' + @conflict + N': tabla, expresion o estado distinto; se requiere CHECK habilitado, confiable y sin NOT FOR REPLICATION.';
    THROW 51009, @message, 1;
END;

IF EXISTS (SELECT 1 FROM @rules r JOIN @checks c ON c.table_id = r.table_id
           AND c.column_id = COLUMNPROPERTY(r.table_id, r.column_name, 'ColumnId')
           AND c.normalized COLLATE Latin1_General_100_BIN2
               IN (r.expression, r.reverse_expression, r.integer_expression, r.reverse_integer_expression)
           WHERE c.usable = 0)
    THROW 51010, 'V2: CHECK equivalente deshabilitado, no confiable o NOT FOR REPLICATION. No se acepta como regla activa.', 1;

UPDATE r SET existing = 1 FROM @rules r WHERE EXISTS (
    SELECT 1 FROM @checks c WHERE c.table_id = r.table_id AND c.usable = 1
      AND c.column_id = COLUMNPROPERTY(r.table_id, r.column_name, 'ColumnId')
      AND c.normalized COLLATE Latin1_General_100_BIN2
          IN (r.expression, r.reverse_expression, r.integer_expression, r.reverse_integer_expression));

-- No se infiere equivalencia de SQL arbitrario. Si una regla desconocida ya
-- depende de la columna y no hay una equivalente acreditada, se pide revision
-- en lugar de duplicar una regla o aceptar una expresion mas debil.
IF EXISTS (SELECT 1 FROM @rules r JOIN @checks c ON c.table_id = r.table_id
    WHERE r.existing = 0 AND EXISTS (
        SELECT 1 FROM sys.sql_expression_dependencies d JOIN sys.columns col
          ON col.object_id = d.referenced_id AND col.column_id = d.referenced_minor_id
        WHERE d.referencing_id = c.object_id AND col.object_id = r.table_id AND col.name = r.column_name))
    THROW 51011, 'V2: CHECK previo sobre una cantidad con expresion no reconocida; revisa su equivalencia antes de agregar otra regla.', 1;

IF NOT EXISTS (SELECT 1 FROM @uniqueCandidates WHERE usable = 1)
    ALTER TABLE dbo.detalles_transferencia ADD CONSTRAINT UQ_transferencia_lote UNIQUE (transferencia_id, lote_id);
IF EXISTS (SELECT 1 FROM @rules WHERE name = N'CK_detalle_cantidad' AND existing = 0)
    ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT CK_detalle_cantidad CHECK (cantidad_retirada > 0);
IF EXISTS (SELECT 1 FROM @rules WHERE name = N'CK_detalle_stock' AND existing = 0)
    ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT CK_detalle_stock CHECK (stock_restante >= 0);
IF EXISTS (SELECT 1 FROM @rules WHERE name = N'CK_operacion_cantidad' AND existing = 0)
    ALTER TABLE dbo.operaciones_salida WITH CHECK ADD CONSTRAINT CK_operacion_cantidad CHECK (cantidad > 0);
