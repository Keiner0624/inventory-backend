package com.lmmedical.inventory.dto;

import java.time.LocalDate;

public record LoteResponse(
        Long id,
        Long productoId,
        String codigo,
        LocalDate fechaVencimiento
) {
}
