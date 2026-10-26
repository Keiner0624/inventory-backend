package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.enums.TipoMovimiento;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.service.InventarioService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class InventarioSqlServerTest {

    @Autowired
    private CategoryRepository categoriaRepository;

    @Autowired
    private ProductRepository productoRepository;

    @Autowired
    private AlmacenRepository almacenRepository;

    @Autowired
    private LoteRepository loteRepository;

    @Autowired
    private ExistenciaRepository existenciaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void verificarBaseDeDatos() {

        String database = jdbcTemplate.queryForObject(
                "SELECT DB_NAME()",
                String.class
        );

        assertEquals(
                "LM_Medical_Test",
                database,
                "Las pruebas deben utilizar la base de datos de pruebas"
        );
    }

    @Test
    @Transactional
    @Rollback
    void debeRegistrarEntradaEnSqlServer() {

        String codigo = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        Category categoria = categoriaRepository.save(
                new Category("Categoria " + codigo)
        );

        Product producto = productoRepository.save(
                new Product(
                        "PROD-" + codigo,
                        "Guantes de prueba",
                        "Producto de integración",
                        new BigDecimal("15.00"),
                        new BigDecimal("25.00"),
                        categoria
                )
        );

        Almacen almacen = almacenRepository.save(
                new Almacen(
                        "ALM-" + codigo,
                        "Almacén de pruebas",
                        "Lima"
                )
        );

        Lote lote = loteRepository.save(
                new Lote(
                        producto,
                        "LOT-" + codigo,
                        LocalDate.now().plusYears(2)
                )
        );

        EntradaInventarioRequest request =
                new EntradaInventarioRequest(
                        lote.getId(),
                        almacen.getId(),
                        500,
                        "Prueba de integración"
                );

        EntradaInventarioResponse respuesta =
                inventarioService.registrarEntrada(request);

        entityManager.flush();
        entityManager.clear();

        Existencia existencia = existenciaRepository
                .findByLote_IdAndAlmacen_Id(
                        lote.getId(),
                        almacen.getId()
                )
                .orElseThrow();

        MovimientoInventario movimiento =
                movimientoRepository
                        .findById(respuesta.movimientoId())
                        .orElseThrow();

        assertEquals(500, existencia.getCantidad());

        assertEquals(500, respuesta.stockActual());

        assertEquals(500, movimiento.getCantidad());

        assertEquals(
                TipoMovimiento.ENTRADA,
                movimiento.getTipo()
        );
    }
}