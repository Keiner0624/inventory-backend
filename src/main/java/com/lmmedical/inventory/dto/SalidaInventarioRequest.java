package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.*;

public record SalidaInventarioRequest(

        @NotNull
        @Positive
        Long productoId,

        @NotNull
        @Positive
        Long almacenId,

        @NotNull
        @Positive
        Integer cantidad,

        @Size(max = 250)
        String observacion

) {}