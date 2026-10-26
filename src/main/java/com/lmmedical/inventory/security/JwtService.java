package com.lmmedical.inventory.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class JwtService {
    public static final long EXPIRATION_SEGUNDOS = 900;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;

    public JwtService(JwtEncoder jwtEncoder, String issuer, String audience) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
    }

    // Mantiene el login de acceso existente, que todavía no emite refresh tokens.
    public String generarToken(Authentication authentication) {
        return crearToken(authentication, null);
    }

    public String generarToken(Authentication authentication, UUID familiaId) {
        Objects.requireNonNull(familiaId, "La familia de sesion es obligatoria");
        return crearToken(authentication, familiaId);
    }

    private String crearToken(Authentication authentication, UUID familiaId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("El usuario debe estar autenticado");
        }
        List<String> roles = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .filter(rol -> rol.startsWith("ROLE_"))
                .distinct()
                .sorted()
                .toList();

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("El usuario no tiene roles asignados");
        }

        Instant now = Instant.now();
        var builder = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(authentication.getName())
                .audience(List.of(audience))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(EXPIRATION_SEGUNDOS))
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .claim("roles", roles);
        if (familiaId != null) builder.claim("sid", familiaId.toString());
        JwtClaimsSet claims = builder.build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
