package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.domain.StockInsuficienteException;
import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.service.TransferenciaService;
import jdk.dynalink.linker.LinkerServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransferenciaTransaccionalSqlServerTest {

    @Autowired
    private TransferenciaService transferenciaService;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository productos;

    @Autowired
    private AlmacenRepository almacenes;

    @Autowired
    private LoteRepository lotes;

    @Autowired
    private ExistenciaRepository existencias;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoriaId;
    private Long productoId;
    private Long origenId;
    private Long destinoId;
    private Long loteId;

    @BeforeEach
    void prepararDatos() {

        verificarBaseDePruebas();

        String codigo = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        Category categoria = categorias.saveAndFlush(
                new Category("Transaccional-" + codigo)
        );

        categoriaId = categoria.getId();

        Product producto = productos.saveAndFlush(
                new Product(
                        "PR-" + codigo,
                        "Guantes médicos",
                        "Prueba de transacciones",
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"),
                        categoria
                )
        );

        productoId = producto.getId();

        Almacen origen = almacenes.saveAndFlush(
                new Almacen(
                        "OR-" + codigo,
                        "Almacén origen",
                        "Lima"
                )
        );

        origenId = origen.getId();

        Almacen destino = almacenes.saveAndFlush(
                new Almacen(
                        "DE-" + codigo,
                        "Almacén destino",
                        "Arequipa"
                )
        );

        destinoId = destino.getId();

        LocalDate vencimiento = LocalDate
                .now(ZoneId.of("America/Lima"))
                .plusYears(2);

        Lote lote = lotes.saveAndFlush(
                new Lote(
                        producto,
                        "LT-" + codigo,
                        vencimiento
                )
        );

        loteId = lote.getId();

        registrarExistencia(lote, origen, 100);
    }

    @Test
    void debeConfirmarTransferenciaEnSqlServer() {

        String clave = UUID.randomUUID().toString();

        TransferenciaResponse respuesta =
                transferenciaService.registrar(
                        clave,
                        crearRequest(40)
                );

        assertNotNull(respuesta.transferenciaId());

        assertEquals(40, respuesta.cantidadTotal());

        assertEquals(60, stock(origenId));
        assertEquals(40, stock(destinoId));

        assertEquals(
                100,
                stock(origenId) + stock(destinoId)
        );

        assertEquals(1, contarTransferencias());

        assertEquals(
                1,
                contarMovimientos(origenId, "SALIDA")
        );

        assertEquals(
                1,
                contarMovimientos(destinoId, "ENTRADA")
        );
    }

    @Test
    void debeRevertirTodoSiFallaElDestino() {

        Lote lote = lotes.findById(loteId)
                .orElseThrow();

        Almacen destino = almacenes.findById(destinoId)
                .orElseThrow();

        registrarExistencia(
                lote,
                destino,
                Integer.MAX_VALUE
        );

        assertThrows(
                RuntimeException.class,
                () -> transferenciaService.registrar(
                        UUID.randomUUID().toString(),
                        crearRequest(2)
                )
        );

        assertEquals(100, stock(origenId));

        assertEquals(
                Integer.MAX_VALUE,
                stock(destinoId)
        );

        assertEquals(0, contarTransferencias());

        assertEquals(
                0,
                contarMovimientos(origenId, "SALIDA")
        );

        assertEquals(
                0,
                contarMovimientos(destinoId, "ENTRADA")
        );
    }

    @Test
    void debeEvitarDuplicadosEntreTransacciones() {

        String clave = UUID.randomUUID().toString();

        TransferenciaRequest request =
                crearRequest(30);

        TransferenciaResponse primera =
                transferenciaService.registrar(
                        clave,
                        request
                );

        TransferenciaResponse segunda =
                transferenciaService.registrar(
                        clave,
                        request
                );

        assertEquals(
                primera.transferenciaId(),
                segunda.transferenciaId()
        );

        assertEquals(70, stock(origenId));
        assertEquals(30, stock(destinoId));

        assertEquals(1, contarTransferencias());

        assertEquals(
                1,
                contarMovimientos(origenId, "SALIDA")
        );

        assertEquals(
                1,
                contarMovimientos(destinoId, "ENTRADA")
        );
    }

    private TransferenciaRequest crearRequest(
            int cantidad
    ) {

        return new TransferenciaRequest(
                productoId,
                origenId,
                destinoId,
                cantidad,
                "Prueba transaccional"
        );
    }

    private void registrarExistencia(
            Lote lote,
            Almacen almacen,
            int cantidad
    ) {

        Existencia existencia = new Existencia(
                lote,
                almacen
        );

        existencia.ingresar(cantidad);

        existencias.saveAndFlush(existencia);
    }

    private int stock(Long almacenId) {

        Integer cantidad = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(cantidad), 0)
                FROM dbo.existencias
                WHERE lote_id = ?
                  AND almacen_id = ?
                """,
                Integer.class,
                loteId,
                almacenId
        );

        return cantidad == null ? 0 : cantidad;
    }

    private int contarTransferencias() {

        Integer total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.transferencias
                WHERE producto_id = ?
                """,
                Integer.class,
                productoId
        );

        return total == null ? 0 : total;
    }

    private int contarMovimientos(
            Long almacenId,
            String tipo
    ) {

        Integer total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.movimientos_inventario
                WHERE lote_id = ?
                  AND almacen_id = ?
                  AND tipo = ?
                """,
                Integer.class,
                loteId,
                almacenId,
                tipo
        );

        return total == null ? 0 : total;
    }

    private void verificarBaseDePruebas() {

        String baseActual = jdbc.queryForObject(
                "SELECT DB_NAME()",
                String.class
        );

        assertEquals(
                "LM_Medical_Test",
                baseActual,
                "No se está utilizando la base de pruebas"
        );
    }

    @AfterEach
    void limpiarDatos() {

        verificarBaseDePruebas();

        if (productoId != null) {

            jdbc.update(
                    """
                    DELETE FROM dbo.detalles_transferencia
                    WHERE transferencia_id IN (
                        SELECT id
                        FROM dbo.transferencias
                        WHERE producto_id = ?
                    )
                    """,
                    productoId
            );

            jdbc.update(
                    """
                    DELETE FROM dbo.transferencias
                    WHERE producto_id = ?
                    """,
                    productoId
            );
        }

        if (loteId != null) {

            jdbc.update(
                    """
                    DELETE FROM dbo.movimientos_inventario
                    WHERE lote_id = ?
                    """,
                    loteId
            );

            jdbc.update(
                    """
                    DELETE FROM dbo.existencias
                    WHERE lote_id = ?
                    """,
                    loteId
            );

            jdbc.update(
                    "DELETE FROM dbo.lotes WHERE id = ?",
                    loteId
            );
        }

        if (productoId != null) {
            jdbc.update(
                    "DELETE FROM dbo.productos WHERE id = ?",
                    productoId
            );
        }

        if (origenId != null) {
            jdbc.update(
                    "DELETE FROM dbo.almacenes WHERE id = ?",
                    origenId
            );
        }

        if (destinoId != null) {
            jdbc.update(
                    "DELETE FROM dbo.almacenes WHERE id = ?",
                    destinoId
            );
        }

        if (categoriaId != null) {
            jdbc.update(
                    "DELETE FROM dbo.categorias WHERE id = ?",
                    categoriaId
            );
        }
    }

    @Test
    void noDebePermitirTransferenciasQueSuperenElStock() throws Exception{
        String claveA = UUID.randomUUID().toString();
        String claveB = UUID.randomUUID().toString();

        List<Boolean> resultados = ejecutarConcurrentemente(
                () -> intentarTransferencia(claveA),
        () -> intentarTransferencia(claveB));
        long aprobadas = resultados.stream().filter(Boolean::booleanValue).count();
        assertEquals(1, aprobadas);
        assertEquals(20, stock(origenId));
        assertEquals(80, stock(destinoId));
        assertEquals(100, stock(origenId) +  stock(destinoId));
        assertEquals(1, contarTransferencias());
        assertEquals(1, contarMovimientos(origenId, "SALIDA"));
        assertEquals(1, contarMovimientos(destinoId, "ENTRADA"));
    }

    private boolean intentarTransferencia(String clave) {
        try {
            transferenciaService.registrar(clave, crearRequest(80));
            return true;
        }catch (StockInsuficienteException exception){
            return false;
        }catch (ResponseStatusException exception){
            if (exception.getStatusCode().value() == 409){
                return false;
            }
            throw exception;
        }
    }

    @Test
    void mismaClaveConcurrenteDebeCrearUnaSolaTransferencia()
            throws Exception {

        String clave = UUID.randomUUID().toString();

        TransferenciaRequest request = crearRequest(80);

        List<TransferenciaResponse> respuestas =
                ejecutarConcurrentemente(
                        () -> transferenciaService.registrar(
                                clave,
                                request
                        ),
                        () -> transferenciaService.registrar(
                                clave,
                                request
                        )
                );

        TransferenciaResponse primera = respuestas.get(0);
        TransferenciaResponse segunda = respuestas.get(1);

        assertEquals(
                primera.transferenciaId(),
                segunda.transferenciaId()
        );

        assertEquals(
                primera.claveIdempotencia(),
                segunda.claveIdempotencia()
        );

        assertEquals(80, primera.cantidadTotal());
        assertEquals(80, segunda.cantidadTotal());

        assertEquals(20, stock(origenId));
        assertEquals(80, stock(destinoId));

        assertEquals(1, contarTransferencias());

        assertEquals(
                1,
                contarMovimientos(origenId, "SALIDA")
        );

        assertEquals(
                1,
                contarMovimientos(destinoId, "ENTRADA")
        );
    }

    private <T> List<T> ejecutarConcurrentemente(
            Callable<T> tareaA,
            Callable<T> tareaB
    ) throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);

        Callable<T> primera = () -> {

            preparados.countDown();

            if (!inicio.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Tiempo de espera agotado"
                );
            }

            return tareaA.call();
        };

        Callable<T> segunda = () -> {

            preparados.countDown();

            if (!inicio.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Tiempo de espera agotado"
                );
            }

            return tareaB.call();
        };

        try {

            Future<T> resultadoA = executor.submit(primera);
            Future<T> resultadoB = executor.submit(segunda);

            assertTrue(
                    preparados.await(10, TimeUnit.SECONDS),
                    "Los hilos no estuvieron preparados"
            );

            inicio.countDown();

            T valorA = resultadoA.get(
                    30,
                    TimeUnit.SECONDS
            );

            T valorB = resultadoB.get(
                    30,
                    TimeUnit.SECONDS
            );

            return List.of(valorA, valorB);

        } finally {

            inicio.countDown();

            executor.shutdownNow();

            if (!executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
            )) {
                throw new IllegalStateException(
                        "Los hilos no finalizaron correctamente"
                );
            }
        }
    }

    @Test
    void transferenciasEnSentidosOpuestosDebenConservarElStock()
            throws Exception {

        Lote lote = lotes.findById(loteId)
                .orElseThrow();

        Almacen almacenDestino = almacenes.findById(destinoId)
                .orElseThrow();

        registrarExistencia(
                lote,
                almacenDestino,
                100
        );

        String claveA = UUID.randomUUID().toString();
        String claveB = UUID.randomUUID().toString();

        TransferenciaRequest transferenciaA =
                new TransferenciaRequest(
                        productoId,
                        origenId,
                        destinoId,
                        40,
                        "Transferencia A hacia B"
                );

        TransferenciaRequest transferenciaB =
                new TransferenciaRequest(
                        productoId,
                        destinoId,
                        origenId,
                        30,
                        "Transferencia B hacia A"
                );

        List<TransferenciaResponse> resultados =
                ejecutarConcurrentemente(
                        () -> transferenciaService.registrar(
                                claveA,
                                transferenciaA
                        ),
                        () -> transferenciaService.registrar(
                                claveB,
                                transferenciaB
                        )
                );

        assertEquals(2, resultados.size());

        assertEquals(90, stock(origenId));
        assertEquals(110, stock(destinoId));

        assertEquals(
                200,
                stock(origenId) + stock(destinoId)
        );

        assertEquals(2, contarTransferencias());

        assertEquals(
                1,
                contarMovimientos(origenId, "SALIDA")
        );

        assertEquals(
                1,
                contarMovimientos(origenId, "ENTRADA")
        );

        assertEquals(
                1,
                contarMovimientos(destinoId, "SALIDA")
        );

        assertEquals(
                1,
                contarMovimientos(destinoId, "ENTRADA")
        );
    }
}