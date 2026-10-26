package com.lmmedical.inventory.security;

import com.lmmedical.inventory.controller.AuthController;
import com.lmmedical.inventory.dto.auth.LoginRequest;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthLoginFlowTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;
    private JwtDecoder decoder;
    private UsuarioRepository users;
    private UsuarioRolRepository assignments;
    private Usuario user;
    private String password;

    @BeforeEach void setup() {
        PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();
        password = UUID.randomUUID().toString();
        user = new Usuario("Ana", "Pérez", "admin@example.test", encoder.encode(password));
        ReflectionTestUtils.setField(user, "id", 1L);
        users = mock(UsuarioRepository.class);
        assignments = mock(UsuarioRolRepository.class);
        when(users.findByCorreo("admin@example.test")).thenReturn(Optional.of(user));
        when(assignments.findRolesByUsuarioId(1L)).thenReturn(List.of(new Rol("ADMIN", "Administrador")));
        var details = new UsuarioDetailsService(users, assignments);
        var authConfig = new AuthenticationConfig();
        var auth = new AuthService(authConfig.authenticationManager(authConfig.authenticationProvider(details, encoder)));
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        var config = new JwtConfig();
        var key = config.jwtSecretKey(Base64.getEncoder().encodeToString(bytes));
        decoder = config.jwtDecoder(key, "lm-medical-supplies", "lm-medical-api");
        var jwtService = config.jwtService(config.jwtEncoder(key), "lm-medical-supplies", "lm-medical-api");
        var refresh = mock(RefreshTokenService.class);
        when(refresh.emitir(user)).thenReturn(new RefreshTokenService.Emision(UUID.randomUUID(), new RefreshTokenGenerator().generateToken()));
        var sessions = new AuthSessionService(auth, refresh, jwtService, users, details);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(sessions)).build();
    }

    private org.springframework.test.web.servlet.MvcResult login(String email, String pass) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(email, pass)))).andReturn();
    }

    @Test void loginHttpAceptaBcryptYEmiteUnJwtVerificable() throws Exception {
        var response = login("admin@example.test", password).getResponse();
        assertEquals(200, response.getStatus());
        var body = json.readTree(response.getContentAsString());
        Jwt jwt = decoder.decode(body.get("accessToken").asText());
        assertEquals("admin@example.test", jwt.getSubject());
        assertEquals(List.of("ROLE_ADMIN"), jwt.getClaimAsStringList("roles"));
        assertEquals("access", jwt.getClaimAsString("token_type"));
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
        assertEquals(900, body.get("expiresIn").asInt());
        assertEquals("Bearer", body.get("tokenType").asText());
    }

    @Test void loginHttpRechazaPasswordIncorrectoCon401() throws Exception {
        assertEquals(401, login("admin@example.test", UUID.randomUUID().toString()).getResponse().getStatus());
    }

    @Test void loginHttpRechazaUsuarioInexistenteCon401() throws Exception {
        assertEquals(401, login("nadie@example.test", password).getResponse().getStatus());
    }

    @Test void loginHttpRechazaUsuarioInactivoCon401() throws Exception {
        user.cambiarEstado(false);
        assertEquals(401, login("admin@example.test", password).getResponse().getStatus());
    }

    @Test void loginHttpRechazaUsuarioSinRolesCon401() throws Exception {
        when(assignments.findRolesByUsuarioId(1L)).thenReturn(List.of());
        assertEquals(401, login("admin@example.test", password).getResponse().getStatus());
    }
}
