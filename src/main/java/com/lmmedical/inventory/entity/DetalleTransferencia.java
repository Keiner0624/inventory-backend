package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "detalles_transferencia", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetalleTransferencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transferencia_id", nullable = false)
    private Transferencia transferencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id",  nullable = false)
    private Lote lote;

    @Column(nullable = false)
    private int cantidad;

    public DetalleTransferencia(Transferencia transferencia, Lote lote, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantiad debe ser positiva");
        }
        this.transferencia = transferencia;
        this.lote = lote;
        this.cantidad = cantidad;
    }
}
