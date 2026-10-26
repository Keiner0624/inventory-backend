package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.auditoria.*;
import com.lmmedical.inventory.enums.*;
import com.lmmedical.inventory.service.AuditoriaConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {
    private final AuditoriaConsultaService auditoria;

    @GetMapping
    public AuditoriaPaginaResponse listar(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(required=false) Long usuarioId,
            @RequestParam(required=false) AccionAuditoria accion, @RequestParam(required=false) EntidadAuditoria entidad,
            @RequestParam(required=false) Long entidadId, @RequestParam(required=false) Long almacenId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return auditoria.listar(page, size, usuarioId, accion, entidad, entidadId, almacenId, desde, hasta);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public org.springframework.http.ResponseEntity<org.springframework.http.ProblemDetail> filtroMalFormado() {
        var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.BAD_REQUEST, "El tipo de un filtro de auditoría es inválido");
        problem.setTitle("Error de validación");
        return org.springframework.http.ResponseEntity.badRequest().body(problem);
    }

    @GetMapping("/{id}")
    public EventoAuditoriaResponse buscar(@PathVariable Long id) { return auditoria.buscar(id); }
}
