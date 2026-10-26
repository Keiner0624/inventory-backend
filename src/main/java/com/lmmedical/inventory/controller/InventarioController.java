package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.service.InventarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventarios")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;

    @PostMapping("/entradas")
    public ResponseEntity<EntradaInventarioResponse> registrarEntrada(
            @Valid @RequestBody EntradaInventarioRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(inventarioService.registrarEntrada(request));
    }

    @GetMapping("/almacenes/{almacenId}/existencias")
    public ResponseEntity<Page<ExistenciaResponse>> listarExistencias(
            @PathVariable Long almacenId,
            @PageableDefault(size = 20, sort = "id")
            Pageable pageable
    ) {

        return ResponseEntity.ok(
                inventarioService.listarPorAlmacen(
                        almacenId,
                        pageable
                )
        );
    }
}