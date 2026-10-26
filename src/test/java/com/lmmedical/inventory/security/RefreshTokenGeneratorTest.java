package com.lmmedical.inventory.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator generator =
            new RefreshTokenGenerator();

    @Test
    void debeGenerarTokensDiferentes() {

        String token1 = generator.generateToken();
        String token2 = generator.generateToken();

        assertNotEquals(token1, token2);
    }

    @Test
    void debeGenerarTokensDe43Caracteres() {

        String token = generator.generateToken();

        assertEquals(43, token.length());
    }

    @Test
    void debeGenerarElMismoHashParaElMismoToken() {

        String token = generator.generateToken();

        String hash1 = generator.calcularHash(token);
        String hash2 = generator.calcularHash(token);

        assertEquals(hash1, hash2);
    }

    @Test
    void debeGenerarHashesDiferentesParaTokensDiferentes() {

        String token1 = generator.generateToken();
        String token2 = generator.generateToken();

        assertNotEquals(
                generator.calcularHash(token1),
                generator.calcularHash(token2)
        );
    }

    @Test
    void noDebeAceptarTokensVacios() {

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.calcularHash("")
        );
    }
}
