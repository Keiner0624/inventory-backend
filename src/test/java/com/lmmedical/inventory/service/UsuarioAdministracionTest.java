package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.usuario.ActualizarRolesRequest;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsuarioAdministracionTest {
    private UsuarioRepository users;
    private RolRepository roles;
    private UsuarioRolRepository assignments;
    private UsuarioService service;
    private Usuario admin;
    private Usuario target;
    private Rol adminRole;

    private Usuario user(long id, String email) {
        var user = new Usuario("Prueba", "Seguridad", email, "{bcrypt}$2a$12$" + "A".repeat(53));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach void setup() {
        users = mock(UsuarioRepository.class);
        roles = mock(RolRepository.class);
        assignments = mock(UsuarioRolRepository.class);
        service = new UsuarioService(users, roles, assignments, mock(PasswordEncoder.class), mock(AuditoriaService.class));
        admin = user(1L, "admin@example.test");
        target = user(2L, "target@example.test");
        adminRole = new Rol("ADMIN", "Administrador");
        ReflectionTestUtils.setField(adminRole, "id", 1L);
        when(roles.bloquearAdmin()).thenReturn(Optional.of(adminRole));
        when(users.findByCorreo(admin.getCorreo())).thenReturn(Optional.of(admin));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(assignments.findRolesByUsuarioId(1L)).thenReturn(List.of(adminRole));
        when(assignments.findRolesByUsuarioId(2L)).thenReturn(List.of(adminRole));
    }

    private void status(int expected, Runnable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    @Test void noPermiteDesactivarLaPropiaCuentaAdministrativa() {
        status(409, () -> service.cambiarEstado(1L, false, admin.getCorreo()));
        assertTrue(admin.isActivo());
    }

    @Test void noPermiteQuitarseElPropioRolAdmin() {
        status(409, () -> service.actualizarRoles(1L, new ActualizarRolesRequest(Set.of("EMPLEADO")), admin.getCorreo()));
        verify(assignments, never()).deleteById(any());
        verify(assignments, never()).save(any());
    }

    @Test void protegeLaCondicionDeUltimoAdministrador() {
        when(assignments.contarAdministradores()).thenReturn(1L);
        status(409, () -> service.actualizarRoles(2L, new ActualizarRolesRequest(Set.of("EMPLEADO")), admin.getCorreo()));
        verify(assignments, never()).deleteById(any());
    }

    @Test void unRolDesconocidoNoModificaAsignaciones() {
        status(400, () -> service.actualizarRoles(2L, new ActualizarRolesRequest(Set.of("SUPERADMIN")), admin.getCorreo()));
        verifyNoInteractions(roles, assignments, users);
    }

    @Test void unRolPermitidoPeroAusenteEnCatalogoNoProduceCambiosParciales() {
        when(assignments.contarAdministradores()).thenReturn(2L);
        when(roles.findByCodigo("EMPLEADO")).thenReturn(Optional.empty());
        status(404, () -> service.actualizarRoles(2L, new ActualizarRolesRequest(Set.of("EMPLEADO")), admin.getCorreo()));
        verify(assignments, never()).deleteById(any());
        verify(assignments, never()).save(any());
    }

    @Test void vuelveAVerificarQueElActorSigueSiendoAdministrador() {
        when(assignments.findRolesByUsuarioId(1L)).thenReturn(List.of(new Rol("EMPLEADO", "Empleado")));
        status(403, () -> service.actualizarRoles(2L, new ActualizarRolesRequest(Set.of("ADMIN")), admin.getCorreo()));
        verify(assignments, never()).save(any());
    }

    @Test void unAdministradorDesactivadoNoCambiaRoles() {
        admin.cambiarEstado(false);
        status(403, () -> service.actualizarRoles(2L, new ActualizarRolesRequest(Set.of("ADMIN")), admin.getCorreo()));
        verify(assignments, never()).save(any());
    }

    @Test void permiteDesactivarOtraCuentaSinCambiarSusRoles() {
        var result = service.cambiarEstado(2L, false, admin.getCorreo());
        assertFalse(result.activo());
        assertFalse(target.isActivo());
        verify(assignments, never()).deleteById(any());
    }
}
