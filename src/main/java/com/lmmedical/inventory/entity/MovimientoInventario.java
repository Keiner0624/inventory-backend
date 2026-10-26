package com.lmmedical.inventory.entity;

import com.lmmedical.inventory.enums.TipoMovimiento;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "movimientos_inventario", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "almacen_id", nullable = false)
    private Almacen almacen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private int cantidad;

    @Column(length = 250)
    private String observacion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public MovimientoInventario(
            Lote lote,
            Almacen almacen,
            int cantidad,
            String observacion
    ) {
        this(
                lote,
                almacen,
                TipoMovimiento.ENTRADA,
                cantidad,
                observacion
        );
    }

    private MovimientoInventario(
            Lote lote,
            Almacen almacen,
            TipoMovimiento tipo,
            int cantidad,
            String observacion
    ) {

        if (lote == null || almacen == null || tipo == null) {
            throw new IllegalArgumentException(
                    "Los datos del movimiento son obligatorios"
            );
        }

        if (cantidad <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser positiva"
            );
        }

        if (observacion != null && observacion.length() > 250) {
            throw new IllegalArgumentException(
                    "La observación supera los 250 caracteres"
            );
        }

        this.lote = lote;
        this.almacen = almacen;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.observacion = observacion;
        this.fecha = LocalDateTime.now(ZoneOffset.UTC);
    }

    public static MovimientoInventario salida(
            Lote lote,
            Almacen almacen,
            int cantidad,
            String observacion
    ) {
        return new MovimientoInventario(
                lote,
                almacen,
                TipoMovimiento.SALIDA,
                cantidad,
                observacion
        );
    }
}