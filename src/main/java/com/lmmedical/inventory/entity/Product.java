package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "precio_compra", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCompra;

    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Category categoria;

    @Column(nullable = false)
    private boolean activo = true;

    public Product(String codigo, String nombre, String descripcion, BigDecimal precioCompra, BigDecimal precioVenda, Category categoria) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioCompra = precioCompra;
        this.precioVenda = precioVenda;
        this.categoria = categoria;
        this.activo = true;
    }

    public void actualizarDatos(String codigo, String nombre, String descripcion, BigDecimal precioCompra, BigDecimal precioVenda, Category categoria) {
        if (!activo){
            throw new IllegalStateException("No se puede actualizar un producto inactivo");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código es obligatorio");
        }

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        if (precioCompra == null || precioCompra.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio compra no puede ser negativo");
        }

        if (precioVenda == null || precioVenda.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio venta debe ser mayor a cero");
        }

        if (categoria == null){
            throw new IllegalArgumentException("El categoria es obligatorio");
        }

        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioCompra = precioCompra;
        this.precioVenda = precioVenda;
        this.categoria = categoria;
    }

    public void desactivar(){
        this.activo = false;
    }
}
