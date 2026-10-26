package com.lmmedical.inventory.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.*;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {
    private String key(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test void jwtDesactivadoPorDefectoYNoReconoceEnableMalEscrito() {
        new ApplicationContextRunner().withUserConfiguration(JwtConfig.class).run(ctx ->
                assertEquals(0, ctx.getBeansOfType(JwtService.class).size()));
        new ApplicationContextRunner().withUserConfiguration(JwtConfig.class)
                .withPropertyValues("app.jwt.enable=true").run(ctx ->
                        assertEquals(0, ctx.getBeansOfType(JwtService.class).size()));
    }

    @Test void enabledRegistraUnaUnicaConfiguracionJwt() {
        new ApplicationContextRunner().withUserConfiguration(JwtConfig.class)
                .withPropertyValues("app.jwt.enabled=true", "app.jwt.enable=true",
                        "app.jwt.issuer=lm-medical-supplies", "app.jwt.audience=lm-medical-api",
                        "app.jwt.secret-base64=" + key(32)).run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(1, ctx.getBeansOfType(JwtEncoder.class).size());
                    assertEquals(1, ctx.getBeansOfType(JwtDecoder.class).size());
                    assertEquals(1, ctx.getBeansOfType(JwtService.class).size());
                });
    }

    @Test void rechazaClaveBase64Corta() {
        assertThrows(IllegalStateException.class, () -> new JwtConfig().jwtSecretKey(key(25)));
    }

    @Test void rechazaClaveSinBase64() {
        assertThrows(IllegalStateException.class, () -> new JwtConfig().jwtSecretKey("%%%"));
    }
}
