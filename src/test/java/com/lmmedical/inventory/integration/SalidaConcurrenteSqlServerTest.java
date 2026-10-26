package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.service.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SalidaConcurrenteSqlServerTest {

    @Autowired
    private ProductRepository productoRepository;

    @Autowired
    private CategoryRepository categoriaRepository;

    @Autowired
    private AlmacenRepository almacenRepository;

    @Autowired
    private LoteRepository loteRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private SalidaInventarioService salidaService;

    @Autowired
    private ExistenciaRepository existenciaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SalidaIdempotenteService salidaIdempotenteService;

    private Long productoId;
    private Long categoriaId;
    private Long almacenId;
    private Long loteId;

    @BeforeEach
    void prepararDatos() {

        String bd = jdbcTemplate.queryForObject(
                "SELECT DB_NAME()",
                String.class
        );

        assertEquals("LM_Medical_Test", bd);

        String codigo = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        Category categoria = categoriaRepository.save(
                new Category("Categoria-" + codigo)
        );

        categoriaId = categoria.getId();

        Product producto = productoRepository.save(
                new Product(
                        "PROD-" + codigo,
                        "Guantes de prueba",
                        "Prueba de concurrencia",
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"),
                        categoria
                )
        );

        productoId = producto.getId();

        Almacen almacen = almacenRepository.save(
                new Almacen(
                        "ALM-" + codigo,
                        "Almacén de pruebas",
                        "Lima"
                )
        );

        almacenId = almacen.getId();

        Lote lote = loteRepository.save(
                new Lote(
                        producto,
                        "LOT-" + codigo,
                        LocalDate.now().plusYears(2)
                )
        );

        loteId = lote.getId();

        inventarioService.registrarEntrada(
                new EntradaInventarioRequest(
                        loteId,
                        almacenId,
                        100,
                        "Stock inicial"
                )
        );
    }

    @Test
    void noDebePermitirDosSalidasSimultaneas() throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);

        Callable<Boolean> operacion = () -> {

            preparados.countDown();

            if (!inicio.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "No se recibió la señal de inicio"
                );
            }

            try {

                salidaService.registrarSalida(
                        new SalidaInventarioRequest(
                                productoId,
                                almacenId,
                                80,
                                "Salida concurrente"
                        )
                );

                return true;

            } catch (ResponseStatusException ex) {

                if (ex.getStatusCode().value() == 409) {
                    return false;
                }

                throw ex;
            }
        };

        try {

            Future<Boolean> empleadoA =
                    executor.submit(operacion);

            Future<Boolean> empleadoB =
                    executor.submit(operacion);

            assertTrue(
                    preparados.await(10, TimeUnit.SECONDS)
            );

            inicio.countDown();

            boolean resultadoA =
                    empleadoA.get(30, TimeUnit.SECONDS);

            boolean resultadoB =
                    empleadoB.get(30, TimeUnit.SECONDS);

            int aprobadas =
                    (resultadoA ? 1 : 0) +
                            (resultadoB ? 1 : 0);

            assertEquals(1, aprobadas);

            Existencia existencia = existenciaRepository
                    .findByLote_IdAndAlmacen_Id(
                            loteId,
                            almacenId
                    )
                    .orElseThrow();

            assertEquals(20, existencia.getCantidad());

            Integer movimientos = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM dbo.movimientos_inventario
                    WHERE lote_id = ?
                    AND tipo = 'SALIDA'
                    """,
                    Integer.class,
                    loteId
            );

            assertEquals(1, movimientos);

        } finally {

            executor.shutdownNow();
        }
    }

    @AfterEach
    void limpiarDatos() {
        if (productoId != null) {

            jdbcTemplate.update(
                    """
                    DELETE FROM dbo.detalles_salida
                    WHERE operacion_clave IN (
                        SELECT clave
                        FROM dbo.operaciones_salida
                        WHERE producto_id = ?
                    )
                    """,
                    productoId
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM dbo.operaciones_salida
                    WHERE producto_id = ?
                    """,
                    productoId
            );
        }

        if (loteId != null) {

            jdbcTemplate.update(
                    "DELETE FROM dbo.movimientos_inventario WHERE lote_id = ?",
                    loteId
            );

            jdbcTemplate.update(
                    "DELETE FROM dbo.existencias WHERE lote_id = ?",
                    loteId
            );

            jdbcTemplate.update(
                    "DELETE FROM dbo.lotes WHERE id = ?",
                    loteId
            );
        }

        if (productoId != null) {
            jdbcTemplate.update(
                    "DELETE FROM dbo.productos WHERE id = ?",
                    productoId
            );
        }

        if (almacenId != null) {
            jdbcTemplate.update(
                    "DELETE FROM dbo.almacenes WHERE id = ?",
                    almacenId
            );
        }

        if (categoriaId != null) {
            jdbcTemplate.update(
                    "DELETE FROM dbo.categorias WHERE id = ?",
                    categoriaId
            );
        }
    }

    @Test
    void debeProcesarUnaSolaSalidaConLaMismaClave() throws Exception {

        String clave = UUID.randomUUID().toString();

        SalidaInventarioRequest request =
                new SalidaInventarioRequest(
                        productoId,
                        almacenId,
                        80,
                        "Prueba de idempotencia concurrente"
                );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);

        Callable<SalidaInventarioResponse> tarea = () -> {

            preparados.countDown();

            if (!inicio.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Tiempo de espera agotado"
                );
            }

            return salidaIdempotenteService.registrar(
                    clave,
                    request
            );
        };

        try {

            Future<SalidaInventarioResponse> solicitudA =
                    executor.submit(tarea);

            Future<SalidaInventarioResponse> solicitudB =
                    executor.submit(tarea);

            assertTrue(
                    preparados.await(10, TimeUnit.SECONDS)
            );

            inicio.countDown();

            SalidaInventarioResponse respuestaA =
                    solicitudA.get(30, TimeUnit.SECONDS);

            SalidaInventarioResponse respuestaB =
                    solicitudB.get(30, TimeUnit.SECONDS);

            assertEquals(respuestaA, respuestaB);

            assertEquals(80, respuestaA.totalRetirado());

            Existencia existencia = existenciaRepository
                    .findByLote_IdAndAlmacen_Id(
                            loteId,
                            almacenId
                    )
                    .orElseThrow();

            assertEquals(20, existencia.getCantidad());

            Integer operaciones = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM dbo.operaciones_salida
                    WHERE clave = ?
                    """,
                    Integer.class,
                    clave
            );

            assertEquals(1, operaciones);

            Integer movimientos = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM dbo.movimientos_inventario
                    WHERE lote_id = ?
                    AND tipo = 'SALIDA'
                    """,
                    Integer.class,
                    loteId
            );

            assertEquals(1, movimientos);

        } finally {

            executor.shutdownNow();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Los hilos de prueba no terminaron"
                );
            }
        }
    }
}