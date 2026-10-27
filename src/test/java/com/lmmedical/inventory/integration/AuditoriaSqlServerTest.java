package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.dto.*;
import com.lmmedical.inventory.dto.auth.*;
import com.lmmedical.inventory.dto.usuario.*;
import com.lmmedical.inventory.dto.auditoria.*;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import com.lmmedical.inventory.service.*;
import com.lmmedical.inventory.support.CiSqlServer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in. DML solo en LM_Medical_Test verificada; ninguna limpieza destructiva ni migración automática. */
@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=validate", "app.bootstrap.admin.enabled=false",
        "app.bootstrap.admin.authorized=false", "app.jwt.enabled=true", "spring.jpa.show-sql=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditoriaSqlServerTest {
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String HASH = "{bcrypt}"+new BCryptPasswordEncoder(12).encode(PASSWORD);
    private static final byte[] KEY = new byte[32];
    static { new SecureRandom().nextBytes(KEY); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        CiSqlServer.configure(registry);
        registry.add("app.jwt.secret-base64", () -> Base64.getEncoder().encodeToString(KEY));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UsuarioRepository users;
    @Autowired RolRepository roles;
    @Autowired UsuarioRolRepository assignments;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired AlmacenRepository warehouses;
    @Autowired LoteRepository lots;
    @Autowired InventarioService entradas;
    @Autowired SalidaIdempotenteService salidas;
    @Autowired TransferenciaService transferencias;
    @Autowired ProductService productos;
    @Autowired UsuarioService usuarios;
    @Autowired CategoryService categorias;
    @Autowired AlmacenService almacenes;
    @Autowired ProveedorService proveedores;
    @Autowired LoteService lotes;
    @Autowired AuditoriaConsultaService consulta;
    @MockitoSpyBean AuditoriaService auditoria;
    @Autowired MockMvc mvc;
    JsonMapper json = JsonMapper.builder().build();
    TransactionTemplate tx;
    Usuario actor;
    record Fixture(Product producto, Almacen origen, Almacen destino, Lote primero, Lote segundo) {}

    void guard() {
        assertEquals("LM_Medical_Test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        CiSqlServer.verify(jdbc);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='4' AND type='SQL' AND success=1", Integer.class));
    }
    @BeforeAll void init() { guard(); tx=new TransactionTemplate(transactions); }
    @BeforeEach void setup() { guard(); actor=usuarioFixture("ADMIN"); autenticar(actor); }
    @AfterEach void clear() { reset(auditoria); SecurityContextHolder.clearContext(); }
    void autenticar(Usuario user) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user.getCorreo(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    Usuario usuarioFixture(String role) {
        guard();
        return tx.execute(status -> {
            var user=users.saveAndFlush(new Usuario("Fase13", "Auditoría", "fase13-"+UUID.randomUUID()+"@example.invalid", HASH));
            assignments.saveAndFlush(new UsuarioRol(user, roles.findByCodigo(role).orElseThrow()));
            return user;
        });
    }
    Fixture fixture() {
        guard();
        return tx.execute(status -> {
            String code=UUID.randomUUID().toString().substring(0,16);
            var category=categories.saveAndFlush(new Category("F13-"+code));
            var product=products.saveAndFlush(new Product("F13-"+code, "Producto "+code, null, BigDecimal.ONE, BigDecimal.TEN, category));
            var origin=warehouses.saveAndFlush(new Almacen("O-"+code,"Origen "+code,"Prueba"));
            var destination=warehouses.saveAndFlush(new Almacen("D-"+code,"Destino "+code,"Prueba"));
            var first=lots.saveAndFlush(new Lote(product,"L1",LocalDate.now().plusDays(30)));
            var second=lots.saveAndFlush(new Lote(product,"L2",LocalDate.now().plusDays(60)));
            return new Fixture(product,origin,destination,first,second);
        });
    }
    EntradaInventarioResponse entrada(Fixture f, Lote lote, int n) {
        return entradas.registrarEntrada(new EntradaInventarioRequest(lote.getId(), f.origen().getId(), n, "Observación no auditada"));
    }
    int stock(Long lote, Long almacen) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(cantidad),0) FROM dbo.existencias WHERE lote_id=? AND almacen_id=?", Integer.class, lote, almacen);
    }
    long eventos(String accion) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.eventos_auditoria WHERE usuario_id=? AND accion=?", Long.class, actor.getId(), accion);
    }
    Map<String,Object> evento(String accion) {
        return jdbc.queryForMap("SELECT TOP (1) * FROM dbo.eventos_auditoria WHERE usuario_id=? AND accion=? ORDER BY id DESC", actor.getId(), accion);
    }
    String clave() { return UUID.randomUUID().toString(); }

    @Test void entradaConfirmadaConservaActorMovimientoCantidadYUtc() {
        var f=fixture(); var result=entrada(f,f.primero(),10);
        var event=evento("ENTRADA_REGISTRADA");
        assertEquals(1, eventos("ENTRADA_REGISTRADA"));
        assertEquals(result.movimientoId(), ((Number)event.get("entidad_id")).longValue());
        assertEquals(actor.getCorreo(), event.get("usuario_identificador"));
        assertEquals(f.origen().getId(), ((Number)event.get("almacen_id")).longValue());
        var detail=json.readTree((String)event.get("detalles"));
        assertEquals(f.producto().getId().longValue(),detail.get("productoId").asLong());
        assertEquals(10,detail.get("cantidad").asInt());
        assertEquals(0, jdbc.queryForObject("SELECT DATEPART(TZOFFSET,fecha) FROM dbo.eventos_auditoria WHERE id=?", Integer.class, event.get("id")));
        assertEquals(10, stock(f.primero().getId(),f.origen().getId()));
    }
    @Test void salidaFefoAuditaCadaLoteSinCambiarElOrden() {
        var f=fixture(); entrada(f,f.primero(),3); entrada(f,f.segundo(),5);
        var response=salidas.registrar(clave(),new SalidaInventarioRequest(f.producto().getId(),f.origen().getId(),5,null));
        assertEquals(List.of(f.primero().getId(),f.segundo().getId()),response.detalles().stream().map(DetalleSalidaResponse::loteId).toList());
        assertEquals(List.of(3,2),response.detalles().stream().map(DetalleSalidaResponse::cantidadRetirada).toList());
        assertEquals(2,eventos("SALIDA_REGISTRADA"));
        assertEquals(0,stock(f.primero().getId(),f.origen().getId()));
        assertEquals(3,stock(f.segundo().getId(),f.origen().getId()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.eventos_auditoria a JOIN dbo.movimientos_inventario m ON m.id=a.entidad_id WHERE a.usuario_id=? AND a.accion='SALIDA_REGISTRADA' AND m.tipo='SALIDA' AND m.cantidad=TRY_CONVERT(int,JSON_VALUE(a.detalles,'$.cantidad'))", Integer.class, actor.getId()));
    }
    @Test void transferenciaConservaOrigenDestinoYNoDuplicaAlReintentar() {
        var f=fixture(); entrada(f,f.primero(),10);
        String key=clave(); var request=new TransferenciaRequest(f.producto().getId(),f.origen().getId(),f.destino().getId(),4,null);
        var first=transferencias.registrar(key,request); var second=transferencias.registrar(key,request);
        assertEquals(first,second);
        assertEquals(1,eventos("TRANSFERENCIA_REGISTRADA"));
        assertEquals(1,eventos("SALIDA_REGISTRADA"));
        assertEquals(2,eventos("ENTRADA_REGISTRADA"));
        var event=evento("TRANSFERENCIA_REGISTRADA");
        assertEquals(first.transferenciaId(),((Number)event.get("entidad_id")).longValue());
        var detail=json.readTree((String)event.get("detalles"));
        assertEquals(f.origen().getId().longValue(),detail.get("origenId").asLong());
        assertEquals(f.destino().getId().longValue(),detail.get("destinoId").asLong());
        assertEquals(4,stock(f.primero().getId(),f.destino().getId()));
        assertEquals(6,stock(f.primero().getId(),f.origen().getId()));
    }
    @Test void repetirSalidaIdempotenteNoDuplicaMovimientosNiEventos() {
        var f=fixture(); entrada(f,f.primero(),10);
        var request=new SalidaInventarioRequest(f.producto().getId(),f.origen().getId(),3,null); String key=clave();
        assertEquals(salidas.registrar(key,request),salidas.registrar(key,request));
        assertEquals(1,eventos("SALIDA_REGISTRADA"));
        assertEquals(7,stock(f.primero().getId(),f.origen().getId()));
        assertThrows(ResponseStatusException.class, () -> salidas.registrar(key,
                new SalidaInventarioRequest(f.producto().getId(),f.origen().getId(),4,null)));
        assertEquals(1,eventos("SALIDA_REGISTRADA"));
    }
    @Test void rollbackPosteriorAlInsertRevierteEventoYMovimiento() {
        var f=fixture();
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            entrada(f,f.primero(),10); em.flush();
            assertEquals(1,eventos("ENTRADA_REGISTRADA"));
            throw new IllegalStateException("Rollback deliberado después de escribir en SQL");
        }));
        assertEquals(0,eventos("ENTRADA_REGISTRADA"));
        assertEquals(0,stock(f.primero().getId(),f.origen().getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.movimientos_inventario WHERE lote_id=?",Integer.class,f.primero().getId()));
    }
    @Test void falloObligatorioAlAuditarTransferenciaRevierteTodo() {
        var f=fixture(); entrada(f,f.primero(),10);
        doAnswer(call -> { call.callRealMethod(); em.flush(); throw new IllegalStateException("Fallo de auditoría controlado"); })
                .when(org.springframework.test.util.AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria)).registrar(eq(TRANSFERENCIA_REGISTRADA),anyLong(),anyLong(),any());
        assertThrows(IllegalStateException.class, () -> transferencias.registrar(clave(),
                new TransferenciaRequest(f.producto().getId(),f.origen().getId(),f.destino().getId(),4,null)));
        assertEquals(10,stock(f.primero().getId(),f.origen().getId()));
        assertEquals(0,stock(f.primero().getId(),f.destino().getId()));
        assertEquals(0,eventos("SALIDA_REGISTRADA"));
        assertEquals(1,eventos("ENTRADA_REGISTRADA"));
        assertEquals(0,eventos("TRANSFERENCIA_REGISTRADA"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.transferencias WHERE producto_id=?",Integer.class,f.producto().getId()));
    }
    @Test void falloSqlAlInsertarAuditoriaNoConfirmaEntrada() {
        var f=fixture();
        doAnswer(call -> {
            // Viola CK_eventos_auditoria_json mediante SQL real en la MISMA transacción.
            jdbc.update("INSERT INTO dbo.eventos_auditoria(fecha,usuario_id,usuario_identificador,accion,entidad,entidad_id,descripcion,detalles) VALUES(TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'),?,'fixture@example.invalid','ENTRADA_REGISTRADA','MOVIMIENTO_INVENTARIO',1,N'Prueba','json-invalido')",actor.getId());
            return null;
        }).when(org.springframework.test.util.AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria)).registrar(eq(ENTRADA_REGISTRADA),anyLong(),anyLong(),any());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> entrada(f,f.primero(),5));
        assertEquals(0,stock(f.primero().getId(),f.origen().getId()));
        assertEquals(0,eventos("ENTRADA_REGISTRADA"));
    }
    @Test void auditoriaExigeUnaTransaccionExistente() {
        assertThrows(IllegalTransactionStateException.class, () -> auditoria.registrar(PRODUCTO_CREADO,1L,null,null));
    }
    @Test void stockInsuficienteNoProduceAuditoriaDeExito() {
        var f=fixture(); entrada(f,f.primero(),2);
        assertThrows(ResponseStatusException.class, () -> salidas.registrar(clave(),
                new SalidaInventarioRequest(f.producto().getId(),f.origen().getId(),3,null)));
        assertEquals(0,eventos("SALIDA_REGISTRADA"));
        assertEquals(2,stock(f.primero().getId(),f.origen().getId()));
    }
    @Test void creacionEstadoYRolesDeUsuarioSeAuditanSinCredenciales() {
        String password=UUID.randomUUID().toString();
        var target=usuarios.crearEmpleado(new CrearUsuarioRequest("Prueba", "Auditoría", "fase13-"+clave()+"@example.invalid", password));
        usuarios.actualizarRoles(target.id(),new ActualizarRolesRequest(Set.of("ENCARGADO_ALMACEN")),actor.getCorreo());
        usuarios.actualizarRoles(target.id(),new ActualizarRolesRequest(Set.of("ENCARGADO_ALMACEN")),actor.getCorreo());
        usuarios.cambiarEstado(target.id(),false,actor.getCorreo());
        usuarios.cambiarEstado(target.id(),false,actor.getCorreo());
        usuarios.cambiarEstado(target.id(),true,actor.getCorreo());
        assertEquals(1,eventos("USUARIO_CREADO")); assertEquals(1,eventos("ROLES_MODIFICADOS"));
        assertEquals(1,eventos("USUARIO_DESACTIVADO")); assertEquals(1,eventos("USUARIO_ACTIVADO"));
        var detail=json.readTree((String)evento("ROLES_MODIFICADOS").get("detalles"));
        assertEquals("EMPLEADO",detail.get("antes").get(0).asString());
        assertEquals("ENCARGADO_ALMACEN",detail.get("despues").get(0).asString());
        String output=json.writeValueAsString(consulta.listar(0,100,actor.getId(),null,null,null,null,null,null));
        for (String secret:List.of(password,HASH,"password", "refreshToken", "accessToken", "token_hash")) assertFalse(output.contains(secret));
    }
    @Test void operacionesDeCatalogosYProductosQuedanAuditadas() {
        String code=clave().substring(0,16);
        var c=categorias.registrar(new CategoryRequest("F13-C-"+code));
        categorias.actualizar(c.id(),new CategoryRequest("F13-C2-"+code));
        var a=almacenes.registrar(new AlmacenRequest(code,"F13-A-"+code,"Prueba"));
        almacenes.actualizar(a.id(),new AlmacenRequest(code,"F13-A2-"+code,"Prueba"));
        almacenes.desactivar(a.id()); almacenes.desactivar(a.id());
        var p=productos.registrar(new ProductRequest(code,"F13-P-"+code,"Texto libre no auditado",BigDecimal.ONE,BigDecimal.TEN,c.id()));
        productos.actualizar(p.id(),new ProductRequest(code,"F13-P2-"+code,null,BigDecimal.TEN,BigDecimal.TEN,c.id()));
        lotes.registrar(new LoteRequest(p.id(),"F13-L-"+code,LocalDate.now().plusDays(30)));
        productos.desactivar(p.id()); productos.desactivar(p.id());
        String ruc="20"+String.format("%09d", new SecureRandom().nextInt(1_000_000_000));
        var provider=proveedores.registrar(new ProveedorRequest(ruc,"F13-S-"+code,null,null));
        proveedores.actualizar(provider.id(),new ProveedorUpdateRequest("F13-S2-"+code,null,null));
        proveedores.desactivar(provider.id()); proveedores.desactivar(provider.id());
        for (String action:List.of("CATEGORIA_CREADA","CATEGORIA_ACTUALIZADA","ALMACEN_CREADO","ALMACEN_ACTUALIZADO",
                "ALMACEN_DESACTIVADO","PRODUCTO_CREADO","PRODUCTO_ACTUALIZADO","PRODUCTO_DESACTIVADO","LOTE_CREADO",
                "PROVEEDOR_CREADO","PROVEEDOR_ACTUALIZADO","PROVEEDOR_DESACTIVADO")) assertEquals(1,eventos(action),action);
        var change=json.readTree((String)evento("PRODUCTO_ACTUALIZADO").get("detalles"));
        assertEquals(1,change.get("antes").get("precioCompra").asInt());
        assertEquals(10,change.get("despues").get("precioCompra").asInt());
    }
    @Test void filtrosSeCombinanYLaPaginacionEsEstable() {
        var f=fixture(); var start=OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        for (int i=0;i<3;i++) entrada(f,f.primero(),1);
        var page=consulta.listar(0,2,actor.getId(),ENTRADA_REGISTRADA,
                com.lmmedical.inventory.enums.EntidadAuditoria.MOVIMIENTO_INVENTARIO,null,f.origen().getId(),start,OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1));
        assertEquals(3,page.totalElementos()); assertEquals(2,page.eventos().size());
        assertTrue(page.eventos().get(0).id()>page.eventos().get(1).id());
        var last=consulta.listar(1,2,actor.getId(),ENTRADA_REGISTRADA,null,null,null,null,null);
        assertEquals(1,last.eventos().size());
        assertEquals(0,consulta.listar(0,20,actor.getId(),ENTRADA_REGISTRADA,null,null,f.destino().getId(),null,null).totalElementos());
        var event=page.eventos().get(0);
        assertEquals(event.id(),consulta.buscar(event.id()).id());
        assertEquals(1,consulta.listar(0,20,actor.getId(),ENTRADA_REGISTRADA,null,event.entidadId(),null,null,null).totalElementos());
    }
    String login(Usuario user) throws Exception {
        // Las llamadas de servicio usan un contexto explícito; HTTP debe empezar sin ese contexto de prueba.
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();
        var response=mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(user.getCorreo(),PASSWORD)))).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        return json.readValue(response.getContentAsString(),AuthTokenResponse.class).accessToken();
    }
    int getStatus(String token,String path) throws Exception {
        return mvc.perform(get(path).header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus();
    }
    @Test void adminRealConsultaYLaSeguridadRevalidaRolesEstadoYSesion() throws Exception {
        var f=fixture(); entrada(f,f.primero(),1); Long id=((Number)evento("ENTRADA_REGISTRADA").get("id")).longValue();
        String token=login(actor);
        assertEquals(200,getStatus(token,"/api/auditoria?usuarioId="+actor.getId()+"&size=1"));
        assertEquals(200,getStatus(token,"/api/auditoria/"+id));
        var employee=usuarioFixture("EMPLEADO"); var manager=usuarioFixture("ENCARGADO_ALMACEN");
        for (Usuario user:List.of(employee,manager)) {
            String other=login(user); assertEquals(403,getStatus(other,"/api/auditoria")); assertEquals(403,getStatus(other,"/api/auditoria/"+id));
        }
        guard();
        // Solo fixture propio: perder ADMIN invalida acceso aunque el JWT se emitió antes.
        jdbc.update("UPDATE dbo.usuario_rol SET rol_id=(SELECT id FROM dbo.roles WHERE codigo='EMPLEADO') WHERE usuario_id=?",actor.getId());
        assertEquals(403,getStatus(token,"/api/auditoria"));
        jdbc.update("UPDATE dbo.usuarios SET activo=0 WHERE id=?",actor.getId());
        assertEquals(401,getStatus(token,"/api/auditoria"));
    }
    @Test void historialNoPermiteBorradoJpaNiEscrituraSqlConCuentaCi() {
        var f=fixture(); entrada(f,f.primero(),1); Long id=((Number)evento("ENTRADA_REGISTRADA").get("id")).longValue();
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> em.remove(em.find(EventoAuditoria.class,id))));
        assertEquals(1,eventos("ENTRADA_REGISTRADA"));
        if (CiSqlServer.target(System.getenv()).isPresent()) {
            assertEquals(0,jdbc.queryForObject("SELECT HAS_PERMS_BY_NAME('dbo.eventos_auditoria','OBJECT','UPDATE')",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT HAS_PERMS_BY_NAME('dbo.eventos_auditoria','OBJECT','DELETE')",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT HAS_PERMS_BY_NAME('dbo.eventos_auditoria','OBJECT','INSERT')",Integer.class));
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void salidasConcurrentesNoSobrevendenNiDuplicanAuditoria(int iteration) throws Exception {
        var f=fixture(); entrada(f,f.primero(),10);
        var start=new CyclicBarrier(2); var actorForThread=actor;
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=() -> {
                autenticar(actorForThread);
                try {
                    start.await(15,TimeUnit.SECONDS);
                    // Servicio sin clave: enfoca la carrera del bloqueo de lotes compartido por toda salida.
                    salidaDirecta.registrarSalida(new SalidaInventarioRequest(f.producto().getId(),f.origen().getId(),7,null));
                    return 200;
                } catch(ResponseStatusException ex) { return ex.getStatusCode().value(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a=executor.submit(attempt); var b=executor.submit(attempt);
            var results=new ArrayList<>(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))); Collections.sort(results);
            assertEquals(List.of(200,409),results);
        }
        assertEquals(3,stock(f.primero().getId(),f.origen().getId()));
        assertEquals(1,eventos("SALIDA_REGISTRADA"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.movimientos_inventario WHERE lote_id=? AND tipo='SALIDA'",Integer.class,f.primero().getId()));
    }
    @Autowired SalidaInventarioService salidaDirecta;
    @Test void salidaIdempotenteConcurrenteConfirmaUnSoloEvento() throws Exception {
        var f=fixture(); entrada(f,f.primero(),10); String key=clave(); var start=new CyclicBarrier(2); var threadActor=actor;
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<SalidaInventarioResponse> attempt=() -> {
                autenticar(threadActor);
                try { start.await(15,TimeUnit.SECONDS); return salidas.registrar(key,
                        new SalidaInventarioRequest(f.producto().getId(),f.origen().getId(),3,null)); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a=executor.submit(attempt); var b=executor.submit(attempt);
            assertEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
        assertEquals(7,stock(f.primero().getId(),f.origen().getId()));
        assertEquals(1,eventos("SALIDA_REGISTRADA"));
    }
}
