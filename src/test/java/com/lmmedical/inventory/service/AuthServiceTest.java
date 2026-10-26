package com.lmmedical.inventory.service;

import com.lmmedical.inventory.security.AuthenticationConfig;
import com.lmmedical.inventory.security.PasswordEncoderConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void configurar() {

        PasswordEncoder encoder =
                new PasswordEncoderConfig().passwordEncoder();

        UserDetails usuario = User.withUsername("empleado@empresa.com")
                .password(encoder.encode("ClaveSegura123!"))
                .roles("EMPLEADO")
                .build();

        UserDetailsService detailsService = correo -> {
            if (!usuario.getUsername().equals(correo)) {
                throw new UsernameNotFoundException("Usuario no encontrado");
            }

            return usuario;
        };

        AuthenticationConfig config = new AuthenticationConfig();

        var provider = config.authenticationProvider(
                detailsService,
                encoder
        );

        var manager = config.authenticationManager(provider);

        authService = new AuthService(manager);
    }

    @Test
    void debeAutenticarConCredencialesCorrectas() {

        Authentication resultado = authService.autenticar(
                "empleado@empresa.com",
                "ClaveSegura123!"
        );

        assertTrue(resultado.isAuthenticated());

        assertEquals(
                "empleado@empresa.com",
                resultado.getName()
        );

        assertTrue(resultado.getAuthorities().stream()
                .anyMatch(rol ->
                        rol.getAuthority().equals("ROLE_EMPLEADO")
                ));
    }

    @Test
    void debeRechazarPasswordIncorrecto() {

        assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(
                        "empleado@empresa.com",
                        "PasswordIncorrecto"
                )
        );
    }

    @Test
    void debeRechazarUsuarioInexistente() {

        assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(
                        "desconocido@empresa.com",
                        "ClaveSegura123!"
                )
        );
    }

    @Test
    void debeNormalizarCorreo() {

        Authentication resultado = authService.autenticar(
                "  EMPLEADO@EMPRESA.COM  ",
                "ClaveSegura123!"
        );

        assertTrue(resultado.isAuthenticated());
    }

    @Test
    void debeRechazarCredencialesVacias() {

        assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar("", "")
        );
    }
}