package com.lmmedical.inventory.entity;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeguridadEntityTest {
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    private Usuario usuario() { return new Usuario(" Lucía ", " Pérez ", " Persona@Example.test ", HASH); }
    private String tokenHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void normalizaCorreoYMantieneFechasUtc() {
        Usuario usuario = usuario();
        assertEquals("persona@example.test", usuario.getCorreo());
        assertEquals("Lucía", usuario.getNombre());
        assertEquals(ZoneOffset.UTC, usuario.getFechaCreacion().getOffset());
        assertEquals(usuario.getFechaCreacion(), usuario.getFechaActualizacion());
        assertTrue(usuario.isActivo());
    }

    @Test
    void rechazaUnValorSinFormatoDeHashAdaptativo() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("A", "B", "a@example.test", "valor-invalido"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("A", "B", "a@example.test", HASH.replace("$12$", "$04$")));
    }

    @Test
    void validaCamposObligatoriosYCorreo() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(usuario()).isEmpty());
            var invalid = new Usuario(" ", "B", "correo-invalido", HASH);
            var fields = validator.validate(invalid).stream().map(v -> v.getPropertyPath().toString()).toList();
            assertTrue(fields.contains("nombre"));
            assertTrue(fields.contains("correo"));
            assertFalse(validator.validate(new Rol("rol no valido", " ")).isEmpty());
        }
    }

    @Test
    void nuncaSerializaHashes() throws Exception {
        var mapper = JsonMapper.builder().build();
        String userJson = mapper.writeValueAsString(usuario());
        assertFalse(userJson.contains("passwordHash"));
        assertFalse(userJson.contains(HASH));
        String tokenHash = tokenHash();
        String sessionJson = mapper.writeValueAsString(new SesionRefresh(usuario(), tokenHash, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)));
        assertFalse(sessionJson.contains("tokenHash"));
        assertFalse(sessionJson.contains(tokenHash));
        assertFalse(sessionJson.contains("passwordHash"));
    }

    @Test
    void sesionExigeHashYExpiracionPosterior() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new SesionRefresh(usuario(), "valor-invalido", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)));
        String hash = tokenHash();
        assertThrows(IllegalArgumentException.class, () -> new SesionRefresh(usuario(), hash, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)));
    }

    @Test
    void preparaFamiliasYNormalizaZonaHoraria() throws Exception {
        UUID family = UUID.randomUUID();
        var session = new SesionRefresh(usuario(), tokenHash(), family, OffsetDateTime.now(ZoneOffset.ofHours(2)).plusDays(1));
        assertEquals(family, session.getFamiliaId());
        assertEquals(ZoneOffset.UTC, session.getFechaCreacion().getOffset());
        assertEquals(ZoneOffset.UTC, session.getFechaExpiracion().getOffset());
    }

    @Test
    void revocacionEsIdempotenteYPuedeSerPosteriorALaExpiracion() throws Exception {
        var session = new SesionRefresh(usuario(), tokenHash(), OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> session.revocar(session.getFechaCreacion().minusSeconds(1)));
        var revoked = session.getFechaExpiracion().plusDays(1);
        session.revocar(revoked);
        session.revocar(revoked.plusDays(1));
        assertEquals(revoked, session.getFechaRevocacion());
    }

    @Test
    void claveCompuestaDetectaLaMismaAsignacion() {
        assertEquals(new UsuarioRol.Id(1L, 2L), new UsuarioRol.Id(1L, 2L));
        assertNotEquals(new UsuarioRol.Id(1L, 2L), new UsuarioRol.Id(1L, 3L));
        assertEquals("ENCARGADO_ALMACEN", new Rol(" encargado_almacen ", "Encargado").getCodigo());
    }
}
