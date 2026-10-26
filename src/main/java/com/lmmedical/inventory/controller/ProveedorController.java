package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.service.ProveedorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @PostMapping
    public ResponseEntity<ProveedorResponse> registrar(
            @Valid @RequestBody ProveedorRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proveedorService.registrar(request));
    }

    @GetMapping
    public ResponseEntity<Page<ProveedorResponse>> listar(
            @PageableDefault(size = 20, sort = "razonSocial")
            Pageable pageable
    ) {

        return ResponseEntity.ok(
                proveedorService.listar(pageable)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProveedorResponse> buscarPorId(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                proveedorService.buscarPorId(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProveedorResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorUpdateRequest request
    ) {

        return ResponseEntity.ok(
                proveedorService.actualizar(id, request)
        );
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivar(
            @PathVariable Long id
    ) {

        proveedorService.desactivar(id);

        return ResponseEntity.noContent().build();
    }
}