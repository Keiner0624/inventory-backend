package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.SalidaInventarioRequest;
import com.lmmedical.inventory.dto.SalidaInventarioResponse;
import com.lmmedical.inventory.service.SalidaIdempotenteService;
import com.lmmedical.inventory.service.SalidaInventarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventarios/salidas")
@RequiredArgsConstructor
public class SalidaInventarioController {

    private final SalidaIdempotenteService salidaIdempotenteService;

    @PostMapping
    public ResponseEntity<SalidaInventarioResponse> registrar(
            @RequestHeader("Idempotency-key") String clave,
            @Valid @RequestBody SalidaInventarioRequest request
    ) {

        SalidaInventarioResponse respuesta = salidaIdempotenteService.registrar(clave, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }
}