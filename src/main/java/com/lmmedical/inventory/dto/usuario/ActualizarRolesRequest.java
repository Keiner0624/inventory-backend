package com.lmmedical.inventory.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record ActualizarRolesRequest(@NotEmpty Set<@NotBlank String > roles) {
}
