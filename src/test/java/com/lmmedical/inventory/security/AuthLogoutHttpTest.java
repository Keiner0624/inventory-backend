package com.lmmedical.inventory.security;

import com.lmmedical.inventory.controller.AuthController;
import com.lmmedical.inventory.dto.auth.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.exception.GlobalExceptionHandler;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AuthLogoutHttpTest.Config.class)
@WebAppConfiguration
@ActiveProfiles("prod")
@TestPropertySource(properties="app.jwt.enabled=true")
class AuthLogoutHttpTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AuthController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean UsuarioRepository users() { return mock(UsuarioRepository.class); }
        @Bean UsuarioRolRepository roles() { return mock(UsuarioRolRepository.class); }
        @Bean SesionRefreshRepository sessions() { return mock(SesionRefreshRepository.class); }
        @Bean AuthService auth() { return mock(AuthService.class); }
        @Bean RefreshTokenGenerator generator() { return new RefreshTokenGenerator(); }
        @Bean RefreshTokenService refresh(SesionRefreshRepository sessions, RefreshTokenGenerator generator, UsuarioRepository users) {
            return new RefreshTokenService(sessions, generator, users, mock(jakarta.persistence.EntityManager.class));
        }
        @Bean SecretKey key() {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            return new SecretKeySpec(bytes, "HmacSHA256");
        }
        @Bean JwtDecoder decoder(SecretKey key) { return new JwtConfig().jwtDecoder(key, "test-issuer", "test-audience"); }
        @Bean JwtService jwt(SecretKey key) {
            return new JwtService(new JwtConfig().jwtEncoder(key), "test-issuer", "test-audience");
        }
        @Bean UsuarioDetailsService details(UsuarioRepository users, UsuarioRolRepository roles) {
            return new UsuarioDetailsService(users, roles);
        }
        @Bean AuthSessionService authSessions(AuthService auth, RefreshTokenService refresh, JwtService jwt,
                                             UsuarioRepository users, UsuarioDetailsService details) {
            return new AuthSessionService(auth, refresh, jwt, users, details);
        }
        @Bean RolesActualesJwtConverter converter(UsuarioRepository users, UsuarioRolRepository roles, SesionRefreshRepository sessions) {
            return new RolesActualesJwtConverter(users, roles, sessions);
        }
        @Bean Probe probe() { return new Probe(); }
    }

    @RestController
    static class Probe {
        @GetMapping("/api/logout-test") String get() { return "ok"; }
        @PostMapping("/api/logout-test") String post() { return "ok"; }
    }

    @Autowired WebApplicationContext context;
    @Autowired UsuarioRepository users;
    @Autowired UsuarioRolRepository roles;
    @Autowired SesionRefreshRepository sessions;
    @Autowired AuthService auth;
    @Autowired RefreshTokenGenerator generator;
    @Autowired SecretKey key;
    private MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, SesionRefresh> stored = new HashMap<>();
    private String password;
    private Usuario user;

    @BeforeEach void setup() {
        reset(users, roles, sessions, auth);
        stored.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        password = UUID.randomUUID().toString();
        user = new Usuario("Ana", "Pérez", "persona@example.test", "{bcrypt}" + "$2a$12$" + "A".repeat(53));
        ReflectionTestUtils.setField(user, "id", 1L);
        when(users.findByCorreo(user.getCorreo())).thenReturn(Optional.of(user));
        when(users.bloquearPorId(1L)).thenReturn(Optional.of(user));
        when(roles.findRolesByUsuarioId(1L)).thenReturn(List.of(new Rol("EMPLEADO", "Empleado")));
        when(auth.autenticar(user.getCorreo(), password)).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(user.getCorreo(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_EMPLEADO"))));
        when(sessions.saveAndFlush(any(SesionRefresh.class))).thenAnswer(call -> {
            SesionRefresh session = call.getArgument(0);
            ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
            stored.put(session.getTokenHash(), session);
            return session;
        });
        when(sessions.findByTokenHash(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(sessions.bloquearPorHash(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(sessions.findByFamiliaIdAndUsuario_Id(any(UUID.class), eq(1L))).thenAnswer(call ->
                stored.values().stream().filter(s -> s.getFamiliaId().equals(call.getArgument(0))).toList());
        when(sessions.existsByFamiliaIdAndUsuario_IdAndFechaRevocacionIsNullAndFechaExpiracionAfter(
                any(UUID.class), eq(1L), any(OffsetDateTime.class))).thenAnswer(call ->
                stored.values().stream().anyMatch(s -> s.getFamiliaId().equals(call.getArgument(0))
                        && s.estaVigente(call.getArgument(2))));
    }

    private AuthTokenResponse login() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest(user.getCorreo(), password))))
                .andExpect(status().isOk()).andReturn();
        return json.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
    }

    private String body(String token) { return json.writeValueAsString(new RefreshTokenRequest(token)); }

    @Test void empleadoCierraSinAccessTokenYRevocaElAccesoYLaRenovacion() throws Exception {
        var tokens = login();
        mvc.perform(get("/api/logout-test").header("Authorization", "Bearer " + tokens.accessToken())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").contentType("application/json").content(body(tokens.refreshToken())))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertNotNull(stored.get(generator.calcularHash(tokens.refreshToken())).getFechaRevocacion());
        mvc.perform(get("/api/logout-test").header("Authorization", "Bearer " + tokens.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content(body(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test void cierraConElRefreshNuevoDespuesDeRotar() throws Exception {
        var original = login();
        var result = mvc.perform(post("/api/auth/refresh").contentType("application/json").content(body(original.refreshToken())))
                .andExpect(status().isOk()).andReturn();
        var renewed = json.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        assertNotEquals(original.refreshToken(), renewed.refreshToken());
        mvc.perform(post("/api/auth/logout").contentType("application/json").content(body(renewed.refreshToken())))
                .andExpect(status().isNoContent());
        assertTrue(stored.values().stream().allMatch(s -> s.getFechaRevocacion() != null));
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content(body(renewed.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test void logoutNoDependeDelBearerHeredadoDePostman() throws Exception {
        var tokens = login();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token-vencido-o-invalido")
                        .contentType("application/json").content(body(tokens.refreshToken())))
                .andExpect(status().isNoContent());
        assertNotNull(stored.get(generator.calcularHash(tokens.refreshToken())).getFechaRevocacion());
    }

    @Test void accessTokenVencidoNoImpideCerrarConUnRefreshVigente() throws Exception {
        var tokens = login();
        var now = java.time.Instant.now();
        var claims = JwtClaimsSet.builder().issuer("test-issuer").subject(user.getCorreo())
                .audience(List.of("test-audience")).issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600))
                .claim("token_type", "access").build();
        String expired = new JwtConfig().jwtEncoder(key).encode(JwtEncoderParameters.from(
                JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/logout-test").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + expired)
                        .contentType("application/json").content(body(tokens.refreshToken())))
                .andExpect(status().isNoContent());
        assertNotNull(stored.get(generator.calcularHash(tokens.refreshToken())).getFechaRevocacion());
    }

    @Test void logoutEsIdempotenteYSoloCierraLaFamiliaIndicada() throws Exception {
        var first = login();
        var second = login();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/logout").contentType("application/json").content(body(first.refreshToken())))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/logout-test").header("Authorization", "Bearer " + second.accessToken())).andExpect(status().isOk());
    }

    @Test void logoutValidaElCuerpo() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType("application/json").content(body("corto")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").contentType("application/json").content(body("")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sessions);
    }

    @Test void conservaLaSeguridadDeLasDemasRutas() throws Exception {
        mvc.perform(get("/api/logout-test")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/logout-test").header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
        var tokens = login();
        mvc.perform(post("/api/logout-test").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/logout")).andExpect(status().isUnauthorized());
    }
}
