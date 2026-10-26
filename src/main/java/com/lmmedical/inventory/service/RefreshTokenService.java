package com.lmmedical.inventory.service;

import com.lmmedical.inventory.entity.SesionRefresh;
import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.repository.SesionRefreshRepository;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.security.RefreshTokenGenerator;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class RefreshTokenService {
    private static final int DIAS_EXPIRACION = 7;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final SesionRefreshRepository refreshRepository;
    private final RefreshTokenGenerator  refreshTokenGenerator;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    public RefreshTokenService(SesionRefreshRepository refreshRepository, RefreshTokenGenerator refreshTokenGenerator,  UsuarioRepository usuarioRepository,  EntityManager entityManager) {
        this.refreshRepository = refreshRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Emision emitir(Usuario usuario){
        if (usuario == null || !usuario.isActivo()){
            throw tokenInvalido();
        }

        String token = refreshTokenGenerator.generateToken();
        String hash = refreshTokenGenerator.calcularHash(token);

        OffsetDateTime expiracion = OffsetDateTime.now(ZoneOffset.UTC).plusDays(DIAS_EXPIRACION);
        SesionRefresh sesion = new SesionRefresh(usuario, hash, expiracion);
        refreshRepository.saveAndFlush(sesion);
        return new Emision(sesion.getFamiliaId(), token);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Rotacion renovar(String refreshToken) {

        String hash = calcularHashValido(refreshToken);

        SesionRefresh referencia = refreshRepository
                .findByTokenHash(hash)
                .orElseThrow(this::tokenInvalido);

        Usuario usuario = usuarioRepository
                .bloquearPorId(referencia.getUsuario().getId())
                .orElseThrow(this::tokenInvalido);

        SesionRefresh sesionAnterior = refreshRepository
                .bloquearPorHash(hash)
                .orElseThrow(this::tokenInvalido);

        entityManager.refresh(sesionAnterior);

        OffsetDateTime ahora =
                OffsetDateTime.now(ZoneOffset.UTC);

        // Detectar posible reutilización
        if (sesionAnterior.getFechaRevocacion() != null) {

            revocarFamilia(
                    sesionAnterior.getFamiliaId(),
                    usuario.getId(),
                    ahora
            );

            throw tokenInvalido();
        }

        // Verificar expiración
        if (!sesionAnterior.getFechaExpiracion().isAfter(ahora)) {
            throw tokenInvalido();
        }

        // Verificar usuario
        if (!usuario.isActivo()) {
            throw tokenInvalido();
        }

        // Revocar refresh token anterior
        sesionAnterior.revocar(ahora);

        // Crear refresh token nuevo
        String nuevoToken = refreshTokenGenerator.generateToken();

        String nuevoHash = refreshTokenGenerator.calcularHash(nuevoToken);

        SesionRefresh nuevaSesion = new SesionRefresh(usuario, nuevoHash, sesionAnterior.getFamiliaId(),
                ahora.plusDays(DIAS_EXPIRACION)
        );

        refreshRepository.saveAndFlush(nuevaSesion);

        return new Rotacion(
                usuario,
                nuevaSesion.getFamiliaId(),
                nuevoToken
        );
    }

    @Transactional
    public void revocar(String refreshToken){
        String hash = calcularHashValido(refreshToken);
        refreshRepository.bloquearPorHash(hash).ifPresent(sesion -> {
            if (sesion.getFechaRevocacion() == null){
                sesion.revocar(OffsetDateTime.now(ZoneOffset.UTC));
            }
        });
    }
    private String calcularHashValido(String token){
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()){
            throw tokenInvalido();
        }
        return refreshTokenGenerator.calcularHash(token);
    }

    private ResponseStatusException tokenInvalido(){
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalido o expirado");
    }

    public record Emision(UUID familiaId, String refreshToken) {
        @Override public String toString() { return "Emision[familiaId=" + familiaId + ", refreshToken=<oculto>]"; }
    }
    public record Rotacion(Usuario usuario, UUID familiaId, String refreshToken) {
        @Override public String toString() { return "Rotacion[familiaId=" + familiaId + ", refreshToken=<oculto>]"; }
    }

    @Transactional
    public void cerrarSesion(String refreshToken){
        String hash = calcularHashValido(refreshToken);
        var referencia = refreshRepository.findByTokenHash(hash);

        if (referencia.isEmpty()){
            return;
        }

        Long usuarioId = referencia.get().getUsuario().getId();
        usuarioRepository.bloquearPorId(usuarioId).orElseThrow(this::tokenInvalido);
        var sesionActual = refreshRepository.bloquearPorHash(hash);

        if (sesionActual.isEmpty()){
            return;
        }

        SesionRefresh sesion = sesionActual.get();
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

        if (!sesion.estaVigente(ahora)){
            return;
        }

        List<SesionRefresh> sesiones = refreshRepository.findByFamiliaIdAndUsuario_Id(sesion.getFamiliaId(), usuarioId);
        for (SesionRefresh registro : sesiones){
            if (registro.getFechaRevocacion() == null){
                registro.revocar(ahora);
            }
        }
    }

    public void revocarFamilia(UUID familiaId, Long usuarioId, OffsetDateTime ahora){
        List<SesionRefresh> sesiones = refreshRepository.findByFamiliaIdAndUsuario_Id(familiaId, usuarioId);
        for (SesionRefresh sesion : sesiones){
            if (sesion.getFechaRevocacion() == null){
                sesion.revocar(ahora);
            }
        }
        refreshRepository.flush();
    }
}