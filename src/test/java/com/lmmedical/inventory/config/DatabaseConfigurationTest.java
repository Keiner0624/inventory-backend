package com.lmmedical.inventory.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigurationTest {
    private MockEnvironment environment(String profile, String database) {
        var env = new MockEnvironment().withProperty("DB_USERNAME", "usuario-prueba")
                .withProperty("DB_PASSWORD", "valor-de-prueba")
                .withProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=" + database);
        env.setActiveProfiles(profile);
        return env;
    }

    @ParameterizedTest
    @CsvSource({"dev,LM_Medical_supplies", "test,LM_Medical_Test", "flyway-check,LM_Medical_Flyway_Prueba"})
    void permiteLaBaseCorrespondienteAlPerfil(String profile, String database) {
        assertDoesNotThrow(() -> DatabaseConfiguration.validate(environment(profile, database)));
    }

    @Test
    void rechazaCredencialesAusentesConUnMensajeSinSecretos() {
        var env = environment("test", "LM_Medical_Test");
        env.getPropertySources().remove("mockProperties");
        env.withProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=LM_Medical_Test");
        assertTrue(assertThrows(IllegalStateException.class,
                () -> DatabaseConfiguration.validate(env)).getMessage().contains("DB_USERNAME"));
    }

    @Test
    void rechazaUnaVariableSinResolver() {
        var env = environment("test", "LM_Medical_Test").withProperty("DB_PASSWORD", "${SIN_CONFIGURAR}");
        assertThrows(IllegalStateException.class, () -> DatabaseConfiguration.validate(env));
    }

    @Test
    void rechazaLaBaseDeOtroPerfil() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseConfiguration.validate(environment("test", "LM_Medical_supplies")));
    }

    @Test
    void rechazaDosPerfilesDeBaseActivos() {
        var env = environment("test", "LM_Medical_Test");
        env.setActiveProfiles("dev", "test");
        assertThrows(IllegalStateException.class, () -> DatabaseConfiguration.validate(env));
    }

    @Test
    void rechazaUnDataSourceIndependienteParaFlyway() {
        var env = environment("test", "LM_Medical_Test").withProperty("spring.flyway.url", "otra-conexion");
        assertThrows(IllegalStateException.class, () -> DatabaseConfiguration.validate(env));
    }

    @Test
    void rechazaElBaselineAutomatico() {
        var env = environment("test", "LM_Medical_Test").withProperty("spring.flyway.baseline-on-migrate", "true");
        assertThrows(IllegalStateException.class, () -> DatabaseConfiguration.validate(env));
    }
}
