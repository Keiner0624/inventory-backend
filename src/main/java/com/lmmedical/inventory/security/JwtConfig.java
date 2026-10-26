package com.lmmedical.inventory.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(
        prefix = "app.jwt",
        name = "enabled",
        havingValue = "true"
)
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.jwt.secret-base64}") String secret
    ) {
        try {
            byte[] bytes = Base64.getDecoder().decode(secret);

            if (bytes.length < 32) {
                throw new IllegalStateException(
                        "La clave JWT debe tener al menos 32 bytes"
                );
            }

            return new SecretKeySpec(bytes, "HmacSHA256");

        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "La clave JWT debe estar codificada en Base64",
                    exception
            );
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey secretKey,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtAudienceValidator(audience),
                        new JwtClaimValidator<String>(
                                "token_type",
                                "access"::equals
                        )
                )
        );

        return decoder;
    }

    @Bean
    public JwtService jwtService(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience
    ) {
        return new JwtService(jwtEncoder, issuer, audience);
    }
}