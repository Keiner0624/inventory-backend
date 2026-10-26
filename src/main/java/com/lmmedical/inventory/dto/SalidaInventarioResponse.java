package com.lmmedical.inventory.dto;

import java.util.List;

public record SalidaInventarioResponse(
        Long productoId,
        Long almacenId,
        int totalRetirado,
        List<DetalleSalidaResponse> detalles
) {
}
