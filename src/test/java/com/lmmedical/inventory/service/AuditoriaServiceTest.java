package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import com.lmmedical.inventory.entity.*;
import com.lmmedical.inventory.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.ZoneOffset;
import java.util.*;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditoriaServiceTest {
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    EntityManager em = mock(EntityManager.class);
    JsonMapper json = JsonMapper.builder().build();
    AuditoriaService service = new AuditoriaService(usuarios, em, json);
    Usuario actor;
    @BeforeEach void setup() {
        actor = new Usuario("Prueba", "Auditoría", "actor@example.invalid", "{bcrypt}$2a$12$" + "A".repeat(53));
        ReflectionTestUtils.setField(actor, "id", 7L);
        when(usuarios.findByCorreo(actor.getCorreo())).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor.getCorreo(), "credencial-no-publicable", List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void capturaIdentidadDelContextoFechaUtcYDetallesSeleccionados() {
        service.registrar(ENTRADA_REGISTRADA, 4L, 2L, new DetalleAuditoria.Movimiento(1L, 3L, 10));
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(em).persist(captor.capture());
        var evento = captor.getValue();
        assertEquals(7L, evento.getUsuarioId());
        assertEquals(actor.getCorreo(), evento.getUsuarioIdentificador());
        assertEquals(ZoneOffset.UTC, evento.getFecha().getOffset());
        assertEquals(4L, evento.getEntidadId());
        assertEquals(2L, evento.getAlmacenId());
        assertEquals(10, json.readTree(evento.getDetalles()).get("cantidad").asInt());
        String contenido = json.writeValueAsString(evento);
        for (String prohibido : List.of("password", "token", "hash-no-publicable", "credencial-no-publicable"))
            assertFalse(contenido.contains(prohibido));
    }
    @Test void rechazaLaAusenciaDeActorSinInventarUnUsuarioSistema() {
        SecurityContextHolder.clearContext();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.registrar(PRODUCTO_CREADO, 1L, null, null)).getStatusCode().value());
        verifyNoInteractions(em);
    }
    @Test void rechazaUnUsuarioDesactivado() {
        actor.cambiarEstado(false);
        assertThrows(ResponseStatusException.class, () -> service.registrar(PRODUCTO_CREADO, 1L, null, null));
        verifyNoInteractions(em);
    }
    @Test void rechazaAutenticacionAnonima() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThrows(ResponseStatusException.class, () -> service.registrar(PRODUCTO_CREADO, 1L, null, null));
        verifyNoInteractions(em);
    }
    @Test void propagaElFalloDePersistenciaObligatoria() {
        doThrow(new IllegalStateException("fallo controlado")).when(em).persist(any());
        assertThrows(IllegalStateException.class, () -> service.registrar(PRODUCTO_CREADO, 1L, null, null));
    }
    @Test void losRolesNoAceptanValoresLibresYSeCopian() {
        assertThrows(IllegalArgumentException.class, () -> new DetalleAuditoria.Roles(List.of(), List.of("secreto")));
        var lista = new ArrayList<>(List.of("EMPLEADO"));
        var roles = new DetalleAuditoria.Roles(List.of(), lista);
        lista.clear();
        assertEquals(List.of("EMPLEADO"), roles.despues());
    }
}
