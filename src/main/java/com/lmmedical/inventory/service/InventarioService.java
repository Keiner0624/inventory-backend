package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.entity.*;

import com.lmmedical.inventory.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class InventarioService {
    private final AuditoriaService auditoria;

    private final LoteRepository loteRepository;
    private final AlmacenRepository almacenRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    @Transactional
    public EntradaInventarioResponse registrarEntrada(
            EntradaInventarioRequest request
    ) {

        Lote lote = loteRepository
                .buscarParaMovimiento(request.loteId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lote no encontrado"
                ));

        Almacen almacen = almacenRepository
                .findById(request.almacenId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Almacén no encontrado"
                ));

        if (!lote.getProduct().isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El producto se encuentra inactivo"
            );
        }

        if (!almacen.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El almacén se encuentra inactivo"
            );
        }

        LocalDate hoy = LocalDate.now(
                ZoneId.of("America/Lima")
        );

        if (lote.getFechaVencimiento() != null &&
                lote.getFechaVencimiento().isBefore(hoy)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se permite ingresar un lote vencido"
            );
        }

        Existencia existencia = existenciaRepository
                .findByLote_IdAndAlmacen_Id(
                        lote.getId(),
                        almacen.getId()
                )
                .orElseGet(() -> new Existencia(lote, almacen));

        existencia.ingresar(request.cantidad());

        existenciaRepository.save(existencia);

        MovimientoInventario movimiento =
                new MovimientoInventario(
                        lote,
                        almacen,
                        request.cantidad(),
                        request.observacion()
                );

        MovimientoInventario guardado =
                movimientoRepository.save(movimiento);

        auditoria.registrar(ENTRADA_REGISTRADA, guardado.getId(), almacen.getId(),
                new DetalleAuditoria.Movimiento(lote.getProduct().getId(), lote.getId(), request.cantidad()));
        return new EntradaInventarioResponse(
                guardado.getId(),
                lote.getId(),
                almacen.getId(),
                request.cantidad(),
                existencia.getCantidad()
        );
    }

    @Transactional(readOnly = true)
    public Page<ExistenciaResponse> listarPorAlmacen(
            Long almacenId,
            Pageable pageable
    ) {

        if (!almacenRepository.existsById(almacenId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Almacén no encontrado"
            );
        }

        return existenciaRepository
                .findByAlmacen_Id(almacenId, pageable)
                .map(this::convertirResponse);
    }

    private ExistenciaResponse convertirResponse(
            Existencia existencia
    ) {

        Lote lote = existencia.getLote();
        Almacen almacen = existencia.getAlmacen();

        return new ExistenciaResponse(
                lote.getProduct().getId(),
                lote.getProduct().getNombre(),

                lote.getId(),
                lote.getCodigo(),
                lote.getFechaVencimiento(),

                almacen.getId(),
                almacen.getNombre(),

                existencia.getCantidad()
        );
    }
}