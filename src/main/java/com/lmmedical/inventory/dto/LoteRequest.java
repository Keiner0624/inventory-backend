package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record LoteRequest(
        @NotNull
        @Positive
        Long productoId,

        @NotBlank
        @Size(max = 50)
        String codigo,

        @FutureOrPresent
        LocalDate fechaVencimiento
) {
}
