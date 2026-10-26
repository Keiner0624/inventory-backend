package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferenciaService {
    private final AuditoriaService auditoria;
    private final TransferenciaRepository transferenciaRepository;
    private final ProductRepository productoRepository;
    private final AlmacenRepository almacenRepository;
    private final LoteRepository loteRepository;
    private final SalidaInventarioService salidaInventarioService;
    private final InventarioService inventarioService;
    private final EntityManager entityManager;

    @Transactional
    public TransferenciaResponse registrar(String clave, TransferenciaRequest request) {
        String claveNormalizada = normalizarClave(clave);
        if (Objects.equals(request.origenId(), request.destinoId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los almacenes de origen y destino deben ser diferentes");
        }

        var existente = transferenciaRepository.bloquearPorClave(claveNormalizada);
        if (existente.isPresent()) {
            Transferencia transferencia = existente.get();
            if (!Objects.equals(transferencia.getProducto().getId(), request.productoId())
                    || !Objects.equals(transferencia.getOrigen().getId(), request.origenId())
                    || !Objects.equals(transferencia.getDestino().getId(), request.destinoId())
                    || transferencia.getCantidadTotal() != request.cantidad()
                    || !Objects.equals(transferencia.getObservacion(), request.observacion())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La clave pertenece a otra transferencia");
            }
            return convertirResponse(transferencia);
        }

        Almacen destino = almacenRepository.findById(request.destinoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Almacén de destino no encontrado"));
        if (!destino.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El almacén de destino está inactivo");
        }

        // La salida aplica FEFO y bloquea los lotes. Ambas operaciones participan
        // en esta transaccion: cualquier fallo revierte el traslado completo.
        SalidaInventarioResponse salida = salidaInventarioService.registrarSalida(
                new SalidaInventarioRequest(request.productoId(), request.origenId(),
                        request.cantidad(), request.observacion()));

        Transferencia transferencia = new Transferencia(claveNormalizada,
                productoRepository.getReferenceById(request.productoId()),
                almacenRepository.getReferenceById(request.origenId()), destino,
                request.cantidad(), request.observacion());

        for (DetalleSalidaResponse detalle : salida.detalles()) {
            inventarioService.registrarEntrada(new EntradaInventarioRequest(
                    detalle.loteId(), request.destinoId(), detalle.cantidadRetirada(),
                    request.observacion()));
            transferencia.agregarDetalle(loteRepository.getReferenceById(detalle.loteId()),
                    detalle.cantidadRetirada());
        }

        Transferencia guardada = transferenciaRepository.saveAndFlush(transferencia);
        // La fecha la genera SQL Server con el DEFAULT de creada_en.
        entityManager.refresh(guardada);
        auditoria.registrar(TRANSFERENCIA_REGISTRADA, guardada.getId(), request.origenId(),
                new DetalleAuditoria.Traslado(request.productoId(), request.origenId(), request.destinoId(),
                        request.cantidad(), salida.detalles().stream().map(d ->
                            new DetalleAuditoria.LoteTrasladado(d.loteId(), d.cantidadRetirada())).toList()));
        return convertirResponse(guardada);
    }

    private String normalizarClave(String clave) {
        try {
            if (clave == null) {
                throw new IllegalArgumentException();
            }
            String normalizada = clave.trim().toLowerCase(Locale.ROOT);
            if (!UUID.fromString(normalizada).toString().equals(normalizada)) {
                throw new IllegalArgumentException();
            }
            return normalizada;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key debe ser un UUID válido");
        }
    }

    private TransferenciaResponse convertirResponse(Transferencia transferencia) {
        var detalles = transferencia.getDetalles().stream()
                .map(detalle -> new TransferenciaResponse.DetalleTransferenciaResponse(
                        detalle.getLote().getId(), detalle.getLote().getCodigo(),
                        detalle.getCantidad()))
                .toList();
        return new TransferenciaResponse(transferencia.getId(),
                transferencia.getClaveIdempotencia(), transferencia.getProducto().getId(),
                transferencia.getOrigen().getId(), transferencia.getDestino().getId(),
                transferencia.getCantidadTotal(), transferencia.getCreadaEn(), detalles);
    }
}
