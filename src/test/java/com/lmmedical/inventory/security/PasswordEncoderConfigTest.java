package com.lmmedical.inventory.security;

import com.lmmedical.inventory.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder =
            new PasswordEncoderConfig().passwordEncoder();

    @Test
    void debeGenerarHashCompatibleConUsuario() {

        String password = "ClaveDePrueba123!";

        String hash = encoder.encode(password);

        assertTrue(
                hash.startsWith("{bcrypt}$2"),
                "ERROR 1: El hash no tiene el formato esperado"
        );

        assertTrue(
                encoder.matches(password, hash),
                "ERROR 2: BCrypt no reconoce la contraseña original"
        );

        Usuario usuario = new Usuario(
                "Juan",
                "Perez",
                "juan@example.com",
                hash
        );

        assertTrue(
                encoder.matches(password, usuario.getPasswordHash()),
                "ERROR 3: La contraseña no coincide con el hash del usuario"
        );

        assertFalse(
                encoder.matches("ContraseñaIncorrecta", usuario.getPasswordHash()),
                "ERROR 4: Se aceptó una contraseña incorrecta"
        );
    }
}