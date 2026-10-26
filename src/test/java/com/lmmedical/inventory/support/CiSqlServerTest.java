package com.lmmedical.inventory.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CiSqlServerTest {
    private static final String MARKER = "a".repeat(48);
    private Map<String, String> environment() {
        return new HashMap<>(Map.of("GITHUB_ACTIONS", "true", "RUNNER_ENVIRONMENT", "github-hosted",
                "CI_SQLSERVER_PORT", "32199", "CI_SQLSERVER_MARKER", MARKER));
    }

    @Test void ejecucionLocalConservaSuConfiguracion() {
        assertTrue(CiSqlServer.target(Map.of()).isEmpty());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CiSqlServer.verify(jdbc, Map.of());
        verifyNoInteractions(jdbc);
    }

    @Test void githubSinDestinoNoRecurreALocalhost1433() {
        var env = environment();
        env.remove("CI_SQLSERVER_PORT");
        assertThrows(IllegalStateException.class, () -> CiSqlServer.target(env));
    }

    @ParameterizedTest @ValueSource(strings={"1434", "32199", "65535"})
    void aceptaSoloPuertoAisladoEnLoopback(String port) {
        var env = environment(); env.put("CI_SQLSERVER_PORT", port);
        var target = CiSqlServer.target(env).orElseThrow();
        assertTrue(target.url().startsWith("jdbc:sqlserver://127.0.0.1:"+port+";databaseName=LM_Medical_Test;"));
    }

    @ParameterizedTest @ValueSource(strings={"1433", "80", "0000", "65536", "no", "1434;databaseName=LM_Medical_supplies"})
    void rechazaPuertosLocalesOConfiguracionesInyectadas(String port) {
        var env = environment(); env.put("CI_SQLSERVER_PORT", port);
        assertThrows(IllegalStateException.class, () -> CiSqlServer.target(env));
    }

    @Test void exigeLaMarcaDeEstaEjecucion() {
        var env = environment(); env.remove("CI_SQLSERVER_MARKER");
        assertThrows(IllegalStateException.class, () -> CiSqlServer.target(env));
    }

    @Test void noAdmiteUnRunnerAutohospedado() {
        var env = environment(); env.put("RUNNER_ENVIRONMENT", "self-hosted");
        assertThrows(IllegalStateException.class, () -> CiSqlServer.target(env));
    }

    @Test void compruebaMarcaYNombreDelServidorAntesDeEscribir() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("extended_properties"), eq(String.class))).thenReturn(MARKER);
        when(jdbc.queryForObject(contains("MachineName"), eq(String.class))).thenReturn("lmmedical-ci");
        assertDoesNotThrow(() -> CiSqlServer.verify(jdbc, environment()));
        when(jdbc.queryForObject(contains("extended_properties"), eq(String.class))).thenReturn("otra");
        assertThrows(IllegalStateException.class, () -> CiSqlServer.verify(jdbc, environment()));
    }
}
