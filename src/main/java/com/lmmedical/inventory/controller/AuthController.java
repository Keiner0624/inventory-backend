package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.auth.AuthTokenResponse;
import com.lmmedical.inventory.dto.auth.LoginRequest;
import com.lmmedical.inventory.dto.auth.RefreshTokenRequest;
import com.lmmedical.inventory.service.AuthSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = "app.jwt", name = "enabled", havingValue = "true")
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthSessionService  authSessionService;

    public AuthController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authSessionService.login(loginRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authSessionService.renovar(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authSessionService.logout(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> manejarErrorAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).body(Map.of("error", "Credenciales invalidas"));
    }
}
