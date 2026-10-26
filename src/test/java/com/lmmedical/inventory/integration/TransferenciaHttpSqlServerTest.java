package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TransferenciaHttpSqlServerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categorias;
    @Autowired private ProductRepository productos;
    @Autowired private AlmacenRepository almacenes;
    @Autowired private LoteRepository lotes;
    @Autowired private ExistenciaRepository existencias;
    @Autowired private TransferenciaRepository transferencias;

    private Product producto;
    private Almacen origen;
    private Almacen destino;
    private Lote primero;
    private Lote segundo;

    @BeforeEach
    void prepararDatos() {
        assertEquals("LM_Medical_Test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        String codigo = UUID.randomUUID().toString().substring(0, 8);
        Category categoria = categorias.save(new Category("Transferencia-" + codigo));
        producto = productos.save(new Product("TR-" + codigo, "Producto de prueba",
                "Transferencia HTTP", new BigDecimal("10.00"), new BigDecimal("20.00"), categoria));
        origen = almacenes.save(new Almacen("OR-" + codigo, "Origen de prueba", "Lima"));
        destino = almacenes.save(new Almacen("DE-" + codigo, "Destino de prueba", "Lima"));
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        primero = lotes.save(new Lote(producto, "A-" + codigo, hoy.plusDays(30)));
        segundo = lotes.save(new Lote(producto, "B-" + codigo, hoy.plusDays(180)));
        ingresar(primero, origen, 100);
        ingresar(segundo, origen, 200);
        ingresar(segundo, destino, 20);
    }

    @Test
    void postDebeTransferirPorFefoYConservarElStockTotal() throws Exception {
        String clave = UUID.randomUUID().toString();
        enviar(clave, destino.getId(), 150)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferenciaId").isNumber())
                .andExpect(jsonPath("$.destinoId").value(destino.getId().intValue()))
                .andExpect(jsonPath("$.cantidadTotal").value(150))
                .andExpect(jsonPath("$.fechaCreacion").isNotEmpty())
                .andExpect(jsonPath("$.detalles[0].loteId").value(primero.getId().intValue()))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(100))
                .andExpect(jsonPath("$.detalles[1].cantidad").value(50));

        assertEquals(0, stock(primero, origen));
        assertEquals(150, stock(segundo, origen));
        assertEquals(100, stock(primero, destino));
        assertEquals(70, stock(segundo, destino));
        assertEquals(320, stock(primero, origen) + stock(segundo, origen)
                + stock(primero, destino) + stock(segundo, destino));
        assertEquals(4, movimientos());
        assertTrue(transferencias.findByClaveIdempotencia(clave).isPresent());
    }

    @Test
    void repetirLaClaveDebeDevolverLaMismaRespuestaSinMoverStockOtraVez() throws Exception {
        String clave = UUID.randomUUID().toString();
        String respuesta = enviar(clave, destino.getId(), 150)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        enviar(clave, destino.getId(), 150).andExpect(status().isCreated())
                .andExpect(content().json(respuesta));
        assertEquals(150, stock(segundo, origen));
        assertEquals(70, stock(segundo, destino));
        assertEquals(4, movimientos());
    }

    @Test
    void laMismaClaveConOtrosDatosDebeRechazarse() throws Exception {
        String clave = UUID.randomUUID().toString();
        enviar(clave, destino.getId(), 150).andExpect(status().isCreated());
        enviar(clave, destino.getId(), 151).andExpect(status().isConflict());
        assertEquals(4, movimientos());
    }

    @Test
    void stockInsuficienteNoDebeCrearTransferenciasNiMovimientos() throws Exception {
        String clave = UUID.randomUUID().toString();
        enviar(clave, destino.getId(), 301).andExpect(status().isConflict());
        assertEquals(100, stock(primero, origen));
        assertEquals(200, stock(segundo, origen));
        assertEquals(20, stock(segundo, destino));
        assertEquals(0, movimientos());
        assertTrue(transferencias.findByClaveIdempotencia(clave).isEmpty());
    }

    @Test
    void destinoInexistenteDebeDarUn404DeNegocio() throws Exception {
        enviar(UUID.randomUUID().toString(), Long.MAX_VALUE, 150)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Almacén de destino no encontrado"));
    }

    @Test
    void noDebeAceptarElMismoAlmacenComoOrigenYDestino() throws Exception {
        enviar(UUID.randomUUID().toString(), origen.getId(), 150)
                .andExpect(status().isBadRequest());
        assertEquals(0, movimientos());
    }

    @Test
    void destinoEsObligatorioEnElJson() throws Exception {
        mockMvc.perform(post("/api/transferencias").with(user("prueba")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"productoId":%d,"origenId":%d,"cantidad":150}
                                """.formatted(producto.getId(), origen.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.destinoId").isNotEmpty());
    }

    @Test
    void claveInvalidaDebeRechazarseAntesDeMoverStock() throws Exception {
        enviar("clave-invalida", destino.getId(), 150).andExpect(status().isBadRequest());
        assertEquals(0, movimientos());
    }

    private ResultActions enviar(String clave, Long destinoId, int cantidad) throws Exception {
        return mockMvc.perform(post("/api/transferencias").with(user("prueba")).with(csrf())
                .header("Idempotency-Key", clave).contentType("application/json")
                .content("""
                        {"productoId":%d,"origenId":%d,"destinoId":%d,
                         "cantidad":%d,"observacion":"Reposición de inventario"}
                        """.formatted(producto.getId(), origen.getId(), destinoId, cantidad)));
    }

    private void ingresar(Lote lote, Almacen almacen, int cantidad) {
        Existencia existencia = new Existencia(lote, almacen);
        existencia.ingresar(cantidad);
        existencias.saveAndFlush(existencia);
    }

    private int stock(Lote lote, Almacen almacen) {
        return existencias.findByLote_IdAndAlmacen_Id(lote.getId(), almacen.getId())
                .map(Existencia::getCantidad).orElse(0);
    }

    private int movimientos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.movimientos_inventario WHERE lote_id IN (?, ?)",
                Integer.class, primero.getId(), segundo.getId());
    }
}
