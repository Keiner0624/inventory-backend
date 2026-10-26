package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlmacenRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 20)
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120)
        String nombre,

        @NotBlank(message = "La ubicación es obligatoria")
        @Size(max = 200)
        String ubicacion
) {
}
