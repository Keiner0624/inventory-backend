package com.lmmedical.inventory.dto;

public record EntradaInventarioResponse(
        Long movimientoId,
        Long loteId,
        Long almacenId,
        int cantidadIngresada,
        int stockActual
) {
}
