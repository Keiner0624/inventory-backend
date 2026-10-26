package com.lmmedical.inventory.domain;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public final class SelectorLotesFefo {
    public record LoteDisponible(Long loteId, LocalDate fechaVencimiento, int cantidadDisponible) {
        public LoteDisponible{
            Objects.requireNonNull(loteId, "El loteId es obligatorio");

            if (loteId <= 0) {
                throw new IllegalArgumentException("El loteId debe ser positivo");
            }

            if (cantidadDisponible < 0) {
                throw new IllegalArgumentException("Las existencias no pueden ser negativas");
            }
        }
    }
    public record AsignacionLote(Long loteId, int cantidad){}

    public List<AsignacionLote> seleccionar(List<LoteDisponible> lotes, int cantidadSolicitada, LocalDate fechaActual){
        Objects.requireNonNull(lotes);
        Objects.requireNonNull(fechaActual);

        if (cantidadSolicitada <= 0) {
            throw new IllegalArgumentException("La cantidad solicitada debe ser positiva");
        }
        validarLotesDuplicados(lotes);

        List<LoteDisponible> disponibles = lotes.stream()
                .filter(lote -> lote.cantidadDisponible() > 0)
                .filter(lote -> lote.fechaVencimiento() == null
                        || !lote.fechaVencimiento().isBefore(fechaActual))
                .sorted(
                        Comparator.comparing(
                                LoteDisponible::fechaVencimiento,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ).thenComparing(LoteDisponible::loteId)
                ).toList();

        long stockTotal = disponibles.stream()
                .mapToLong(LoteDisponible::cantidadDisponible).sum();

        if (stockTotal < cantidadSolicitada) {
            throw new StockInsuficienteException("Stock insuficiente. Disponible: "
            + stockTotal
            + ", solicitado: "
            + cantidadSolicitada
            );
        }

        List<AsignacionLote> asignaciones = new ArrayList<>();

        int pendiente = cantidadSolicitada;

        for (LoteDisponible lote : disponibles) {
            if (pendiente == 0) {
                break;
            }
            int cantidad = Math.min(pendiente, lote.cantidadDisponible());
            asignaciones.add(new AsignacionLote(lote.loteId(), cantidad));
            pendiente -= cantidad;
        }
        return List.copyOf(asignaciones);
    }

    private void validarLotesDuplicados(List<LoteDisponible> lotes){
        Set<Long> identificadores = new HashSet<>();

        for (LoteDisponible lote : lotes) {
            Objects.requireNonNull(lote);

            if (!identificadores.add(lote.loteId())) {
                throw new IllegalArgumentException("Se encontró un lote duplicado: " + lote.loteId());
            }

        }
    }
}
