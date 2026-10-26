package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.usuario.*;
import com.lmmedical.inventory.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest crearUsuarioRequest) {
        UsuarioResponse usuario = usuarioService.crearEmpleado(crearUsuarioRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflictoIntegridad() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "No se pudo registrar el usuario por un conflicto de integridad"));
    }

    @GetMapping
    public ResponseEntity<UsuarioPaginaResponse> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioConsultaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<UsuarioConsultaResponse> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request, Authentication authentication) {
        UsuarioConsultaResponse usuario = usuarioService.cambiarEstado(id, request.activo(), authentication.getName());
        return ResponseEntity.ok(usuario);
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UsuarioConsultaResponse> actualizarRoles(@PathVariable Long id, @Valid @RequestBody ActualizarRolesRequest request, Authentication authentication) {
        UsuarioConsultaResponse usuario = usuarioService.actualizarRoles(id, request, authentication.getName());
        return ResponseEntity.ok(usuario);
    }
}
