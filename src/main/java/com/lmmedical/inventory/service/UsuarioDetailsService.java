package com.lmmedical.inventory.service;

import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.repository.UsuarioRolRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository  usuarioRolRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository, UsuarioRolRepository usuarioRolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        if (correo == null || correo.isBlank()){
            throw new UsernameNotFoundException("Credenciales invalidas");
        }

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales invalidas"));

        List<GrantedAuthority> permisos = usuarioRolRepository.findRolesByUsuarioId(usuario.getId())
                .stream().map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol.getCodigo())).toList();
        if (permisos.isEmpty()){
            throw new UsernameNotFoundException("Credenciales invalidas");
        }
        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPasswordHash())
                .disabled(!usuario.isActivo())
                .authorities(permisos).build();
    }
}
