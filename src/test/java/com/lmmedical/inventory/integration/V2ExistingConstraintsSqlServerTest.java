package com.lmmedical.inventory.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static com.lmmedical.inventory.integration.V2MigrationSqlServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Base adicional vacia y autorizada; nunca usa dev ni Test. No crea ni borra bases. */
@EnabledIfEnvironmentVariable(named = "V2_SQLSERVER_TESTS", matches = "true")
class V2ExistingConstraintsSqlServerTest {
    @Test
    void flywayRegistersV2WithoutReplacingExistingRules() throws Exception {
        String database = "LM_Medical_Flyway_V2_Equivalentes";
        try (var connection = connect(database)) {
            if (scalar(connection, "SELECT OBJECT_ID(N'dbo.flyway_schema_history',N'U')") == null) {
                assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0"));
            } else {
                assertEmpty(connection);
            }
            var v1 = Flyway.configure().dataSource(url(database), credential("DB_USERNAME"), credential("DB_PASSWORD"))
                    .defaultSchema("dbo").schemas("dbo").cleanDisabled(true).baselineOnMigrate(false).target("1").load();
            // Si ya se verifico en una ejecucion previa, no se intenta volver a V1.
            boolean applied = scalar(connection, "SELECT OBJECT_ID(N'dbo.flyway_schema_history',N'U')") != null
                    && scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='2' AND type='SQL' AND success=1").equals("1");
            if (!applied) {
                v1.migrate();
                assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=0"));
                assertEmpty(connection);
                connection.setAutoCommit(false);
                try {
                    execute(connection, canonicalConstraints());
                    connection.commit();
                } catch (Throwable error) {
                    connection.rollback();
                    throw error;
                } finally { connection.setAutoCommit(true); }
            }
            String query = "SELECT object_id,name,definition FROM (SELECT object_id,name,definition FROM sys.check_constraints WHERE name IN('CK_detalle_cantidad','CK_detalle_stock','CK_operacion_cantidad') UNION ALL SELECT object_id,name,CONVERT(nvarchar(max),unique_index_id) FROM sys.key_constraints WHERE name='UQ_transferencia_lote') q ORDER BY name FOR JSON PATH";
            String before = scalar(connection, query);
            assertEquals("3", scalar(connection, "SELECT COUNT(*) FROM sys.check_constraints WHERE name IN('CK_detalle_cantidad','CK_detalle_stock','CK_operacion_cantidad') AND is_disabled=0 AND is_not_trusted=0"));
            var flyway = Flyway.configure().dataSource(url(database), credential("DB_USERNAME"), credential("DB_PASSWORD"))
                    .defaultSchema("dbo").schemas("dbo").cleanDisabled(true).baselineOnMigrate(false).target("2").load();
            assertEquals(applied ? 0 : 1, flyway.migrate().migrationsExecuted);
            assertEquals(before, scalar(connection, query));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='2' AND type='SQL' AND success=1"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=0"));
            assertEquals(0, flyway.info().pending().length);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEmpty(connection);
        }
    }

    private static void assertEmpty(java.sql.Connection connection) throws java.sql.SQLException {
        var names = new java.util.ArrayList<String>();
        try (var statement = connection.createStatement(); var tables = statement.executeQuery("SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name<>'flyway_schema_history'")) {
            while (tables.next()) names.add(tables.getString(1));
        }
        assertEquals(11, names.size());
        for (String name : names) assertEquals("0", scalar(connection, "SELECT COUNT_BIG(*) FROM dbo.[" + name.replace("]", "]]") + "]"), "Base adicional con datos: detener las pruebas");
    }
}
