package com.lmmedical.inventory.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest (
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[!-~]+") String correo,
        @NotBlank @Size(min = 12) String password
){
    @Override public String toString() { return "CrearUsuarioRequest[credenciales=<ocultas>]"; }

}
