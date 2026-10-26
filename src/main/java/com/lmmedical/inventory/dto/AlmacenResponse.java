package com.lmmedical.inventory.dto;

public record AlmacenResponse(
        Long id,
        String codigo,
        String nombre,
        String ubicacion,
        boolean activo
) {

}
