package com.lmmedical.inventory.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        BigDecimal precioCompra,
        BigDecimal precioVenta,
        String categoria,
        boolean activo
){}
