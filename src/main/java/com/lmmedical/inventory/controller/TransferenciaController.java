package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.TransferenciaRequest;
import com.lmmedical.inventory.dto.TransferenciaResponse;
import com.lmmedical.inventory.service.TransferenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transferencias")
@RequiredArgsConstructor
public class TransferenciaController {
    private final TransferenciaService transferenciaService;

    @PostMapping
    public ResponseEntity<TransferenciaResponse> registrar(
            @RequestHeader("Idempotency-Key") String clave,
            @Valid @RequestBody TransferenciaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferenciaService.registrar(clave, request));
    }
}
