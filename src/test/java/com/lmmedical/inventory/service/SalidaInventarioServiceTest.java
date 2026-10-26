package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalidaInventarioServiceTest {

    @Mock
    private ProductRepository productoRepository;

    @Mock
    private AlmacenRepository almacenRepository;

    @Mock
    private LoteRepository loteRepository;

    @Mock
    private ExistenciaRepository existenciaRepository;

    @Mock
    private MovimientoInventarioRepository movimientoRepository;

    @InjectMocks
    private SalidaInventarioService service;

    @Test
    void debeRetirarSegunAlgoritmoFEFO() {

        Product producto = mock(Product.class);
        Almacen almacen = mock(Almacen.class);

        Lote lote1 = mock(Lote.class);
        Lote lote2 = mock(Lote.class);

        Existencia existencia1 =
                new Existencia(lote1, almacen);

        Existencia existencia2 =
                new Existencia(lote2, almacen);

        existencia1.ingresar(100);
        existencia2.ingresar(300);

        LocalDate hoy = LocalDate.now(
                ZoneId.of("America/Lima")
        );

        when(productoRepository.findById(1L))
                .thenReturn(Optional.of(producto));

        when(producto.isActivo()).thenReturn(true);
        when(producto.getId()).thenReturn(1L);

        when(almacenRepository.findById(1L))
                .thenReturn(Optional.of(almacen));

        when(almacen.isActivo()).thenReturn(true);
        when(almacen.getId()).thenReturn(1L);

        when(lote1.getId()).thenReturn(10L);
        when(lote2.getId()).thenReturn(20L);

        when(lote1.getCodigo()).thenReturn("LOT-001");
        when(lote2.getCodigo()).thenReturn("LOT-002");

        when(lote1.getFechaVencimiento())
                .thenReturn(hoy.plusDays(30));

        when(lote2.getFechaVencimiento())
                .thenReturn(hoy.plusDays(180));

        when(loteRepository.findByProduct_IdOrderByIdAsc(1L))
                .thenReturn(List.of(lote1, lote2));

        when(loteRepository.buscarParaMovimiento(10L))
                .thenReturn(Optional.of(lote1));

        when(loteRepository.buscarParaMovimiento(20L))
                .thenReturn(Optional.of(lote2));

        when(existenciaRepository.findByLote_IdAndAlmacen_Id(10L, 1L))
                .thenReturn(Optional.of(existencia1));

        when(existenciaRepository.findByLote_IdAndAlmacen_Id(20L, 1L))
                .thenReturn(Optional.of(existencia2));

        SalidaInventarioRequest request =
                new SalidaInventarioRequest(
                        1L,
                        1L,
                        250,
                        "Prueba FEFO"
                );

        SalidaInventarioResponse respuesta =
                service.registrarSalida(request);

        assertEquals(250, respuesta.totalRetirado());

        assertEquals(2, respuesta.detalles().size());

        assertEquals(
                100,
                respuesta.detalles().get(0).cantidadRetirada()
        );

        assertEquals(
                150,
                respuesta.detalles().get(1).cantidadRetirada()
        );

        assertEquals(0, existencia1.getCantidad());

        assertEquals(150, existencia2.getCantidad());

        verify(
                movimientoRepository,
                times(2)
        ).save(any(MovimientoInventario.class));
    }
}
