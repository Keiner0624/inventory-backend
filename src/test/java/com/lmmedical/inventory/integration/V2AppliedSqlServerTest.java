package com.lmmedical.inventory.integration;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("flyway-check")
class V2AppliedSqlServerTest {
    @Autowired private Flyway flyway;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void flywayAndHibernateValidateV2OnAuthorizedTemporaryDatabase() {
        assertTrue("LM_Medical_Flyway_Prueba".equalsIgnoreCase(jdbc.queryForObject("SELECT DB_NAME()", String.class)));
        assertSame(dataSource, flyway.getConfiguration().getDataSource());
        assertTrue(entityManagerFactory.isOpen());
        assertFalse(flyway.getConfiguration().isBaselineOnMigrate());
        assertTrue(flyway.getConfiguration().isCleanDisabled());
        assertTrue(flyway.info().current().getVersion().compareTo(MigrationVersion.fromVersion("2")) >= 0);
        assertEquals(0, flyway.info().pending().length);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='2' AND type='SQL' AND success=1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=0", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys.check_constraints WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN('CK_detalle_cantidad','CK_detalle_stock','CK_operacion_cantidad') AND is_disabled=0 AND is_not_trusted=0 AND is_not_for_replication=0", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys.key_constraints WHERE schema_id=SCHEMA_ID(N'dbo') AND name='UQ_transferencia_lote' AND type='UQ'", Integer.class));
        var keys = jdbc.queryForList("SELECT c.name FROM sys.key_constraints k JOIN sys.index_columns ic ON ic.object_id=k.parent_object_id AND ic.index_id=k.unique_index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE k.parent_object_id=OBJECT_ID(N'dbo.detalles_transferencia') AND k.name='UQ_transferencia_lote' AND ic.key_ordinal>0 ORDER BY ic.key_ordinal", String.class);
        assertEquals(java.util.List.of("transferencia_id", "lote_id"), keys);
        flyway.validate();
    }
}
