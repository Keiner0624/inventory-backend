package com.lmmedical.inventory.service;

import com.lmmedical.inventory.entity.SesionRefresh;
import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.repository.SesionRefreshRepository;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.security.RefreshTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private SesionRefreshRepository repository;
    private UsuarioRepository users;
    private RefreshTokenGenerator generator;
    private RefreshTokenService service;
    private Usuario usuario;
    private final String input = "_-" + "A".repeat(41);

    @BeforeEach void setup() {
        repository = mock(SesionRefreshRepository.class);
        generator = new RefreshTokenGenerator();
        users = mock(UsuarioRepository.class);
        service = new RefreshTokenService(repository, generator, users, mock(jakarta.persistence.EntityManager.class));
        usuario = new Usuario("Ana", "Pérez", "persona@example.test", PASSWORD_HASH);
        ReflectionTestUtils.setField(usuario, "id", 1L);
        when(repository.saveAndFlush(any(SesionRefresh.class))).thenAnswer(invocation -> {
            SesionRefresh session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
            return session;
        });
    }

    private SesionRefresh session() {
        SesionRefresh session = new SesionRefresh(usuario, generator.calcularHash(input), OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(repository.bloquearPorHash(generator.calcularHash(input))).thenReturn(Optional.of(session));
        when(repository.findByTokenHash(generator.calcularHash(input))).thenReturn(Optional.of(session));
        when(users.bloquearPorId(1L)).thenReturn(Optional.of(usuario));
        return session;
    }

    private void unauthorized(Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(401, error.getStatusCode().value());
    }

    @Test void emiteUnTokenYGuardaSoloSuHashConExpiracionUtc() {
        var result = service.emitir(usuario);
        var stored = ArgumentCaptor.forClass(SesionRefresh.class);
        verify(repository).saveAndFlush(stored.capture());
        assertNotNull(result.familiaId());
        assertTrue(result.refreshToken().matches("[A-Za-z0-9_-]{43}"));
        assertEquals(generator.calcularHash(result.refreshToken()), stored.getValue().getTokenHash());
        assertNotEquals(result.refreshToken(), stored.getValue().getTokenHash());
        assertEquals(result.familiaId(), stored.getValue().getFamiliaId());
        assertEquals(ZoneOffset.UTC, stored.getValue().getFechaExpiracion().getOffset());
        long seconds = Duration.between(stored.getValue().getFechaCreacion(), stored.getValue().getFechaExpiracion()).getSeconds();
        assertTrue(Math.abs(seconds - Duration.ofDays(7).getSeconds()) <= 1);
    }

    @Test void noEmiteParaUsuarioNulo() {
        unauthorized(() -> service.emitir(null));
        verifyNoInteractions(repository);
    }

    @Test void noEmiteParaUsuarioInactivo() {
        usuario.cambiarEstado(false);
        unauthorized(() -> service.emitir(usuario));
        verifyNoInteractions(repository);
    }

    @Test void rotaTokenBase64UrlRevocaElAnteriorYConservaLaFamilia() {
        var previous = session();
        var result = service.renovar(input);
        var stored = ArgumentCaptor.forClass(SesionRefresh.class);
        verify(repository).saveAndFlush(stored.capture());
        assertNotNull(previous.getFechaRevocacion());
        assertEquals(previous.getFamiliaId(), stored.getValue().getFamiliaId());
        assertEquals(previous.getFamiliaId(), result.familiaId());
        assertEquals(generator.calcularHash(result.refreshToken()), stored.getValue().getTokenHash());
        assertNotEquals(input, result.refreshToken());
        assertSame(usuario, result.usuario());
        assertEquals(stored.getValue().getFamiliaId(), result.familiaId());
    }

    @Test void rechazaSesionExpirada() {
        var previous = session();
        ReflectionTestUtils.setField(previous, "fechaExpiracion", OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        unauthorized(() -> service.renovar(input));
        verify(repository, never()).saveAndFlush(any());
        assertNull(previous.getFechaRevocacion());
    }

    @Test void rechazaLaReutilizacionDeUnaSesionRevocada() {
        var previous = session();
        previous.revocar(OffsetDateTime.now(ZoneOffset.UTC));
        unauthorized(() -> service.renovar(input));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void noRenuevaParaUsuarioInactivo() {
        var previous = session();
        usuario.cambiarEstado(false);
        unauthorized(() -> service.renovar(input));
        assertNull(previous.getFechaRevocacion());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void rechazaUnTokenDesconocido() {
        when(repository.bloquearPorHash(generator.calcularHash(input))).thenReturn(Optional.empty());
        unauthorized(() -> service.renovar(input));
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={" ", "corto", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void formatoInvalidoDevuelve401AntesDeConsultar(String token) {
        unauthorized(() -> service.renovar(token));
        unauthorized(() -> service.revocar(token));
        verifyNoInteractions(repository);
    }

    @Test void revocaUnaSesionEncontradaConOptionalIfPresent() {
        var previous = session();
        service.revocar(input);
        assertNotNull(previous.getFechaRevocacion());
        assertEquals(ZoneOffset.UTC, previous.getFechaRevocacion().getOffset());
    }

    @Test void revocacionRepetidaConservaLaPrimeraFecha() {
        var previous = session();
        service.revocar(input);
        var first = previous.getFechaRevocacion();
        service.revocar(input);
        assertEquals(first, previous.getFechaRevocacion());
    }

    @Test void revocarUnTokenDesconocidoEsIdempotente() {
        when(repository.bloquearPorHash(generator.calcularHash(input))).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.revocar(input));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void losResultadosNoExponenTokensEnToString() {
        var emission = service.emitir(usuario);
        assertFalse(emission.toString().contains(emission.refreshToken()));
        session();
        var rotation = service.renovar(input);
        assertFalse(rotation.toString().contains(rotation.refreshToken()));
    }
}
