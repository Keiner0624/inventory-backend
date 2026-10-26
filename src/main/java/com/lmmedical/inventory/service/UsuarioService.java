package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.usuario.*;
import com.lmmedical.inventory.entity.Rol;
import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.entity.UsuarioRol;
import com.lmmedical.inventory.repository.RolRepository;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.repository.UsuarioRolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioRolRepository  usuarioRolRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository, UsuarioRolRepository usuarioRolRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponse crearEmpleado(CrearUsuarioRequest request){
        String correo = request.correo().strip().toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByCorreo(correo)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña no debe superar 72 bytes");
        }

        Rol rol = rolRepository.findByCodigo("EMPLEADO").orElseThrow(() ->new IllegalArgumentException("El rol EMPLEADO no existe"));
        String hash = passwordEncoder.encode(request.password());
        Usuario usuario = usuarioRepository.saveAndFlush(new Usuario(request.nombre(), request.apellido(), correo, hash));
        usuarioRolRepository.saveAndFlush(new UsuarioRol(usuario, rol));
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.isActivo(),
                rol.getCodigo()
        );
    }

    @Transactional(readOnly = true)
    public UsuarioPaginaResponse listar(Pageable pageable){
        if (pageable.getPageSize() > 100){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tamaño máximo de pagina es 100");
        }

        Page<Usuario> pagina = usuarioRepository.findAll(pageable);

        List<Long> ids = pagina.getContent().stream().map(Usuario::getId).toList();
        Map<Long, List<String>> rolesPorUsuario = new HashMap<>();

        if (!ids.isEmpty()) {
            List<UsuarioRol> asignaciones = usuarioRolRepository.findByUsuarioIdsWithRoles(ids);

            for (UsuarioRol asignacion : asignaciones) {
                Long usuarioId = asignacion.getId().getUsuarioId();
                rolesPorUsuario.computeIfAbsent(usuarioId, id -> new ArrayList<>()).add(asignacion.getRol().getCodigo());
            }
        }

        List<UsuarioConsultaResponse> usuarios = pagina.getContent().stream().map(usuario ->
                new UsuarioConsultaResponse(usuario.getId(), usuario.getNombre(), usuario.getApellido(), usuario.getCorreo(),
                        usuario.isActivo(), rolesPorUsuario.getOrDefault(usuario.getId(), List.of()).stream().sorted().toList())).toList();

        return new UsuarioPaginaResponse(
                usuarios,
                pagina.getTotalElements(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public UsuarioConsultaResponse buscarPorId(Long id){
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        List<String > roles = usuarioRolRepository.findRolesByUsuarioId(usuario.getId()).stream().map(Rol::getCodigo).sorted().toList();
        return new UsuarioConsultaResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.isActivo(),
                roles
        );
    }

    @Transactional
    public UsuarioConsultaResponse cambiarEstado(Long id, boolean activo, String correoAdministrador){
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (!activo && usuario.getCorreo().equalsIgnoreCase(correoAdministrador)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes desactivar tu propia cuenta");
        }
        usuario.cambiarEstado(activo);
        return buscarPorId(id);
    }

    @Transactional
    public UsuarioConsultaResponse actualizarRoles(Long usuarioId, ActualizarRolesRequest request, String correoAdministrador){
        Set<String > rolesPermitidos = Set.of("ADMIN", "ENCARGADO_ALMACEN", "EMPLEADO");
        if (request == null || request.roles() == null || request.roles().isEmpty() || request.roles().stream().anyMatch(rol -> rol == null || !rolesPermitidos.contains(rol))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los roles enviados no son validos");
        }
        Rol rolAdmin = rolRepository.bloquearAdmin().orElseThrow(() -> new IllegalStateException("El rol ADMIN no existe"));
        Usuario administrador = usuarioRepository.findByCorreo(correoAdministrador).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado"));
        boolean tienePermiso = administrador.isActivo() && usuarioRolRepository.findRolesByUsuarioId(administrador.getId()).stream().anyMatch(
                rol -> rol.getCodigo().equals("ADMIN")
        );

        if (!tienePermiso) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        List<Rol> rolesActuales = usuarioRolRepository.findRolesByUsuarioId(usuarioId);
        boolean areAdmin = rolesActuales.stream().anyMatch(rol -> rol.getCodigo().equals("ADMIN"));
        boolean seguiraAdmin = request.roles().contains("ADMIN");

        if (areAdmin && !seguiraAdmin) {
            if (usuario.getId().equals(administrador.getId())){
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes quitarte tu propio rol ADMIN");
            }
            if (usuarioRolRepository.contarAdministradores() <= 1 ){
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes eliminar el último administrador");
            }
        }
        Map<String , Rol> rolesDisponibles = new HashMap<>();
        rolesDisponibles.put("ADMIN", rolAdmin);

        for (String codigo : request.roles()) {
            if (!rolesDisponibles.containsKey(codigo)) {
                Rol rol = rolRepository.findByCodigo(codigo).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado: " + codigo));
                rolesDisponibles.put(codigo, rol);
            }
        }
        Set<String > nuevosRoles = request.roles();
        for (Rol rol : rolesActuales) {
            if (!nuevosRoles.contains(rol.getCodigo())) {
                usuarioRolRepository.deleteById(new UsuarioRol.Id(usuarioId, rol.getId()));
            }
        }

        for (String codigo : nuevosRoles) {
            boolean yaAsignado = rolesActuales.stream().anyMatch(rol -> rol.getCodigo().equals(codigo));
            if (!yaAsignado) {
                usuarioRolRepository.save(new UsuarioRol(usuario, rolesDisponibles.get(codigo)));
            }
        }
        usuarioRolRepository.flush();
        return new UsuarioConsultaResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.isActivo(),
                nuevosRoles.stream().sorted().toList()
        );
    }
}
