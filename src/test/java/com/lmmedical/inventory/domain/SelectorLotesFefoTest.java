package com.lmmedical.inventory.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelectorLotesFefoTest {

    private final SelectorLotesFefo selector =
            new SelectorLotesFefo();

    private final LocalDate hoy =
            LocalDate.of(2026, 10, 17);

    @Test
    void debeSeleccionarPrimeroLosLotesProximosAVencer() {

        var lotes = List.of(
                new SelectorLotesFefo.LoteDisponible(
                        1L,
                        LocalDate.of(2026, 12, 10),
                        100
                ),
                new SelectorLotesFefo.LoteDisponible(
                        2L,
                        LocalDate.of(2026, 10, 25),
                        50
                ),
                new SelectorLotesFefo.LoteDisponible(
                        3L,
                        LocalDate.of(2026, 11, 15),
                        80
                )
        );

        var resultado = selector.seleccionar(
                lotes,
                120,
                hoy
        );

        assertEquals(2, resultado.size());

        assertEquals(2L, resultado.get(0).loteId());
        assertEquals(50, resultado.get(0).cantidad());

        assertEquals(3L, resultado.get(1).loteId());
        assertEquals(70, resultado.get(1).cantidad());
    }

    @Test
    void noDebeSeleccionarLotesVencidos() {

        var lotes = List.of(
                new SelectorLotesFefo.LoteDisponible(
                        1L,
                        LocalDate.of(2026, 10, 10),
                        100
                ),
                new SelectorLotesFefo.LoteDisponible(
                        2L,
                        LocalDate.of(2026, 11, 10),
                        50
                )
        );

        var resultado = selector.seleccionar(
                lotes,
                40,
                hoy
        );

        assertEquals(1, resultado.size());
        assertEquals(2L, resultado.get(0).loteId());
        assertEquals(40, resultado.get(0).cantidad());
    }

    @Test
    void debeRechazarStockInsuficiente() {

        var lotes = List.of(
                new SelectorLotesFefo.LoteDisponible(
                        1L,
                        LocalDate.of(2026, 12, 10),
                        30
                )
        );

        assertThrows(
                StockInsuficienteException.class,
                () -> selector.seleccionar(
                        lotes,
                        100,
                        hoy
                )
        );
    }

    @Test
    void debeRechazarCantidadesInvalidas() {

        assertThrows(
                IllegalArgumentException.class,
                () -> selector.seleccionar(
                        List.of(),
                        0,
                        hoy
                )
        );
    }

    @Test
    void debePriorizarLotesConFechaSobreLotesSinFecha() {

        var lotes = List.of(
                new SelectorLotesFefo.LoteDisponible(
                        1L,
                        null,
                        100
                ),
                new SelectorLotesFefo.LoteDisponible(
                        2L,
                        LocalDate.of(2026, 11, 10),
                        50
                )
        );

        var resultado = selector.seleccionar(
                lotes,
                70,
                hoy
        );

        assertEquals(2, resultado.size());

        assertEquals(2L, resultado.get(0).loteId());
        assertEquals(50, resultado.get(0).cantidad());

        assertEquals(1L, resultado.get(1).loteId());
        assertEquals(20, resultado.get(1).cantidad());
    }
}