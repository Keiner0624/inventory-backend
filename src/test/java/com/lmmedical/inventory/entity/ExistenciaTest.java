package com.lmmedical.inventory.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ExistenciaTest {

    private Existencia existencia;

    @BeforeEach
    void configurar() {

        Lote lote = mock(Lote.class);
        Almacen almacen = mock(Almacen.class);

        existencia = new Existencia(lote, almacen);
    }

    @Test
    void debeIngresarUnidadesCorrectamente() {

        existencia.ingresar(100);
        existencia.ingresar(50);

        assertEquals(150, existencia.getCantidad());
    }

    @Test
    void debeRetirarUnidadesCorrectamente() {

        existencia.ingresar(100);

        existencia.retirar(40);

        assertEquals(60, existencia.getCantidad());
    }

    @Test
    void noDebePermitirRetirarMasDelStockDisponible() {

        existencia.ingresar(100);

        assertThrows(
                IllegalArgumentException.class,
                () -> existencia.retirar(150)
        );

        assertEquals(100, existencia.getCantidad());
    }

    @Test
    void noDebePermitirIngresarCantidadesNegativas() {

        assertThrows(
                IllegalArgumentException.class,
                () -> existencia.ingresar(-50)
        );

        assertEquals(0, existencia.getCantidad());
    }

    @Test
    void noDebePermitirRetirarCeroUnidades() {

        existencia.ingresar(100);

        assertThrows(
                IllegalArgumentException.class,
                () -> existencia.retirar(0)
        );
    }

    @Test
    void noDebePermitirDesbordamientoDeStock() {

        existencia.ingresar(Integer.MAX_VALUE);

        assertThrows(
                IllegalArgumentException.class,
                () -> existencia.ingresar(1)
        );

        assertEquals(
                Integer.MAX_VALUE,
                existencia.getCantidad()
        );
    }
}