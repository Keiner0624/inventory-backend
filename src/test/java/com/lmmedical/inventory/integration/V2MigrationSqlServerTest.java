package com.lmmedical.inventory.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Cada caso construye V1 en un esquema aislado y revierte DDL y filas juntos. */
@EnabledIfEnvironmentVariable(named = "V2_SQLSERVER_TESTS", matches = "true")
class V2MigrationSqlServerTest {
    private Connection connection;
    private String schema;

    static String url(String database) {
        if (!Set.of("LM_Medical_Flyway_Prueba", "LM_Medical_Flyway_V2_Equivalentes").contains(database)) {
            throw new IllegalArgumentException("Base no autorizada para pruebas V2");
        }
        return "jdbc:sqlserver://localhost:1433;databaseName=" + database + ";encrypt=true;trustServerCertificate=true";
    }

    static String credential(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Falta " + name);
        return value;
    }

    static Connection connect(String database) throws SQLException {
        Connection connection = DriverManager.getConnection(url(database), credential("DB_USERNAME"), credential("DB_PASSWORD"));
        try {
            assertTrue(database.equalsIgnoreCase(scalar(connection, "SELECT DB_NAME()")));
            return connection;
        } catch (Throwable error) {
            connection.close();
            throw error;
        }
    }

    static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            boolean result = statement.execute(sql);
            // Los lotes T-SQL pueden emitir recuentos antes de un THROW.
            // Hay que consumirlos todos para no ocultar errores posteriores.
            while (true) {
                if (!result && statement.getUpdateCount() == -1) break;
                result = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
            }
        }
    }

    static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            String value = result.getString(1);
            if (value == null) return null;
            StringBuilder text = new StringBuilder(value);
            while (result.next()) text.append(result.getString(1));
            return text.toString();
        }
    }

    static String resource(String version) throws Exception {
        String file = version.equals("1") ? "V1__esquema_inicial.sql" : "V2__restricciones_integridad.sql";
        try (var input = V2MigrationSqlServerTest.class.getResourceAsStream("/db/migration/" + file)) {
            assertNotNull(input, file);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String isolated(String sql) {
        // Solo la copia en memoria cambia de esquema; V1 y V2 originales no cambian.
        return sql.replace("[dbo]", "[" + schema + "]").replace("dbo.", schema + ".").replace("N'dbo'", "N'" + schema + "'");
    }

    private void sql(String sql) throws SQLException { execute(connection, isolated(sql)); }

    @BeforeEach
    void createIsolatedV1() throws Exception {
        connection = connect("LM_Medical_Flyway_Prueba");
        assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='1' AND type='SQL' AND success=1"));
        assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=0"));
        connection.setAutoCommit(false);
        schema = "v2_" + UUID.randomUUID().toString().replace("-", "");
        execute(connection, "CREATE SCHEMA [" + schema + "]");
        sql(resource("1"));
    }

    @AfterEach
    void rollbackAll() throws Exception {
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    private String constraints() throws SQLException {
        return scalar(connection, "SELECT name,object_id,type FROM sys.objects WHERE schema_id=SCHEMA_ID(N'" + schema + "') AND type IN ('C','UQ','PK','F') ORDER BY name FOR JSON PATH");
    }

    private String originalTables() throws SQLException {
        return scalar(connection, "SELECT object_id,name,create_date,modify_date FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') ORDER BY name FOR JSON PATH");
    }

    private void assertCompleteRollback(String before) throws SQLException {
        assertEquals(before, originalTables(), "Las tablas originales deben permanecer intactas");
        assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM sys.schemas WHERE name=N'" + schema + "'"),
                "XACT_ABORT debe revertir incluso el esquema aislado creado por el caso de prueba");
    }

    private void fixture() throws SQLException {
        sql("""
            INSERT dbo.categorias(nombre) VALUES (N'V2');
            INSERT dbo.productos(codigo,nombre,precio_compra,precio_venta,categoria_id) VALUES(N'V2',N'V2',1,2,1);
            INSERT dbo.almacenes(codigo,nombre,ubicacion) VALUES('O',N'Origen',N'Lima'),('D',N'Destino',N'Lima');
            INSERT dbo.lotes(producto_id,codigo) VALUES(1,N'V2');
            INSERT dbo.operaciones_salida(clave,producto_id,almacen_id,cantidad) VALUES(N'v2',1,1,1);
            INSERT dbo.transferencias(clave_idempotencia,producto_id,origen_id,destino_id,cantidad_total) VALUES('v2',1,1,2,1);
            """);
    }

    @Test
    void preservesJdbcUpdateCounts() throws Exception {
        assertEquals("0", scalar(connection, "SELECT @@OPTIONS & 512"));
        sql(resource("2"));
        assertEquals("0", scalar(connection, "SELECT @@OPTIONS & 512"));
    }

    @Test
    void createsFourTrustedRulesWithoutChangingColumns() throws Exception {
        String columns = scalar(connection, "SELECT object_id,column_id,name,system_type_id,max_length,is_nullable FROM sys.columns WHERE object_id IN(SELECT object_id FROM sys.tables WHERE schema_id=SCHEMA_ID(N'" + schema + "')) ORDER BY object_id,column_id FOR JSON PATH");
        sql(resource("2"));
        assertEquals(columns, scalar(connection, "SELECT object_id,column_id,name,system_type_id,max_length,is_nullable FROM sys.columns WHERE object_id IN(SELECT object_id FROM sys.tables WHERE schema_id=SCHEMA_ID(N'" + schema + "')) ORDER BY object_id,column_id FOR JSON PATH"));
        assertEquals("3", scalar(connection, "SELECT COUNT(*) FROM sys.check_constraints WHERE schema_id=SCHEMA_ID(N'" + schema + "') AND name IN('CK_detalle_cantidad','CK_detalle_stock','CK_operacion_cantidad') AND is_disabled=0 AND is_not_trusted=0 AND is_not_for_replication=0"));
        assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM sys.key_constraints WHERE schema_id=SCHEMA_ID(N'" + schema + "') AND name='UQ_transferencia_lote' AND type='UQ'"));
    }

    @Test
    void recognizesExistingCanonicalConstraints() throws Exception {
        sql(canonicalConstraints());
        String before = constraints();
        sql(resource("2"));
        assertEquals(before, constraints());
    }

    static String canonicalConstraints() {
        return """
            ALTER TABLE dbo.detalles_transferencia ADD CONSTRAINT UQ_transferencia_lote UNIQUE(transferencia_id,lote_id);
            ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT CK_detalle_cantidad CHECK(cantidad_retirada>0);
            ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT CK_detalle_stock CHECK(stock_restante>=0);
            ALTER TABLE dbo.operaciones_salida WITH CHECK ADD CONSTRAINT CK_operacion_cantidad CHECK(cantidad>0);
            """;
    }

    @Test
    void recognizesOtherNamesReversedKeysAndEquivalentIntegerChecks() throws Exception {
        sql("""
            CREATE UNIQUE INDEX otro_unique ON dbo.detalles_transferencia(lote_id DESC,transferencia_id) INCLUDE(cantidad);
            ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT otra_cantidad CHECK (1 <= [cantidad_retirada]);
            ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT otro_stock CHECK (stock_restante > (-1));
            ALTER TABLE dbo.operaciones_salida WITH CHECK ADD CONSTRAINT otra_operacion CHECK (0 < cantidad);
            """);
        String before = constraints();
        sql(resource("2"));
        assertEquals(before, constraints());
        assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID(N'" + schema + "') AND name IN('UQ_transferencia_lote','CK_detalle_cantidad','CK_detalle_stock','CK_operacion_cantidad')"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE UNIQUE INDEX UQ_transferencia_lote ON dbo.detalles_transferencia(lote_id)",
        "CREATE UNIQUE INDEX UQ_transferencia_lote ON dbo.detalles_transferencia(transferencia_id,lote_id) WHERE cantidad>1",
        "CREATE UNIQUE INDEX UQ_transferencia_lote ON dbo.detalles_transferencia(transferencia_id,lote_id) WITH (IGNORE_DUP_KEY=ON)",
        "ALTER TABLE dbo.operaciones_salida WITH CHECK ADD CONSTRAINT CK_operacion_cantidad CHECK(cantidad>=0)",
        "ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT CK_detalle_cantidad CHECK(stock_restante>=0)",
        "ALTER TABLE dbo.detalles_salida WITH CHECK ADD CONSTRAINT otra_regla CHECK(cantidad_retirada>=0)"
    })
    void rejectsConflictsBeforeAnyAlter(String setup) throws Exception {
        sql(setup);
        String before = originalTables();
        SQLException error = assertThrows(SQLException.class, () -> sql(resource("2")));
        assertTrue(error.getMessage().contains("V2:"), error.getMessage());
        assertTrue(Set.of(51006, 51009, 51011).contains(error.getErrorCode()), error.getMessage());
        assertCompleteRollback(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicados", "cantidad", "stock", "operacion"})
    void rejectsInvalidExistingDataWithoutPartialDdl(String kind) throws Exception {
        fixture();
        switch (kind) {
            case "duplicados" -> sql("INSERT dbo.detalles_transferencia(transferencia_id,lote_id,cantidad) VALUES(1,1,1),(1,1,2)");
            case "cantidad" -> sql("INSERT dbo.detalles_salida(operacion_clave,lote_id,codigo_lote,cantidad_retirada,stock_restante) VALUES(N'v2',1,N'V2',0,0)");
            case "stock" -> sql("INSERT dbo.detalles_salida(operacion_clave,lote_id,codigo_lote,cantidad_retirada,stock_restante) VALUES(N'v2',1,N'V2',1,-1)");
            case "operacion" -> sql("INSERT dbo.operaciones_salida(clave,producto_id,almacen_id,cantidad) VALUES(N'cero',1,1,0)");
            default -> fail(kind);
        }
        String before = originalTables();
        SQLException error = assertThrows(SQLException.class, () -> sql(resource("2")));
        assertEquals(Map.of("duplicados", 51002, "cantidad", 51003, "stock", 51004, "operacion", 51005).get(kind).intValue(), error.getErrorCode());
        assertCompleteRollback(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cantidad", "stock", "operacion", "duplicado"})
    void sqlServerRejectsInvalidNewRows(String kind) throws Exception {
        sql(resource("2"));
        fixture();
        String insert = switch (kind) {
            case "cantidad" -> "INSERT dbo.detalles_salida(operacion_clave,lote_id,codigo_lote,cantidad_retirada,stock_restante) VALUES(N'v2',1,N'V2',-1,0)";
            case "stock" -> "INSERT dbo.detalles_salida(operacion_clave,lote_id,codigo_lote,cantidad_retirada,stock_restante) VALUES(N'v2',1,N'V2',1,-1)";
            case "operacion" -> "INSERT dbo.operaciones_salida(clave,producto_id,almacen_id,cantidad) VALUES(N'cero',1,1,0)";
            case "duplicado" -> "INSERT dbo.detalles_transferencia(transferencia_id,lote_id,cantidad) VALUES(1,1,1),(1,1,2)";
            default -> throw new IllegalArgumentException(kind);
        };
        SQLException error = assertThrows(SQLException.class, () -> sql(insert));
        assertEquals(kind.equals("duplicado") ? 2627 : 547, error.getErrorCode());
        assertTrue(error.getMessage().contains(Map.of("cantidad", "CK_detalle_cantidad", "stock", "CK_detalle_stock", "operacion", "CK_operacion_cantidad", "duplicado", "UQ_transferencia_lote").get(kind)));
    }

    @Test
    void acceptsValidQuantitiesAndZeroRemainingStock() throws Exception {
        sql(resource("2"));
        fixture();
        sql("INSERT dbo.detalles_salida(operacion_clave,lote_id,codigo_lote,cantidad_retirada,stock_restante) VALUES(N'v2',1,N'V2',1,0)");
        sql("INSERT dbo.detalles_transferencia(transferencia_id,lote_id,cantidad) VALUES(1,1,1)");
        assertEquals("1", scalar(connection, isolated("SELECT COUNT(*) FROM dbo.detalles_salida")));
        assertEquals("1", scalar(connection, isolated("SELECT COUNT(*) FROM dbo.detalles_transferencia")));
    }
}
