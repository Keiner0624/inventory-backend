package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferenciaRequest(
        @NotNull(message = "El producto es obligatorio")
        @Positive(message = "El ID del producto debe ser positivo")
        Long productoId,

        @NotNull(message = "El almacén de origen es obligatorio")
        @Positive(message = "El ID del origen debe ser positivo")
        Long origenId,

        @NotNull(message = "El almacén de destino es obligatorio")
        @Positive(message = "El ID del destino debe ser positivo")
        Long destinoId,

        @Positive(message = "La cantidad debe ser mayor a cero")
        int cantidad,

        @Size(max = 250, message = "La observación no puede superar 250 caracteres")
        String observacion
) {
}
