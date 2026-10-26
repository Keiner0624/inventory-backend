package com.lmmedical.inventory.dto;

public record ProveedorResponse(
        Long id,
        String ruc,
        String razonSocial,
        String correo,
        String telefono,
        boolean activo
) {
}
