package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("flyway-check")
class SeguridadPersistenciaSqlServerTest {
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioRolRepository asignaciones;
    @Autowired private SesionRefreshRepository sesiones;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

    private Usuario usuario() { return usuarios.saveAndFlush(new Usuario("Lucía", "Pérez", UUID.randomUUID() + "@example.test", HASH)); }
    private String hash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))); }

    @Test
    void v3YHibernateEstanValidadosSinAdministradorReal() {
        assertTrue("LM_Medical_Flyway_Prueba".equalsIgnoreCase(jdbc.queryForObject("SELECT DB_NAME()", String.class)));
        assertEquals("3", flyway.info().current().getVersion().toString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='3' AND type='SQL' AND success=1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=0", Integer.class));
        assertTrue(emf.isOpen());
        assertEquals(15, jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name<>'flyway_schema_history'", Integer.class));
        assertEquals(List.of("ADMIN", "EMPLEADO", "ENCARGADO_ALMACEN"), jdbc.queryForList("SELECT codigo FROM dbo.roles ORDER BY codigo", String.class));
        assertEquals(0L, usuarios.count());
        assertEquals(0L, asignaciones.count());
        assertEquals(0L, sesiones.count());
        flyway.validate();
        assertEquals(0, flyway.info().pending().length);
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test @Transactional
    void repositoriosBuscanCorreoNormalizadoYRolesSinDuplicados() {
        Usuario usuario = usuarios.saveAndFlush(new Usuario("Lucía", "Pérez", " Caso-" + UUID.randomUUID() + "@EXAMPLE.TEST ", HASH));
        String correo = usuario.getCorreo();
        Long id = usuario.getId();
        assertEquals(correo.toLowerCase(java.util.Locale.ROOT), correo);
        assertTrue(usuarios.existsByCorreo(correo));
        assertTrue(usuarios.existsByCorreo(" " + correo.toUpperCase(java.util.Locale.ROOT) + " "));
        assertEquals(id, usuarios.findByCorreo(correo).orElseThrow().getId());
        assertEquals(id, usuarios.findByCorreo(" " + correo.toUpperCase(java.util.Locale.ROOT) + " ").orElseThrow().getId());
        Rol rol = roles.findByCodigo("ADMIN").orElseThrow();
        asignaciones.saveAndFlush(new UsuarioRol(usuario, rol));
        asignaciones.saveAndFlush(new UsuarioRol(usuario, rol));
        em.clear();
        assertEquals(List.of("ADMIN"), asignaciones.findRolesByUsuarioId(id).stream().map(Rol::getCodigo).toList());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.usuario_rol WHERE usuario_id=?", Integer.class, id));
    }

    @Test @Transactional
    void sesionesConservanHashFamiliaRevocacionYUtc() throws Exception {
        Usuario usuario = usuario();
        String hash = hash();
        UUID family = UUID.randomUUID();
        SesionRefresh session = sesiones.saveAndFlush(new SesionRefresh(usuario, hash, family, OffsetDateTime.now(ZoneOffset.ofHours(2)).plusDays(1)));
        UUID id = session.getId();
        assertNotNull(id);
        session.revocar(OffsetDateTime.now(ZoneOffset.UTC));
        sesiones.flush();
        em.clear();
        var loaded = sesiones.findByTokenHash(hash).orElseThrow();
        assertEquals(id, loaded.getId());
        assertEquals(family, loaded.getFamiliaId());
        assertNotNull(loaded.getFechaRevocacion());
        assertEquals(ZoneOffset.UTC, loaded.getFechaCreacion().getOffset());
        assertEquals(ZoneOffset.UTC, loaded.getFechaExpiracion().getOffset());
        assertEquals(ZoneOffset.UTC, loaded.getFechaRevocacion().getOffset());
        sesiones.saveAndFlush(new SesionRefresh(usuario, hash(), family, OffsetDateTime.now(ZoneOffset.UTC).plusDays(2)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.sesiones_refresh WHERE usuario_id=? AND familia_id=?", Integer.class, usuario.getId(), family.toString()));
    }

    @Test @Transactional
    void actualizacionDeUsuarioMantieneAuditoriaUtc() {
        Usuario usuario = usuario();
        var created = usuario.getFechaCreacion();
        Long id = usuario.getId();
        usuario.cambiarEstado(false);
        usuarios.flush();
        em.clear();
        var loaded = usuarios.findById(id).orElseThrow();
        assertFalse(loaded.isActivo());
        assertEquals(created, loaded.getFechaCreacion());
        assertFalse(loaded.getFechaActualizacion().isBefore(created));
        assertEquals(ZoneOffset.UTC, loaded.getFechaActualizacion().getOffset());
    }
}
