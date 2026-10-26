package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.service.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("flyway-check")
class FlywayCheckSqlServerTest {
    @Autowired private DataSource dataSource;
    @Autowired private Flyway flyway;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categorias;
    @Autowired private ProductRepository productos;
    @Autowired private AlmacenRepository almacenes;
    @Autowired private LoteRepository lotes;
    @Autowired private ExistenciaRepository existencias;
    @Autowired private InventarioService entradas;
    @Autowired private SalidaIdempotenteService salidas;
    @Autowired private TransferenciaService transferencias;

    @Test
    void flywayYHibernateUsanLaMismaBaseYV1EstaAplicada() {
        assertTrue("LM_Medical_Flyway_Prueba".equalsIgnoreCase(jdbc.queryForObject("SELECT DB_NAME()", String.class)));
        assertSame(dataSource, flyway.getConfiguration().getDataSource());
        assertTrue(entityManagerFactory.isOpen());
        assertFalse(flyway.getConfiguration().isBaselineOnMigrate());
        assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN ('almacenes','categorias','detalles_salida','detalles_transferencia','existencias','lotes','movimientos_inventario','operaciones_salida','productos','proveedores','transferencias')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='1' AND type='SQL' AND success=1", Integer.class));
        flyway.validate();
    }

    @Test
    void unaSegundaMigracionNoRecreaTablas() {
        var before = jdbc.queryForList("SELECT object_id,name,create_date FROM sys.tables ORDER BY name");
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(before, jdbc.queryForList("SELECT object_id,name,create_date FROM sys.tables ORDER BY name"));
    }

    @Test
    @Transactional
    void entradasSalidasYTransferenciasFuncionanSobreV1() {
        assertTrue("LM_Medical_Flyway_Prueba".equalsIgnoreCase(jdbc.queryForObject("SELECT DB_NAME()", String.class)));
        String code = UUID.randomUUID().toString().substring(0, 8);
        Category categoria = categorias.save(new Category("Flyway-" + code));
        Product producto = productos.save(new Product("FW-" + code, "Producto Flyway " + code,
                "Regresion V1", BigDecimal.TEN, new BigDecimal("20.00"), categoria));
        Almacen origen = almacenes.save(new Almacen("FO-" + code, "Origen", "Lima"));
        Almacen destino = almacenes.save(new Almacen("FD-" + code, "Destino", "Lima"));
        Lote lote = lotes.save(new Lote(producto, "FL-" + code, LocalDate.now(ZoneId.of("America/Lima")).plusYears(1)));
        entradas.registrarEntrada(new EntradaInventarioRequest(lote.getId(), origen.getId(), 10, "Entrada V1"));
        String salidaKey = UUID.randomUUID().toString();
        var salida = new SalidaInventarioRequest(producto.getId(), origen.getId(), 2, "Salida V1");
        assertEquals(salidas.registrar(salidaKey, salida), salidas.registrar(salidaKey, salida));
        String transferenciaKey = UUID.randomUUID().toString();
        var request = new TransferenciaRequest(producto.getId(), origen.getId(), destino.getId(), 3, "Transferencia V1");
        assertEquals(transferencias.registrar(transferenciaKey, request), transferencias.registrar(transferenciaKey, request));
        assertEquals(5, existencias.findByLote_IdAndAlmacen_Id(lote.getId(), origen.getId()).orElseThrow().getCantidad());
        assertEquals(3, existencias.findByLote_IdAndAlmacen_Id(lote.getId(), destino.getId()).orElseThrow().getCantidad());
    }
}
