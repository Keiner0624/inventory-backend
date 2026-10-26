package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "lotes", schema = "dbo", uniqueConstraints = @UniqueConstraint(
        name = "UQ_lotes_producto_codigo",
        columnNames = {"producto_id", "codigo"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    public Lote(Product product, String codigo, LocalDate fechaVencimiento) {
        if (product == null){
            throw new IllegalArgumentException("El producto es obligatorio");
        }

        if (codigo == null){
            throw new IllegalArgumentException("El código del lote es obligatorio");
        }

        this.product = product;
        this.codigo = codigo;
        this.fechaVencimiento = fechaVencimiento;
    }
}
