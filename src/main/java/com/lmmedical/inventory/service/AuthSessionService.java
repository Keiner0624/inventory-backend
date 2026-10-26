package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auth.AuthTokenResponse;
import com.lmmedical.inventory.dto.auth.LoginRequest;
import com.lmmedical.inventory.dto.auth.RefreshTokenRequest;
import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.security.JwtService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(
        prefix = "app.jwt",
        name = "enabled",
        havingValue = "true"
)
public class AuthSessionService {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final UserDetailsService userDetailsService;

    public AuthSessionService(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            JwtService jwtService,
            UsuarioRepository usuarioRepository,
            UserDetailsService userDetailsService
    ) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        if (request == null) throw new BadCredentialsException("Credenciales inválidas");

        Authentication authentication = authService.autenticar(
                request.correo(),
                request.password()
        );

        Usuario usuario = usuarioRepository
                .findByCorreo(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException(
                        "Credenciales inválidas"
                ));

        RefreshTokenService.Emision emision =
                refreshTokenService.emitir(usuario);

        String accessToken = jwtService.generarToken(
                authentication,
                emision.familiaId()
        );

        return new AuthTokenResponse(
                accessToken,
                emision.refreshToken(),
                "Bearer",
                JwtService.EXPIRATION_SEGUNDOS
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthTokenResponse renovar(RefreshTokenRequest request) {
        if (request == null) throw new BadCredentialsException("Sesión inválida");

        RefreshTokenService.Rotacion rotacion =
                refreshTokenService.renovar(
                        request.refreshToken()
                );

        UserDetails usuario = userDetailsService
                .loadUserByUsername(
                        rotacion.usuario().getCorreo()
                );

        if (!usuario.isEnabled() || usuario.getAuthorities().isEmpty()) {
            throw new BadCredentialsException(
                    "Sesión inválida"
            );
        }

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        usuario,
                        null,
                        usuario.getAuthorities()
                );

        String accessToken = jwtService.generarToken(
                authentication,
                rotacion.familiaId()
        );

        return new AuthTokenResponse(
                accessToken,
                rotacion.refreshToken(),
                "Bearer",
                JwtService.EXPIRATION_SEGUNDOS
        );
    }

    @Transactional
    public void logout(RefreshTokenRequest request){
        refreshTokenService.cerrarSesion(request.refreshToken());
    }
}