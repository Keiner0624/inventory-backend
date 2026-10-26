package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "transferencias", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transferencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "clave_idempotencia",
            nullable = false,
            unique = true,
            length = 36
    )
    private String claveIdempotencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Product producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    private Almacen origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    private Almacen destino;

    @Column(name = "cantidad_total", nullable = false)
    private int cantidadTotal;

    @Nationalized
    @Column(length = 250)
    private String observacion;

    @Column(
            name = "creada_en",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime creadaEn;

    @OneToMany(
            mappedBy = "transferencia",
            cascade = CascadeType.ALL
    )
    @OrderBy("id ASC")
    private List<DetalleTransferencia> detalles = new ArrayList<>();

    public Transferencia(String claveIdempotencia, Product producto, Almacen origen, Almacen destino, int cantidadTotal,  String observacion) {
        if (cantidadTotal <= 0){
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }

        Objects.requireNonNull(producto);
        Objects.requireNonNull(origen);
        Objects.requireNonNull(destino);

        if (Objects.equals(origen.getId(), destino.getId())){
            throw new IllegalArgumentException("Los almacenes deben ser diferentes");
        }

        this.claveIdempotencia = Objects.requireNonNull(claveIdempotencia);
        this.producto = producto;
        this.origen = origen;
        this.destino = destino;
        this.cantidadTotal = cantidadTotal;
        this.observacion = observacion;
    }

    public void agregarDetalle(Lote lote, int cantidad){
        if (cantidad <= 0){
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }
        detalles.add(new DetalleTransferencia(this, lote, cantidad));
    }
}
