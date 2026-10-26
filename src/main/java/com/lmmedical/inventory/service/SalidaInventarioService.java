package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.*;

import com.lmmedical.inventory.entity.*;

import com.lmmedical.inventory.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalidaInventarioService {

    private final ProductRepository productoRepository;
    private final AlmacenRepository almacenRepository;
    private final LoteRepository loteRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    private record Candidato(
            Lote lote,
            Existencia existencia
    ) {}

    @Transactional
    public SalidaInventarioResponse registrarSalida(
            SalidaInventarioRequest request
    ) {

        Product producto = productoRepository
                .findById(request.productoId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Producto no encontrado"
                ));

        if (!producto.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El producto está inactivo"
            );
        }

        Almacen almacen = almacenRepository
                .findById(request.almacenId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Almacén no encontrado"
                ));

        if (!almacen.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El almacén está inactivo"
            );
        }

        LocalDate hoy = LocalDate.now(
                ZoneId.of("America/Lima")
        );

        List<Lote> lotes = loteRepository
                .findByProduct_IdOrderByIdAsc(producto.getId());

        List<Candidato> candidatos = new ArrayList<>();

        for (Lote referencia : lotes) {

            Lote lote = loteRepository
                    .buscarParaMovimiento(referencia.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Un lote dejó de estar disponible"
                    ));

            LocalDate vencimiento = lote.getFechaVencimiento();

            if (vencimiento != null && vencimiento.isBefore(hoy)) {
                continue;
            }

            existenciaRepository
                    .findByLote_IdAndAlmacen_Id(
                            lote.getId(),
                            almacen.getId()
                    )
                    .filter(existencia -> existencia.getCantidad() > 0)
                    .ifPresent(existencia -> candidatos.add(
                            new Candidato(lote, existencia)
                    ));
        }

        candidatos.sort(
                Comparator.comparing(
                        (Candidato candidato) ->
                                candidato.lote().getFechaVencimiento(),

                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                ).thenComparing(
                        candidato -> candidato.lote().getId()
                )
        );

        long stockDisponible = candidatos.stream()
                .mapToLong(
                        candidato -> candidato.existencia().getCantidad()
                )
                .sum();

        if (stockDisponible < request.cantidad()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock insuficiente. Disponible: " + stockDisponible
            );
        }

        int pendiente = request.cantidad();

        List<DetalleSalidaResponse> detalles = new ArrayList<>();

        for (Candidato candidato : candidatos) {

            if (pendiente == 0) {
                break;
            }

            Existencia existencia = candidato.existencia();

            int retirar = Math.min(
                    pendiente,
                    existencia.getCantidad()
            );

            existencia.retirar(retirar);

            MovimientoInventario movimiento =
                    MovimientoInventario.salida(
                            candidato.lote(),
                            almacen,
                            retirar,
                            request.observacion()
                    );

            movimientoRepository.save(movimiento);

            detalles.add(
                    new DetalleSalidaResponse(
                            candidato.lote().getId(),
                            candidato.lote().getCodigo(),
                            retirar,
                            existencia.getCantidad()
                    )
            );

            pendiente -= retirar;
        }

        return new SalidaInventarioResponse(
                producto.getId(),
                almacen.getId(),
                request.cantidad(),
                List.copyOf(detalles)
        );
    }
}