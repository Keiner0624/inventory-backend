package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.AlmacenRequest;
import com.lmmedical.inventory.dto.AlmacenResponse;
import com.lmmedical.inventory.service.AlmacenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/almacenes")
@RequiredArgsConstructor
public class AlmacenController {
    private final AlmacenService almacenService;

    @PostMapping
    public ResponseEntity<AlmacenResponse> registrar(@Valid @RequestBody AlmacenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(almacenService.registrar(request));
    }

    @GetMapping
    public ResponseEntity<Page<AlmacenResponse>> listar(@PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return ResponseEntity.ok(almacenService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlmacenResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(almacenService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlmacenResponse> actualizar(@PathVariable Long id, @Valid @RequestBody AlmacenRequest request) {
        return ResponseEntity.ok(almacenService.actualizar(id, request));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        almacenService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
