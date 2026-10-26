package com.lmmedical.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProveedorRequest(
        @NotBlank(message = "El RUC es obligatorio")
        @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 digitos")
        String ruc,

        @NotBlank(message = "La razón social es obligatoria")
        @Size(max = 150)
        String razonSocial,

        @Email(message = "El correo no es válido")
        @Size(max = 150)
        String correo,

        @Size(max = 20)
        String telefono

) {
}
