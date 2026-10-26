package com.lmmedical.inventory.security;

import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RolesActualesJwtConverterTest {
    private UsuarioRepository users;
    private UsuarioRolRepository roles;
    private SesionRefreshRepository sessions;
    private RolesActualesJwtConverter converter;
    private Usuario user;
    private final UUID family = UUID.randomUUID();

    @BeforeEach void setup() {
        users = mock(UsuarioRepository.class);
        roles = mock(UsuarioRolRepository.class);
        sessions = mock(SesionRefreshRepository.class);
        user = new Usuario("Ana", "Perez", "persona@example.test", "{bcrypt}$2a$12$" + "A".repeat(53));
        ReflectionTestUtils.setField(user, "id", 21L);
        when(users.findByCorreo(user.getCorreo())).thenReturn(Optional.of(user));
        when(roles.findRolesByUsuarioId(21L)).thenReturn(List.of(new Rol("EMPLEADO", "Empleado")));
        when(sessions.existsByFamiliaIdAndUsuario_IdAndFechaRevocacionIsNullAndFechaExpiracionAfter(eq(family), eq(21L), any()))
                .thenReturn(true);
        converter = new RolesActualesJwtConverter(users, roles, sessions);
    }

    private Jwt jwt(String sid) {
        var builder = Jwt.withTokenValue("fixture").header("alg", "HS256").subject(user.getCorreo())
                .claim("roles", List.of("ROLE_ADMIN"));
        if (sid != null) builder.claim("sid", sid);
        return builder.build();
    }

    @Test void usaRolesActualesEIgnoraLaElevacionDePrivilegiosDelClaim() {
        var permissions = converter.convert(jwt(family.toString())).stream().map(GrantedAuthority::getAuthority).toList();
        assertEquals(List.of("ROLE_EMPLEADO"), permissions);
        verify(sessions).existsByFamiliaIdAndUsuario_IdAndFechaRevocacionIsNullAndFechaExpiracionAfter(eq(family), eq(21L), any());
    }

    @Test void releeRolesEnCadaPeticion() {
        Jwt token = jwt(family.toString());
        converter.convert(token);
        when(roles.findRolesByUsuarioId(21L)).thenReturn(List.of(new Rol("ADMIN", "Administrador")));
        assertEquals(List.of("ROLE_ADMIN"), converter.convert(token).stream().map(GrantedAuthority::getAuthority).toList());
        verify(roles, times(2)).findRolesByUsuarioId(21L);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "no-es-uuid"})
    void rechazaSidAusenteOInvalidoConErrorDeAutenticacion(String sid) {
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(sid)));
        verifyNoInteractions(sessions);
    }

    @Test void rechazaUsuarioInexistente() {
        when(users.findByCorreo(user.getCorreo())).thenReturn(Optional.empty());
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(family.toString())));
        verifyNoInteractions(roles, sessions);
    }

    @Test void rechazaUsuarioDesactivado() {
        user.cambiarEstado(false);
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(family.toString())));
        verifyNoInteractions(roles, sessions);
    }

    @Test void rechazaUsuarioSinRoles() {
        when(roles.findRolesByUsuarioId(21L)).thenReturn(List.of());
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(family.toString())));
        verifyNoInteractions(sessions);
    }

    @Test void rechazaFamiliaRevocadaExpiradaODeOtroUsuario() {
        when(sessions.existsByFamiliaIdAndUsuario_IdAndFechaRevocacionIsNullAndFechaExpiracionAfter(eq(family), eq(21L), any()))
                .thenReturn(false);
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(family.toString())));
    }
}
