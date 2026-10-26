package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.repository.UsuarioRolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="USUARIOS_READONLY_TESTS", matches="true")
@SpringBootTest(properties={"app.bootstrap.admin.enabled=false", "app.bootstrap.admin.authorized=false",
        "app.jwt.enabled=true", "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional(readOnly=true)
class UsuariosConsultaSqlServerTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRolRepository roles;
    @Autowired MockMvc mvc;

    @BeforeEach void comprobarDestinoAntesDeConsultar() {
        assertEquals("LM_Medical_supplies", jdbc.queryForObject("SELECT DB_NAME()", String.class));
    }

    @Test void enlazaLosIdsYRecuperaSoloSusAsignaciones() {
        Long id = jdbc.queryForObject("SELECT TOP(1) ur.usuario_id FROM dbo.usuario_rol ur JOIN dbo.roles r ON r.id=ur.rol_id WHERE r.codigo='ADMIN' ORDER BY ur.usuario_id", Long.class);
        var assignments = roles.findByUsuarioIdsWithRoles(List.of(id));
        assertFalse(assignments.isEmpty());
        assertTrue(assignments.stream().allMatch(a -> a.getId().getUsuarioId().equals(id)));
        assertTrue(assignments.stream().anyMatch(a -> a.getRol().getCodigo().equals("ADMIN")));
        assertTrue(roles.findByUsuarioIdsWithRoles(List.of(Long.MAX_VALUE)).isEmpty());
    }

    @Test void listadoDeUsuariosDevuelve200ConRolesSinHashes() throws Exception {
        mvc.perform(get("/api/usuarios?page=0&size=20").with(user("verificacion-local").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarios").isArray())
                .andExpect(jsonPath("$.totalElementos").isNumber()).andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.usuarios[0].roles").isArray())
                .andExpect(jsonPath("$.usuarios[0].passwordHash").doesNotExist());
    }

    @Test void paginaDeProductosUsaLaRepresentacionEstable() throws Exception {
        mvc.perform(get("/api/productos?page=0&size=1").with(user("verificacion-local").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(1)).andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").isNumber()).andExpect(jsonPath("$.page.totalPages").isNumber());
    }

    @Test void unCampoDeOrdenamientoInexistenteDevuelve400() throws Exception {
        mvc.perform(get("/api/usuarios?sort=campoQueNoExiste").with(user("verificacion-local").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test void mantieneElLimiteDePaginacionDeUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios?size=101").with(user("verificacion-local").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }
}
