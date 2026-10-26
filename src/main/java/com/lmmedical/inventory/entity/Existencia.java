package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "existencias", schema = "dbo", uniqueConstraints = @UniqueConstraint(
        name = "UQ_existencias_lote_almacen", columnNames = {"lote_id", "almacen_id"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Existencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "almacen_id", nullable = false)
    private Almacen almacen;

    @Column(nullable = false)
    private int cantidad;

    public Existencia(Lote lote, Almacen almacen){
        if (lote == null || almacen == null){
            throw new IllegalArgumentException("El lote y el almacén son obligatorios");
        }
        this.lote = lote;
        this.almacen = almacen;
        this.cantidad = 0;
    }

    public void ingresar(int unidades){
        if (unidades <= 0){
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }

        if (unidades > Integer.MAX_VALUE - cantidad){
            throw new IllegalArgumentException("La cantidad supera el máximo permitido");
        }
        this.cantidad += unidades;
    }
    public void retirar(int unidades){
        if (unidades <= 0){
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }

        if (unidades > cantidad){
            throw new IllegalArgumentException("No hay suficientes unidades disponibles");
        }
        this.cantidad -= unidades;
    }
}

