package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductUpdateRequest (
        @NotBlank
        @Size(max = 30)
        String codigo,

        @NotBlank
        @Size(max = 150)
        String nombre,

        @Size(max = 500)
        String descripcion,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal precioCompra,

        @NotNull
        @Positive
        Long categoriaId
){}
