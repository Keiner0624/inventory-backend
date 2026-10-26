package com.lmmedical.inventory.dto.usuario;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull(message = "El estado activo es obligatorio") Boolean activo) {
}
