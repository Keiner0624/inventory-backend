package com.lmmedical.inventory.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String correo,
        @NotBlank String password
) {
    @Override public String toString() { return "LoginRequest[credenciales=<ocultas>]"; }
}
