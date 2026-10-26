package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.usuario.UsuarioConsultaResponse;
import com.lmmedical.inventory.exception.GlobalExceptionHandler;
import com.lmmedical.inventory.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

class UsuarioValidacionHttpTest {
    private UsuarioService service;
    private MockMvc mvc;
    private UsernamePasswordAuthenticationToken admin;

    @BeforeEach void setup() {
        service = mock(UsuarioService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        admin = new UsernamePasswordAuthenticationToken("admin@example.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @ParameterizedTest @ValueSource(booleans={true, false})
    void aceptaAmbosValoresBooleanSinErrorDeValidacion(boolean activo) throws Exception {
        when(service.cambiarEstado(7L, activo, "admin@example.test")).thenReturn(
                new UsuarioConsultaResponse(7L, "Ana", "Pérez", "empleado@example.test", activo, List.of("EMPLEADO")));
        var response = mvc.perform(patch("/api/usuarios/7/estado").principal(admin)
                .contentType("application/json").content("{\"activo\":" + activo + "}")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(service).cambiarEstado(7L, activo, "admin@example.test");
    }

    @ParameterizedTest @ValueSource(strings={"{}", "{\"activo\":null}"})
    void estadoAusenteONuloDevuelve400(String body) throws Exception {
        var response = mvc.perform(patch("/api/usuarios/7/estado").principal(admin)
                .contentType("application/json").content(body)).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("El estado activo es obligatorio"));
        verifyNoInteractions(service);
    }

    @Test void textoPlanoDevuelve415ConInstruccionesParaPostman() throws Exception {
        var response = mvc.perform(patch("/api/usuarios/7/estado").principal(admin)
                .contentType("text/plain;charset=UTF-8").content("{\"activo\":false}"))
                .andReturn().getResponse();
        assertEquals(415, response.getStatus());
        assertTrue(response.getContentAsString().contains("Content-Type: application/json"));
        verifyNoInteractions(service);
    }

    @Test void jsonMalFormadoDevuelve400SinExponerElCuerpo() throws Exception {
        var response = mvc.perform(patch("/api/usuarios/7/estado").principal(admin)
                .contentType("application/json").content("{\"activo\":"))
                .andReturn().getResponse();
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("JSON"));
        verifyNoInteractions(service);
    }

    @Test void textoNoBooleanoDevuelve400() throws Exception {
        var response = mvc.perform(patch("/api/usuarios/7/estado").principal(admin)
                .contentType("application/json").content("{\"activo\":\"incorrecto\"}"))
                .andReturn().getResponse();
        assertEquals(400, response.getStatus());
        verifyNoInteractions(service);
    }
}
