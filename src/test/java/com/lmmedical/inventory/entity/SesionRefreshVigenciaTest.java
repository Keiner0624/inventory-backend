package com.lmmedical.inventory.entity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SesionRefreshVigenciaTest {
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    private SesionRefresh session() {
        var user = new Usuario("Ana", "Pérez", "persona@example.test", HASH);
        return new SesionRefresh(user, "a".repeat(64), OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    }

    @Test void esVigenteAntesDeExpirar() {
        var session = session();
        assertTrue(session.estaVigente(session.getFechaExpiracion().minusNanos(1)));
    }

    @Test void dejaDeSerVigenteExactamenteAlExpirar() {
        var session = session();
        assertFalse(session.estaVigente(session.getFechaExpiracion()));
        assertFalse(session.estaVigente(session.getFechaExpiracion().plusSeconds(1)));
    }

    @Test void revocadaNoEstaVigenteAunqueNoHayaExpirado() {
        var session = session();
        session.revocar(OffsetDateTime.now(ZoneOffset.UTC));
        assertFalse(session.estaVigente(session.getFechaExpiracion().minusMinutes(1)));
    }

    @Test void noEstaVigenteAntesDeSuCreacion() {
        var session = session();
        assertFalse(session.estaVigente(session.getFechaCreacion().minusNanos(1)));
        assertTrue(session.estaVigente(session.getFechaCreacion()));
    }

    @Test void exigeUnInstanteDeValidacion() {
        assertThrows(NullPointerException.class, () -> session().estaVigente(null));
    }
}
