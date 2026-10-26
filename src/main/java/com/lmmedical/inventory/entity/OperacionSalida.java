package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "operaciones_salida", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperacionSalida {
    @Id
    @Column(length = 36)
    private String clave;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "almacen_id", nullable = false)
    private Long almacenId;

    @Column(nullable = false)
    private int cantidad;

    @Column(length = 250)
    private String observacion;

    @OneToMany(
            mappedBy = "operacion",
            cascade = CascadeType.ALL
    )
    @OrderBy("id ASC")
    private List<DetalleOperacionSalida> detalles = new ArrayList<>();

    public OperacionSalida(String clave, Long productoId, Long almacenId, int cantidad, String observacion) {
        this.clave = Objects.requireNonNull(clave);
        this.productoId = productoId;
        this.almacenId = almacenId;
        this.cantidad = cantidad;
        this.observacion = observacion;
    }

    public boolean coincideCon(Long productoId, Long almacenId,  int cantidad,  String observacion) {
        return Objects.equals(this.productoId, productoId)
                && Objects.equals(this.almacenId, almacenId)
                && this.cantidad == cantidad
                && Objects.equals(this.observacion, observacion);
    }

    public void agregarDetalle(Long loteId, String codigoLote, int cantidadRetirada, int stockRestante){
        detalles.add(
                new DetalleOperacionSalida(
                        this,
                        loteId,
                        codigoLote,
                        cantidadRetirada,
                        stockRestante
                )
        );
    }

}
