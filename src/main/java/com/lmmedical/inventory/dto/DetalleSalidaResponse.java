package com.lmmedical.inventory.dto;

public record DetalleSalidaResponse(
        Long loteId,
        String codigoLote,
        int cantidadRetirada,
        int stockRestante

) {
}
