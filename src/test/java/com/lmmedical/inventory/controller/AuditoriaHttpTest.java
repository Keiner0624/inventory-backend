package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.entity.EventoAuditoria;
import com.lmmedical.inventory.repository.EventoAuditoriaRepository;
import com.lmmedical.inventory.security.*;
import com.lmmedical.inventory.service.AuditoriaConsultaService;
import com.lmmedical.inventory.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AuditoriaHttpTest.Config.class)
@WebAppConfiguration
class AuditoriaHttpTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, AuditoriaController.class, AuditoriaConsultaService.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean EventoAuditoriaRepository eventos() { return mock(EventoAuditoriaRepository.class); }
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean RolesActualesJwtConverter converter() { return mock(RolesActualesJwtConverter.class); }
    }
    @Autowired WebApplicationContext context;
    @Autowired EventoAuditoriaRepository eventos;
    MockMvc mvc;
    @BeforeEach void setup() {
        reset(eventos);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(eventos.findAll(any(), any(Pageable.class))).thenAnswer(call ->
                new PageImpl<>(List.of(new EventoAuditoria(7L, "actor@example.invalid", PRODUCTO_CREADO, 4L, null, null)),
                        call.getArgument(1), 1));
    }
    @ParameterizedTest @ValueSource(strings={"EMPLEADO", "ENCARGADO_ALMACEN"})
    void rolesNoAdministrativosNoPuedenListarNiVerDetalle(String role) throws Exception {
        for (String path : List.of("/api/auditoria", "/api/auditoria/1"))
            mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_"+role))))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(eventos);
    }
    @Test void requiereAutenticacion() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
    }
    @Test void adminConsultaUnaPaginaLimitadaYOrdenada() throws Exception {
        mvc.perform(get("/api/auditoria?size=2&page=0").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tamanio").value(2))
                .andExpect(jsonPath("$.eventos[0].usuarioId").value(7))
                .andExpect(jsonPath("$.eventos[0].password").doesNotExist());
        verify(eventos).findAll(any(), eq(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "fecha", "id"))));
    }
    @ParameterizedTest @ValueSource(strings={"size=0", "size=101", "page=-1", "usuarioId=0", "entidadId=-1",
            "almacenId=0", "accion=DESCONOCIDA", "entidad=DESCONOCIDA", "desde=sin-fecha",
            "desde=2026-10-27T00:00:00Z&hasta=2026-10-26T00:00:00Z", "page=2147483647&size=100"})
    void filtrosInvalidosNoConsultanLaBase(String query) throws Exception {
        mvc.perform(get("/api/auditoria?"+query).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventos);
    }
    @Test void eventoInexistenteDevuelve404() throws Exception {
        when(eventos.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/auditoria/1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"POST", "PUT", "PATCH", "DELETE"})
    void noHayApiParaModificarHistorialNiComoAdmin(String method) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/auditoria/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventos);
    }
}
