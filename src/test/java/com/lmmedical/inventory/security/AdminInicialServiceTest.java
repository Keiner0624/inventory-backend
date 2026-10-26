package com.lmmedical.inventory.security;

import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminInicialServiceTest {
    private static ValidatorFactory validation;
    private UsuarioRepository users;
    private RolRepository roles;
    private UsuarioRolRepository assignments;
    private JdbcTemplate jdbc;
    private PlatformTransactionManager tx;
    private PasswordEncoder encoder;
    private AdminInicialService service;
    private String password;
    private Rol admin;

    @BeforeAll static void openValidator() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { validation.close(); }

    @BeforeEach void setup() {
        users = mock(UsuarioRepository.class);
        roles = mock(RolRepository.class);
        assignments = mock(UsuarioRolRepository.class);
        jdbc = mock(JdbcTemplate.class);
        tx = mock(PlatformTransactionManager.class);
        encoder = new PasswordEncoderConfig().passwordEncoder();
        password = UUID.randomUUID().toString();
        when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForObject("SELECT DB_NAME()", String.class)).thenReturn("LM_Medical_supplies");
        when(jdbc.queryForObject(AdminInicialService.LOCK_SQL, Integer.class)).thenReturn(0);
        when(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.usuarios", Long.class)).thenReturn(0L);
        when(jdbc.queryForObject("SELECT @@TRANCOUNT", Integer.class)).thenReturn(1);
        admin = new Rol("ADMIN", "Administrador");
        ReflectionTestUtils.setField(admin, "id", 1L);
        when(roles.findByCodigo("ADMIN")).thenReturn(Optional.of(admin));
        when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            Usuario user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 7L);
            return user;
        });
        var target = new AdminInicialService(users, roles, assignments, encoder, jdbc, validation.getValidator());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        service = (AdminInicialService) proxy.getProxy();
    }

    private boolean create() { return service.crear("LM_Medical_supplies", "Ana", "Pérez", " ADMIN@EXAMPLE.TEST ", password); }

    @Test void creaUsuarioConBcryptYAsignaAdminEnUnaTransaccion() {
        assertTrue(create());
        var user = ArgumentCaptor.forClass(Usuario.class);
        verify(users).saveAndFlush(user.capture());
        assertEquals("admin@example.test", user.getValue().getCorreo());
        assertTrue(user.getValue().getPasswordHash().startsWith("{bcrypt}$2"));
        assertTrue(encoder.matches(password, user.getValue().getPasswordHash()));
        var assignment = ArgumentCaptor.forClass(UsuarioRol.class);
        verify(assignments).saveAndFlush(assignment.capture());
        assertEquals(7L, assignment.getValue().getId().getUsuarioId());
        assertEquals(1L, assignment.getValue().getId().getRolId());
        var order = inOrder(jdbc, assignments, users);
        order.verify(jdbc).queryForObject("SELECT DB_NAME()", String.class);
        order.verify(jdbc).queryForObject(AdminInicialService.LOCK_SQL, Integer.class);
        order.verify(assignments).existsByRol_Codigo("ADMIN");
        order.verify(users).existsByCorreo("admin@example.test");
        order.verify(users).saveAndFlush(any());
        order.verify(assignments).saveAndFlush(any());
        verify(tx).commit(any());
        verify(tx, never()).rollback(any());
    }

    @Test void creaRolAdminSoloSiFalta() {
        when(roles.findByCodigo("ADMIN")).thenReturn(Optional.empty());
        when(roles.saveAndFlush(any())).thenAnswer(invocation -> {
            Rol role = invocation.getArgument(0);
            ReflectionTestUtils.setField(role, "id", 2L);
            return role;
        });
        assertTrue(create());
        verify(roles).saveAndFlush(any(Rol.class));
    }

    @Test void informaVariablesFaltantesSinMostrarValores() {
        var error = assertThrows(IllegalArgumentException.class, () -> service.crear("LM_Medical_supplies", "", "", "", ""));
        for (String name : new String[]{"NOMBRE", "APELLIDO", "CORREO", "PASSWORD"}) assertTrue(error.getMessage().contains("BOOTSTRAP_ADMIN_" + name));
        verifyNoInteractions(jdbc, users, roles, assignments);
    }

    @Test void rechazaConfirmacionDeOtraBase() {
        assertThrows(IllegalStateException.class, () -> service.crear("LM_Medical_Test", "Ana", "Pérez", "a@example.test", password));
        verifyNoInteractions(jdbc, users, roles, assignments);
    }

    @Test void rechazaConexionRealAUnaBaseNoAutorizada() {
        when(jdbc.queryForObject("SELECT DB_NAME()", String.class)).thenReturn("LM_Medical_Test");
        assertThrows(IllegalStateException.class, this::create);
        verify(jdbc, never()).queryForObject(AdminInicialService.LOCK_SQL, Integer.class);
        verifyNoInteractions(users, roles, assignments);
    }

    @Test void administradorExistenteNoSeDuplicaNiSeModifica() {
        when(assignments.existsByRol_Codigo("ADMIN")).thenReturn(true);
        assertFalse(create());
        verifyNoInteractions(users, roles);
        verify(assignments, never()).saveAndFlush(any());
    }

    @Test void correoExistenteNoSePromueveNiSeReemplaza() {
        when(users.existsByCorreo("admin@example.test")).thenReturn(true);
        assertThrows(IllegalStateException.class, this::create);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(roles);
        verify(assignments, never()).saveAndFlush(any());
    }

    @Test void falloDeAsignacionSolicitaRollbackDeTodaLaOperacion() {
        doThrow(new IllegalStateException("Asignación fallida de prueba")).when(assignments).saveAndFlush(any());
        assertThrows(IllegalStateException.class, this::create);
        verify(users).saveAndFlush(any());
        verify(tx).rollback(any());
        verify(tx, never()).commit(any());
    }

    @Test void noEscribeSinTransaccionSqlActiva() {
        when(jdbc.queryForObject("SELECT @@TRANCOUNT", Integer.class)).thenReturn(0);
        assertThrows(IllegalStateException.class, this::create);
        verifyNoInteractions(users, roles, assignments);
    }

    @Test void noEscribeSiNoPuedeObtenerElBloqueo() {
        when(jdbc.queryForObject(AdminInicialService.LOCK_SQL, Integer.class)).thenReturn(-1);
        assertThrows(IllegalStateException.class, this::create);
        verifyNoInteractions(users, roles, assignments);
    }

    @ParameterizedTest @ValueSource(strings = {"corta", "áááááááááááááááááááááááááááááááááááááááá"})
    void rechazaPasswordIncompatibleConLaPolitica(String value) {
        password = value;
        assertThrows(IllegalArgumentException.class, this::create);
        verifyNoInteractions(jdbc, users, roles, assignments);
    }

    @Test void validaDatosAntesDePersistirElUsuarioORol() {
        assertThrows(IllegalArgumentException.class, () -> service.crear("LM_Medical_supplies", "Ana", "Pérez", "sin-correo", password));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(roles);
        verify(assignments, never()).saveAndFlush(any());
    }
}
