package com.lmmedical.inventory.dto.usuario;

public record UsuarioResponse(
        Long id,
        String nombre,
        String apellido,
        String correo,
        boolean activo,
        String rol
) {
}
