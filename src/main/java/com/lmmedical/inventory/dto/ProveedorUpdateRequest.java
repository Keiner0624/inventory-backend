package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorUpdateRequest(
        @NotBlank
        @Size(max = 150)
        String razonSocial,

        @Email
        @Size(max = 150)
        String correo,

        @Size(max = 20)
        String telefono
) {
}
