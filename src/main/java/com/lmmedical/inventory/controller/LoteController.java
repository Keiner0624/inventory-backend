package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.LoteRequest;
import com.lmmedical.inventory.dto.LoteResponse;

import com.lmmedical.inventory.service.LoteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lotes")
@RequiredArgsConstructor
public class LoteController {

    private final LoteService loteService;

    @PostMapping
    public ResponseEntity<LoteResponse> registrar(
            @Valid @RequestBody LoteRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(loteService.registrar(request));
    }
}