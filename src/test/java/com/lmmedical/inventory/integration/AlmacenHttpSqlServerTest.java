package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.entity.Almacen;
import com.lmmedical.inventory.repository.AlmacenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AlmacenHttpSqlServerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AlmacenRepository almacenes;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void debeCrearUnAlmacenActivoConCodigoNuevo() throws Exception {
        assertEquals("LM_Medical_Test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String codigo = "ALM-" + sufijo.toUpperCase(java.util.Locale.ROOT);
        mockMvc.perform(post("/api/almacenes").with(user("prueba")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"codigo":" alm-%s ","nombre":" Almacén nuevo ","ubicacion":" Lima, Perú "}
                                """.formatted(sufijo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.nombre").value("Almacén nuevo"))
                .andExpect(jsonPath("$.ubicacion").value("Lima, Perú"))
                .andExpect(jsonPath("$.activo").value(true));
        assertTrue(almacenes.existsByCodigo(codigo));
    }

    @Test
    void unCodigoExistenteNoDebeDuplicarseAunqueElAlmacenEsteInactivo() throws Exception {
        String codigo = "ALM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        Almacen existente = new Almacen(codigo, "Almacén inactivo", "Lima");
        existente.desactivar();
        almacenes.saveAndFlush(existente);
        mockMvc.perform(post("/api/almacenes").with(user("prueba")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"codigo":"%s","nombre":"Otro almacén","ubicacion":"Lima"}
                                """.formatted(codigo.toLowerCase(java.util.Locale.ROOT))))
                .andExpect(status().isConflict());
    }

    @Test
    void debeIndicarQueLaUbicacionEsObligatoria() throws Exception {
        mockMvc.perform(post("/api/almacenes").with(user("prueba")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"codigo":"ALM-NUEVO","nombre":"Almacén nuevo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.ubicacion").value("La ubicación es obligatoria"));
    }
}
