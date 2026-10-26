package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auth.*;
import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.security.SecureRandom;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionServiceTest {
    private AuthService auth;
    private RefreshTokenService refresh;
    private UsuarioRepository users;
    private UserDetailsService details;
    private AuthSessionService service;
    private Usuario user;
    private JwtDecoder decoder;
    private final UUID family = UUID.randomUUID();
    private String password;
    private String refreshToken;

    @BeforeEach void setup() {
        auth = mock(AuthService.class);
        refresh = mock(RefreshTokenService.class);
        users = mock(UsuarioRepository.class);
        details = mock(UserDetailsService.class);
        password = UUID.randomUUID().toString();
        String hash = new PasswordEncoderConfig().passwordEncoder().encode(password);
        user = new Usuario("Ana", "Pérez", "persona@example.test", hash);
        refreshToken = new RefreshTokenGenerator().generateToken();
        var principal = new UsernamePasswordAuthenticationToken(user.getCorreo(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(auth.autenticar(user.getCorreo(), password)).thenReturn(principal);
        when(users.findByCorreo(user.getCorreo())).thenReturn(Optional.of(user));
        when(refresh.emitir(user)).thenReturn(new RefreshTokenService.Emision(family, refreshToken));
        when(refresh.renovar(refreshToken)).thenReturn(new RefreshTokenService.Rotacion(user, family, refreshToken));
        when(details.loadUserByUsername(user.getCorreo())).thenReturn(User.withUsername(user.getCorreo())
                .password(hash).roles("EMPLEADO").build());
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        var config = new JwtConfig();
        var key = config.jwtSecretKey(Base64.getEncoder().encodeToString(bytes));
        var jwt = config.jwtService(config.jwtEncoder(key), "lm-medical-supplies", "lm-medical-api");
        decoder = config.jwtDecoder(key, "lm-medical-supplies", "lm-medical-api");
        service = new AuthSessionService(auth, refresh, jwt, users, details);
    }

    @Test void loginFirmaLaFamiliaRealYEntregaLaRespuestaCorrecta() {
        var response = service.login(new LoginRequest(user.getCorreo(), password));
        var jwt = decoder.decode(response.accessToken());
        assertEquals(family.toString(), jwt.getClaimAsString("sid"));
        assertEquals(user.getCorreo(), jwt.getSubject());
        assertEquals(List.of("ROLE_ADMIN"), jwt.getClaimAsStringList("roles"));
        assertEquals(refreshToken, response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(JwtService.EXPIRATION_SEGUNDOS, response.expiresIn());
    }

    @Test void renovarMantieneLaFamiliaYRecargaLosPermisosActuales() {
        var response = service.renovar(new RefreshTokenRequest(refreshToken));
        var jwt = decoder.decode(response.accessToken());
        assertEquals(family.toString(), jwt.getClaimAsString("sid"));
        assertEquals(List.of("ROLE_EMPLEADO"), jwt.getClaimAsStringList("roles"));
        verify(details).loadUserByUsername(user.getCorreo());
    }

    @Test void credencialesRechazadasNoEmitenRefresh() {
        when(auth.autenticar(user.getCorreo(), password)).thenThrow(new BadCredentialsException("Credenciales inválidas"));
        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest(user.getCorreo(), password)));
        verifyNoInteractions(refresh, users);
    }

    @Test void noEmiteSiLaCuentaYaNoExiste() {
        when(users.findByCorreo(user.getCorreo())).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest(user.getCorreo(), password)));
        verifyNoInteractions(refresh);
    }

    @Test void renovarRechazaUnaCuentaInactiva() {
        when(details.loadUserByUsername(user.getCorreo())).thenReturn(User.withUsername(user.getCorreo())
                .password(user.getPasswordHash()).roles("EMPLEADO").disabled(true).build());
        assertThrows(BadCredentialsException.class, () -> service.renovar(new RefreshTokenRequest(refreshToken)));
    }

    @Test void renovarRechazaUnaCuentaSinPermisos() {
        when(details.loadUserByUsername(user.getCorreo())).thenReturn(User.withUsername(user.getCorreo())
                .password(user.getPasswordHash()).authorities(List.of()).build());
        assertThrows(BadCredentialsException.class, () -> service.renovar(new RefreshTokenRequest(refreshToken)));
    }

    @Test void peticionesNulasSeRechazanAntesDeConsultar() {
        assertThrows(BadCredentialsException.class, () -> service.login(null));
        assertThrows(BadCredentialsException.class, () -> service.renovar(null));
        verifyNoInteractions(auth, refresh, users, details);
    }
}
