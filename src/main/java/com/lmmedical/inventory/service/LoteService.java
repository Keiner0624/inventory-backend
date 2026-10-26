package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;

import com.lmmedical.inventory.dto.LoteRequest;
import com.lmmedical.inventory.dto.LoteResponse;

import com.lmmedical.inventory.entity.Lote;
import com.lmmedical.inventory.entity.Product;

import com.lmmedical.inventory.repository.LoteRepository;
import com.lmmedical.inventory.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoteService {
    private final AuditoriaService auditoria;

    private final LoteRepository loteRepository;
    private final ProductRepository productoRepository;

    @Transactional
    public LoteResponse registrar(LoteRequest request) {

        Product producto = productoRepository
                .findById(request.productoId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Producto no encontrado"
                ));

        if (!producto.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pueden registrar lotes de productos inactivos"
            );
        }

        String codigo = request.codigo()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (loteRepository.existsByProduct_IdAndCodigo(
                producto.getId(),
                codigo
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El lote ya existe para este producto"
            );
        }

        Lote lote = new Lote(
                producto,
                codigo,
                request.fechaVencimiento()
        );

        Lote guardado = loteRepository.save(lote);
        auditoria.registrar(LOTE_CREADO, guardado.getId(), null,
                new DetalleAuditoria.LoteCreado(producto.getId(), guardado.getFechaVencimiento()));

        return new LoteResponse(
                guardado.getId(),
                guardado.getProduct().getId(),
                guardado.getCodigo(),
                guardado.getFechaVencimiento()
        );
    }
}