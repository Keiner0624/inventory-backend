package com.lmmedical.inventory.dto;

import java.time.LocalDate;

public record ExistenciaResponse(
        Long productoId,
        String productoNombre,

        Long loteId,
        String codigoLote,
        LocalDate fechaVencimiento,

        Long almacenId,
        String almacenNombre,

        int cantidad
) {
}
