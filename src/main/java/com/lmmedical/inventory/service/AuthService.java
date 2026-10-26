package com.lmmedical.inventory.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;

    public AuthService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public Authentication autenticar(String correo, String password){
        if (correo == null || correo.isBlank() || password == null || password.isBlank()){
            throw new BadCredentialsException("Credenciales invalidas");
        }

        String correoNormalizado = correo.strip().toLowerCase(Locale.ROOT);

        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(correoNormalizado, password));
    }
}
