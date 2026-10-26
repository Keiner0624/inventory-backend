package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EntradaInventarioRequest(
        @NotNull
        @Positive
        Long loteId,

        @NotNull
        @Positive
        Long almacenId,

        @NotNull
        @Positive
        Integer cantidad,

        @Size(max = 250)
        String observacion
) {
}
