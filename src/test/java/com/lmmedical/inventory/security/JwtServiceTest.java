package com.lmmedical.inventory.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String ISSUER = "lm-medical-supplies";
    private static final String AUDIENCE = "lm-medical-api";

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;
    private SecretKey secretKey;

    @BeforeEach
    void configurar() {

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);

        secretKey = new SecretKeySpec(bytes, "HmacSHA256");

        JwtConfig config = new JwtConfig();

        JwtEncoder encoder = config.jwtEncoder(secretKey);

        jwtDecoder = config.jwtDecoder(
                secretKey,
                ISSUER,
                AUDIENCE
        );

        jwtService = new JwtService(
                encoder,
                ISSUER,
                AUDIENCE
        );
    }

    private Authentication usuarioAutenticado() {

        return new UsernamePasswordAuthenticationToken(
                "empleado@empresa.com",
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_EMPLEADO")
                )
        );
    }

    @Test
    void debeGenerarTokenValido() {

        String token = jwtService.generarToken(
                usuarioAutenticado()
        );

        Jwt jwt = jwtDecoder.decode(token);

        assertEquals(
                "empleado@empresa.com",
                jwt.getSubject()
        );

        assertEquals(
                List.of("ROLE_EMPLEADO"),
                jwt.getClaimAsStringList("roles")
        );

        assertEquals(
                ISSUER,
                jwt.getClaimAsString("iss")
        );

        assertTrue(jwt.getAudience().contains(AUDIENCE));
        assertNotNull(jwt.getExpiresAt());
    }

    @Test
    void debeRechazarUsuarioNoAutenticado() {

        Authentication authentication =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "empleado@empresa.com",
                        "password"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtService.generarToken(authentication)
        );
    }

    @Test
    void debeRechazarTokenConFirmaIncorrecta() {

        String token = jwtService.generarToken(
                usuarioAutenticado()
        );

        byte[] otraClave = new byte[32];
        new SecureRandom().nextBytes(otraClave);
        JwtDecoder decoderConOtraClave = new JwtConfig().jwtDecoder(
                new SecretKeySpec(otraClave, "HmacSHA256"), ISSUER, AUDIENCE);

        assertThrows(JwtException.class, () -> decoderConOtraClave.decode(token));
    }

    @Test
    void debeRechazarTokenConAudienciaIncorrecta() {

        String token = jwtService.generarToken(
                usuarioAutenticado()
        );

        JwtDecoder decoderConOtraAudiencia = new JwtConfig().jwtDecoder(secretKey, ISSUER, "otra-api");
        assertThrows(JwtException.class, () -> decoderConOtraAudiencia.decode(token));
    }
}