package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.support.CiSqlServer;
import com.lmmedical.inventory.dto.auth.*;
import com.lmmedical.inventory.dto.usuario.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.security.RefreshTokenGenerator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in: transacciones reales, exclusivamente en LM_Medical_Test; nunca borra fixtures. */
@SpringBootTest(properties={
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=LM_Medical_Test;encrypt=true;trustServerCertificate=true",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate",
        "app.bootstrap.admin.enabled=false", "app.bootstrap.admin.authorized=false", "app.jwt.enabled=true",
        "spring.jpa.show-sql=false"})
@ActiveProfiles("test")
@Import(SecuritySqlServerTest.ReadBarrierConfiguration.class)
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecuritySqlServerTest {
    private static volatile CyclicBarrier readBarrier;
    private static volatile String barrierHash;

    @TestConfiguration(proxyBeanMethods=false)
    static class ReadBarrierConfiguration {
        @Bean static BeanPostProcessor synchronizeRealReads() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof SesionRefreshRepository)) return bean;
                    var proxy = new ProxyFactory(bean);
                    proxy.addAdvice((MethodInterceptor) invocation -> {
                        Object result = invocation.proceed(); // Consulta real, dentro de la transaccion del servicio.
                        CyclicBarrier barrier = readBarrier;
                        if (barrier != null && invocation.getMethod().getName().equals("findByTokenHash")
                                && Objects.equals(barrierHash, invocation.getArguments()[0])) {
                            barrier.await(15, TimeUnit.SECONDS);
                        }
                        return result;
                    });
                    return proxy.getProxy();
                }
            };
        }
    }

    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(12).encode(PASSWORD);
    private static final byte[] KEY = new byte[32];
    static { new SecureRandom().nextBytes(KEY); }

    @DynamicPropertySource static void jwt(DynamicPropertyRegistry registry) {
        CiSqlServer.configure(registry);
        registry.add("app.jwt.secret-base64", () -> Base64.getEncoder().encodeToString(KEY));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired UsuarioRepository users;
    @Autowired RolRepository roles;
    @MockitoSpyBean UsuarioRolRepository assignments;
    @Autowired SesionRefreshRepository sessions;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RefreshTokenGenerator generator;
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Long> ownedUsers = new ArrayList<>();
    private TransactionTemplate tx;

    private void guard() {
        assertEquals("LM_Medical_Test", jdbc.queryForObject("SELECT DB_NAME()", String.class), "Base no autorizada");
        CiSqlServer.verify(jdbc);
    }

    @BeforeAll void comprobarAislamientoAntesDeEscribir() {
        guard();
        assertFalse(environment.getProperty("spring.flyway.enabled", Boolean.class, true));
        assertFalse(environment.getProperty("app.bootstrap.admin.enabled", Boolean.class, true));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='3' AND success=1", Integer.class));
        assertEquals(List.of("ADMIN", "EMPLEADO", "ENCARGADO_ALMACEN"), jdbc.queryForList("SELECT codigo FROM dbo.roles ORDER BY codigo", String.class));
        tx = new TransactionTemplate(transactionManager);
    }

    @BeforeEach void comprobarDestino() { guard(); ownedUsers.clear(); }

    @AfterEach void desactivarSoloFixturesSinBorrarlos() {
        reset(assignments);
        if (ownedUsers.isEmpty()) return;
        tx.executeWithoutResult(status -> {
            guard();
            for (Long id : ownedUsers) {
                jdbc.update("UPDATE dbo.sesiones_refresh SET fecha_revocacion=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00') WHERE usuario_id=? AND fecha_revocacion IS NULL", id);
                jdbc.update("UPDATE dbo.usuarios SET activo=0,fecha_actualizacion=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00') WHERE id=? AND correo LIKE 'fase129-%@example.invalid'", id);
            }
        });
    }

    private Usuario fixture(String... codes) {
        guard();
        return tx.execute(status -> {
            guard();
            var user = users.saveAndFlush(new Usuario("Fase129", "Seguridad", "fase129-"+UUID.randomUUID()+"@example.invalid", HASH));
            ownedUsers.add(user.getId());
            for (String code : codes) assignments.saveAndFlush(new UsuarioRol(user, roles.findByCodigo(code).orElseThrow()));
            return user;
        });
    }

    private MvcResult postJson(String path, Object body, String access) throws Exception {
        var request = post(path).contentType("application/json").content(json.writeValueAsString(body));
        if (access != null) request.header("Authorization", "Bearer "+access);
        return mvc.perform(request).andReturn();
    }

    private AuthTokenResponse login(Usuario user) throws Exception {
        var result = postJson("/api/auth/login", new LoginRequest(user.getCorreo(), PASSWORD), null);
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
        return json.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
    }

    private int access(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer "+token)).andReturn().getResponse().getStatus();
    }

    private String family(String refresh) {
        return jdbc.queryForObject("SELECT CONVERT(varchar(36),familia_id) FROM dbo.sesiones_refresh WHERE token_hash=?", String.class, generator.calcularHash(refresh));
    }

    private int active(String family) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.sesiones_refresh WHERE familia_id=CONVERT(uniqueidentifier,?) AND fecha_revocacion IS NULL", Integer.class, family);
    }

    private void expire(String refresh) {
        guard();
        jdbc.update("UPDATE dbo.sesiones_refresh SET fecha_creacion=DATEADD(day,-9,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')),fecha_expiracion=DATEADD(day,-1,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')) WHERE token_hash=?", generator.calcularHash(refresh));
    }

    @Test void loginVinculaElUsuarioYGuardaSoloHashConExpiracionDeSieteDias() throws Exception {
        var user = fixture("EMPLEADO");
        var tokens = login(user);
        assertTrue(tokens.accessToken() != null && !tokens.accessToken().isBlank());
        assertTrue(tokens.refreshToken().matches("[A-Za-z0-9_-]{43}"));
        String hash = generator.calcularHash(tokens.refreshToken());
        assertEquals(user.getId(), jdbc.queryForObject("SELECT usuario_id FROM dbo.sesiones_refresh WHERE token_hash=?", Long.class, hash));
        assertTrue(jdbc.queryForObject("SELECT token_hash FROM dbo.sesiones_refresh WHERE token_hash=?", String.class, hash).matches("[0-9a-f]{64}"));
        long seconds = jdbc.queryForObject("SELECT DATEDIFF_BIG(second,fecha_creacion,fecha_expiracion) FROM dbo.sesiones_refresh WHERE token_hash=?", Long.class, hash);
        assertTrue(Math.abs(seconds-Duration.ofDays(7).getSeconds())<=2);
        assertEquals(1, active(family(tokens.refreshToken())));
        String response = json.writeValueAsString(tokens);
        assertFalse(response.contains(PASSWORD));
        assertFalse(response.contains("passwordHash"));
        assertFalse(response.contains(HASH));
        assertEquals(200, access(tokens.accessToken(), "/api/productos"));
    }

    @ParameterizedTest @ValueSource(strings={"password", "correo", "inactivo", "sin-roles"})
    void loginRechazaCredencialesOEstadoIncorrectosSinCrearSesion(String cause) throws Exception {
        var user = cause.equals("sin-roles") ? fixture() : fixture("EMPLEADO");
        if (cause.equals("inactivo")) { guard(); jdbc.update("UPDATE dbo.usuarios SET activo=0 WHERE id=?", user.getId()); }
        String email = cause.equals("correo") ? UUID.randomUUID()+"@example.invalid" : user.getCorreo();
        var result = postJson("/api/auth/login", new LoginRequest(email, cause.equals("password") ? UUID.randomUUID().toString() : PASSWORD), null);
        assertEquals(401, result.getResponse().getStatus());
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
        assertFalse(result.getResponse().getContentAsString().contains(PASSWORD));
        assertFalse(result.getResponse().getContentAsString().contains(HASH));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.sesiones_refresh WHERE usuario_id=?", Integer.class, user.getId()));
    }

    private String jwt(Usuario user, String family, String variant) {
        Instant now = Instant.now();
        var builder = JwtClaimsSet.builder().issuer(variant.equals("issuer") ? "otro" : "lm-medical-supplies")
                .audience(List.of(variant.equals("audience") ? "otra" : "lm-medical-api"))
                .subject(user.getCorreo()).issuedAt(now.minusSeconds(7200))
                .expiresAt(variant.equals("expirado") ? now.minusSeconds(3600) : now.plusSeconds(900))
                .claim("token_type", variant.equals("tipo") ? "refresh" : "access").claim("roles", List.of("ROLE_ADMIN"));
        if (!variant.equals("sin-sid")) builder.claim("sid", variant.equals("sid-invalido") ? "invalido" : family);
        byte[] key = KEY;
        if (variant.equals("firma")) { key = new byte[32]; new SecureRandom().nextBytes(key); }
        var encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).algorithm(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }

    @Test void sinJwtDevuelve401() throws Exception {
        assertEquals(401, mvc.perform(get("/api/usuarios")).andReturn().getResponse().getStatus());
    }

    @ParameterizedTest @ValueSource(strings={"firma", "expirado", "sin-sid", "sid-invalido", "issuer", "audience", "tipo", "familia-ajena"})
    void jwtInvalidoNuncaAutorizaLaPeticion(String variant) throws Exception {
        var user = fixture("EMPLEADO");
        var tokens = login(user);
        String family = variant.equals("familia-ajena") ? family(login(fixture("EMPLEADO")).refreshToken()) : family(tokens.refreshToken());
        assertEquals(401, access(jwt(user, family, variant), "/api/productos"));
    }

    @ParameterizedTest @ValueSource(strings={"usuario-inactivo", "revocada", "expirada"})
    void jwtConsultaElEstadoActualDelUsuarioYLaSesion(String state) throws Exception {
        var user = fixture("EMPLEADO");
        var tokens = login(user);
        guard();
        if (state.equals("usuario-inactivo")) jdbc.update("UPDATE dbo.usuarios SET activo=0 WHERE id=?", user.getId());
        else if (state.equals("expirada")) expire(tokens.refreshToken());
        else jdbc.update("UPDATE dbo.sesiones_refresh SET fecha_revocacion=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00') WHERE token_hash=?", generator.calcularHash(tokens.refreshToken()));
        assertEquals(401, access(tokens.accessToken(), "/api/productos"));
    }

    @ParameterizedTest @ValueSource(strings={"ADMIN", "EMPLEADO", "ENCARGADO_ALMACEN"})
    void soloAdminPuedeConsultarUsuarios(String role) throws Exception {
        var tokens = login(fixture(role));
        assertEquals(role.equals("ADMIN") ? 200 : 403, access(tokens.accessToken(), "/api/usuarios"));
    }

    @Test void adminCreaEmpleadoSinExponerHashes() throws Exception {
        var admin = login(fixture("ADMIN"));
        String email = "fase129-"+UUID.randomUUID()+"@example.invalid";
        guard();
        var result = postJson("/api/usuarios", new CrearUsuarioRequest("Fase129", "Creado", email, PASSWORD), admin.accessToken());
        var created = users.findByCorreo(email);
        created.ifPresent(u -> ownedUsers.add(u.getId()));
        assertEquals(201, result.getResponse().getStatus());
        assertTrue(created.isPresent());
        assertEquals(List.of("EMPLEADO"), assignments.findRolesByUsuarioId(created.orElseThrow().getId()).stream().map(Rol::getCodigo).toList());
        assertFalse(result.getResponse().getContentAsString().contains(HASH));
        assertFalse(result.getResponse().getContentAsString().contains(PASSWORD));
        assertFalse(result.getResponse().getContentAsString().contains("passwordHash"));
    }

    @ParameterizedTest @ValueSource(strings={"EMPLEADO", "ENCARGADO_ALMACEN"})
    void rolesNoAdministrativosNoPuedenCrearDesactivarNiCambiarRoles(String role) throws Exception {
        var user = fixture(role);
        var token = login(user).accessToken();
        assertEquals(403, postJson("/api/usuarios", new CrearUsuarioRequest("Fase129", "Bloqueado", "fase129-"+UUID.randomUUID()+"@example.invalid", PASSWORD), token).getResponse().getStatus());
        assertEquals(403, mvc.perform(patch("/api/usuarios/"+user.getId()+"/estado").header("Authorization", "Bearer "+token)
                .contentType("application/json").content("{\"activo\":false}")).andReturn().getResponse().getStatus());
        assertEquals(403, putRoles(user.getId(), Set.of("ADMIN"), token));
        assertTrue(users.findById(user.getId()).orElseThrow().isActivo());
    }

    private int putRoles(long id, Set<String> roles, String token) throws Exception {
        guard();
        return mvc.perform(put("/api/usuarios/"+id+"/roles").header("Authorization", "Bearer "+token)
                .contentType("application/json").content(json.writeValueAsString(new ActualizarRolesRequest(roles))))
                .andReturn().getResponse().getStatus();
    }

    @Test void losCambiosDeRolesRigenEnLaSiguientePeticionConElMismoJwt() throws Exception {
        var admin = login(fixture("ADMIN"));
        var user = fixture("EMPLEADO");
        var token = login(user).accessToken();
        assertEquals(403, access(token, "/api/usuarios"));
        assertEquals(200, putRoles(user.getId(), Set.of("ADMIN"), admin.accessToken()));
        assertEquals(200, access(token, "/api/usuarios"));
        assertEquals(200, putRoles(user.getId(), Set.of("EMPLEADO"), admin.accessToken()));
        assertEquals(403, access(token, "/api/usuarios"));
    }

    @Test void adminNoPuedeDesactivarseNiQuitarseSuRol() throws Exception {
        var admin = fixture("ADMIN");
        var token = login(admin).accessToken();
        guard();
        assertEquals(409, mvc.perform(patch("/api/usuarios/"+admin.getId()+"/estado").header("Authorization", "Bearer "+token)
                .contentType("application/json").content("{\"activo\":false}")).andReturn().getResponse().getStatus());
        assertEquals(409, putRoles(admin.getId(), Set.of("EMPLEADO"), token));
        assertTrue(users.findById(admin.getId()).orElseThrow().isActivo());
        assertEquals(200, access(token, "/api/usuarios"));
    }

    @Test void rolInexistenteNoDejaModificacionesParciales() throws Exception {
        var admin = login(fixture("ADMIN"));
        var user = fixture("EMPLEADO");
        assertEquals(400, putRoles(user.getId(), Set.of("ADMIN", "SUPERADMIN"), admin.accessToken()));
        assertEquals(List.of("EMPLEADO"), assignments.findRolesByUsuarioId(user.getId()).stream().map(Rol::getCodigo).toList());
    }

    @Test void falloDespuesDeUnaEscrituraRevierteLaActualizacionDeRoles() throws Exception {
        var admin = login(fixture("ADMIN"));
        var user = fixture("EMPLEADO");
        AtomicBoolean reachedDatabase = new AtomicBoolean();
        doAnswer(call -> {
            em.flush();
            reachedDatabase.set(true);
            throw new DataIntegrityViolationException("Fallo controlado de la prueba, sin datos sensibles");
        }).when(assignments).save(any(UsuarioRol.class));
        try {
            assertEquals(409, putRoles(user.getId(), Set.of("ENCARGADO_ALMACEN"), admin.accessToken()));
        } finally { reset(assignments); }
        assertTrue(reachedDatabase.get(), "La prueba debe fallar despues de enviar la escritura a SQL Server");
        assertEquals(List.of("EMPLEADO"), jdbc.queryForList("SELECT r.codigo FROM dbo.usuario_rol ur JOIN dbo.roles r ON r.id=ur.rol_id WHERE ur.usuario_id=? ORDER BY r.codigo", String.class, user.getId()));
    }

    @Test void refreshRotaRevocaElAnteriorYConservaFamilia() throws Exception {
        var original = login(fixture("EMPLEADO"));
        String family = family(original.refreshToken());
        var result = postJson("/api/auth/refresh", new RefreshTokenRequest(original.refreshToken()), null);
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
        var renewed = json.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        assertFalse(original.refreshToken().equals(renewed.refreshToken()));
        assertEquals(family, family(renewed.refreshToken()));
        assertEquals(1, active(family));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.sesiones_refresh WHERE token_hash=? AND fecha_revocacion IS NOT NULL", Integer.class, generator.calcularHash(original.refreshToken())));
    }

    @ParameterizedTest @ValueSource(strings={"expirado", "inactivo", "inexistente", "formato"})
    void refreshInvalidoDevuelve401SinNuevaEmision(String cause) throws Exception {
        var user = fixture("EMPLEADO");
        var original = login(user);
        guard();
        if (cause.equals("expirado")) expire(original.refreshToken());
        if (cause.equals("inactivo")) jdbc.update("UPDATE dbo.usuarios SET activo=0 WHERE id=?", user.getId());
        String refresh = cause.equals("inexistente") ? generator.generateToken() : cause.equals("formato") ? "invalido" : original.refreshToken();
        assertEquals(401, postJson("/api/auth/refresh", new RefreshTokenRequest(refresh), null).getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.sesiones_refresh WHERE usuario_id=?", Integer.class, user.getId()));
    }

    @Test void reutilizarAConfirmaLaRevocacionDeBEnOtraTransaccion() throws Exception {
        var original = login(fixture("EMPLEADO"));
        var response = postJson("/api/auth/refresh", new RefreshTokenRequest(original.refreshToken()), null);
        assertEquals(200, response.getResponse().getStatus());
        var renewed = json.readValue(response.getResponse().getContentAsString(), AuthTokenResponse.class);
        assertEquals(401, postJson("/api/auth/refresh", new RefreshTokenRequest(original.refreshToken()), null).getResponse().getStatus());
        // El test NO tiene @Transactional: JdbcTemplate abre otra conexion despues del commit HTTP.
        assertEquals(0, active(family(original.refreshToken())), "La revocacion no puede perderse por rollback al devolver 401");
        assertEquals(401, postJson("/api/auth/refresh", new RefreshTokenRequest(renewed.refreshToken()), null).getResponse().getStatus());
        assertEquals(401, access(original.accessToken(), "/api/productos"));
        assertEquals(401, access(renewed.accessToken(), "/api/productos"));
    }

    @RepeatedTest(3) void dosRenovacionesConcurrentesTienenUnSoloGanadorYRevocanLaFamilia() throws Exception {
        var original = login(fixture("EMPLEADO"));
        String hash = generator.calcularHash(original.refreshToken());
        barrierHash = hash;
        readBarrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> postJson("/api/auth/refresh", new RefreshTokenRequest(original.refreshToken()), null).getResponse().getStatus();
            Future<Integer> a = executor.submit(attempt);
            Future<Integer> b = executor.submit(attempt);
            var statuses = new ArrayList<>(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)));
            Collections.sort(statuses);
            assertEquals(List.of(200, 401), statuses);
        } finally { executor.shutdownNow(); readBarrier = null; barrierHash = null; }
        String family = family(original.refreshToken());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.sesiones_refresh WHERE familia_id=CONVERT(uniqueidentifier,?)", Integer.class, family));
        assertEquals(0, active(family), "El perdedor detecta reutilizacion y confirma la revocacion del ganador");
        assertEquals(401, access(original.accessToken(), "/api/productos"));
    }

    @Test void logoutAislaFamiliasYUsuariosEsIdempotenteYPermiteLoginNuevo() throws Exception {
        var user = fixture("EMPLEADO");
        var first = login(user);
        var independent = login(user);
        var other = login(fixture("EMPLEADO"));
        assertFalse(first.refreshToken().equals(independent.refreshToken()));
        for (int i=0; i<2; i++) {
            var result = postJson("/api/auth/logout", new RefreshTokenRequest(first.refreshToken()), null);
            assertEquals(204, result.getResponse().getStatus());
            assertTrue(result.getResponse().getContentAsString().isEmpty());
        }
        assertEquals(0, active(family(first.refreshToken())));
        assertEquals(401, access(first.accessToken(), "/api/productos"));
        assertEquals(200, access(independent.accessToken(), "/api/productos"));
        assertEquals(200, access(other.accessToken(), "/api/productos"));
        assertEquals(1, active(family(login(user).refreshToken())));
    }
}
