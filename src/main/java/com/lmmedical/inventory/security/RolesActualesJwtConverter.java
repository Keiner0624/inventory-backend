package com.lmmedical.inventory.security;

import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.repository.SesionRefreshRepository;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.repository.UsuarioRolRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class RolesActualesJwtConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository  usuarioRolRepository;
    private final SesionRefreshRepository sesionRefreshRepository;

    public RolesActualesJwtConverter(UsuarioRepository usuarioRepository, UsuarioRolRepository usuarioRolRepository,  SesionRefreshRepository sesionRefreshRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.sesionRefreshRepository = sesionRefreshRepository;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        Usuario usuario = usuarioRepository.findByCorreo(source.getSubject())
                .orElseThrow(() -> new InvalidBearerTokenException("Token invalido"));

        if (!usuario.isActivo()){
            throw new InvalidBearerTokenException("Token invalido");
        }

        List<GrantedAuthority> permisos = usuarioRolRepository.findRolesByUsuarioId(usuario.getId())
                .stream().map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol.getCodigo())).toList();
        if (permisos.isEmpty()){
            throw new InvalidBearerTokenException("Token invalido");
        }

        UUID familiaId;
        try {
            String sid = source.getClaimAsString("sid");
            if (sid == null || sid.isBlank()) throw new IllegalArgumentException("Falta sid");
            familiaId = UUID.fromString(sid);
        }catch (RuntimeException exception){
            throw new InvalidBearerTokenException("Sesion invalida");
        }

        boolean sesionActiva = sesionRefreshRepository.existsByFamiliaIdAndUsuario_IdAndFechaRevocacionIsNullAndFechaExpiracionAfter(
                familiaId, usuario.getId(), OffsetDateTime.now(ZoneOffset.UTC)
        );

        if (!sesionActiva){
            throw new InvalidBearerTokenException("Sesion invalida");
        }
        return permisos;
    }

    @Override
    public <U> Converter<Jwt, U> andThen(Converter<? super Collection<GrantedAuthority>, ? extends U> after) {
        return Converter.super.andThen(after);
    }
}
