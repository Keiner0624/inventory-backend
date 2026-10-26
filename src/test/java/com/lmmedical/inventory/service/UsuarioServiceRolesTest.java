package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.usuario.ActualizarRolesRequest;
import com.lmmedical.inventory.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceRolesTest {
    @Test void rolesAusentesVaciosDesconocidosONulosSeRechazanSinEscribir() {
        var users = mock(UsuarioRepository.class);
        var roles = mock(RolRepository.class);
        var assignments = mock(UsuarioRolRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var service = new UsuarioService(users, roles, assignments, encoder);
        Set<String> nullElement = new HashSet<>();
        nullElement.add(null);
        var cases = Arrays.asList(null, new ActualizarRolesRequest(null), new ActualizarRolesRequest(Set.of()),
                new ActualizarRolesRequest(Set.of("NO_EXISTE")), new ActualizarRolesRequest(nullElement));
        for (var request : cases) {
            var error = assertThrows(ResponseStatusException.class, () -> service.actualizarRoles(1L, request, "admin@example.test"));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(users, roles, assignments, encoder);
    }
}
