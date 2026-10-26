package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import com.lmmedical.inventory.entity.EventoAuditoria;
import com.lmmedical.inventory.enums.AccionAuditoria;
import com.lmmedical.inventory.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class AuditoriaService {
    private final UsuarioRepository usuarios;
    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;

    /** Obligatoria y síncrona: nunca inicia una transacción independiente ni oculta errores. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AccionAuditoria accion, Long entidadId, Long almacenId, DetalleAuditoria detalles) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere un usuario autenticado para auditar");
        var usuario = usuarios.findByCorreo(auth.getName()).filter(u -> u.isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
        String json = detalles == null ? null : jsonMapper.writeValueAsString(detalles);
        entityManager.persist(new EventoAuditoria(usuario.getId(), usuario.getCorreo(), accion,
                entidadId, almacenId, json));
    }
}
