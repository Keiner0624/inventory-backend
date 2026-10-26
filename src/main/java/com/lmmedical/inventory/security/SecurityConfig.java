package com.lmmedical.inventory.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, RolesActualesJwtConverter rolesActualesJwtConverter) throws Exception {

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(rolesActualesJwtConverter);

        var logout = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout");
        var bearerTokenResolver = new DefaultBearerTokenResolver();

        http.csrf(AbstractHttpConfigurer::disable).sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                        HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
                ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auditoria", "/api/auditoria/**").hasRole("ADMIN")
                        .requestMatchers("/api/auditoria", "/api/auditoria/**").denyAll()
                        .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole("ADMIN", "ENCARGADO_ALMACEN", "EMPLEADO")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("ADMIN", "ENCARGADO_ALMACEN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(outh2 -> outh2
                        // Logout se autoriza con el refresh del cuerpo, incluso si el access token ya venció.
                        .bearerTokenResolver(request -> logout.matches(request) ? null : bearerTokenResolver.resolve(request))
                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
