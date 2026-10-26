package com.lmmedical.inventory.dto;

import com.lmmedical.inventory.entity.DetalleTransferencia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record TransferenciaResponse(
        Long transferenciaId,
        String claveIdempotencia,
        Long productoId,
        Long origenId,
        Long destinoId,
        int cantidadTotal,
        LocalDateTime fechaCreacion,
        List<DetalleTransferenciaResponse> detalles

) {
    public TransferenciaResponse{
        detalles = List.copyOf(Objects.requireNonNull(detalles));
    }

    public record DetalleTransferenciaResponse(Long loteId, String codigoLote, int cantidad){

    }
}
