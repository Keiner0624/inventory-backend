package com.lmmedical.inventory.dto.usuario;

import java.util.List;

public record UsuarioConsultaResponse(
        Long id,
        String nombre,
        String apellido,
        String correo,
        boolean activo,
        List<String> roles
) {
}
