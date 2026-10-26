package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "detalles_salida", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetalleOperacionSalida {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "operacion_clave",
            nullable = false
    )
    private OperacionSalida operacion;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(name = "codigo_lote", nullable = false)
    private String codigoLote;

    @Column(name = "cantidad_retirada", nullable = false)
    private int cantidadRetirada;

    @Column(name = "stock_restante", nullable = false)
    private int stockRestante;

    public DetalleOperacionSalida(OperacionSalida operacion, Long loteId,  String codigoLote, int cantidadRetirada, int stockRestante) {
        this.operacion = operacion;
        this.loteId = loteId;
        this.codigoLote = codigoLote;
        this.cantidadRetirada = cantidadRetirada;
        this.stockRestante = stockRestante;
    }
}
