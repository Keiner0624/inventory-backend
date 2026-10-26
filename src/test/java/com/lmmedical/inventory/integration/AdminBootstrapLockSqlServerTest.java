package com.lmmedical.inventory.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import static com.lmmedical.inventory.integration.V2MigrationSqlServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BOOTSTRAP_SQLSERVER_TESTS", matches="true")
class AdminBootstrapLockSqlServerTest {
    private int lock(Connection connection) throws Exception {
        String sql = """
                DECLARE @resultado int;
                EXEC @resultado = sys.sp_getapplock
                    @Resource=N'LM_Medical_Primer_Admin', @LockMode='Exclusive',
                    @LockOwner='Transaction', @LockTimeout=0;
                SELECT @resultado;
                """;
        return Integer.parseInt(scalar(connection, sql));
    }

    @Test void bloqueoTransaccionalSerializaBootstrapsEnLaTemporalSinCambiarDatos() throws Exception {
        try (var first = connect("LM_Medical_Flyway_Prueba"); var second = connect("LM_Medical_Flyway_Prueba")) {
            assertTrue("LM_Medical_Flyway_Prueba".equalsIgnoreCase(scalar(first, "SELECT DB_NAME()")));
            assertTrue("LM_Medical_Flyway_Prueba".equalsIgnoreCase(scalar(second, "SELECT DB_NAME()")));
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            scalar(first, "SELECT COUNT_BIG(*) FROM dbo.usuarios");
            scalar(second, "SELECT COUNT_BIG(*) FROM dbo.usuarios");
            assertEquals("1", scalar(first, "SELECT @@TRANCOUNT"));
            assertEquals("1", scalar(second, "SELECT @@TRANCOUNT"));
            try {
                assertTrue(lock(first) >= 0);
                assertEquals(-1, lock(second));
                first.rollback();
                assertTrue(lock(second) >= 0);
                assertEquals("0", scalar(first, "SELECT COUNT(*) FROM dbo.usuarios"));
            } finally {
                first.rollback();
                second.rollback();
            }
        }
    }
}
