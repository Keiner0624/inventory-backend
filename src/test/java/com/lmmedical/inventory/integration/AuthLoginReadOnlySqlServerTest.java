package com.lmmedical.inventory.integration;

import com.lmmedical.inventory.dto.auth.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BOOTSTRAP_READONLY_HTTP_TESTS", matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.bootstrap.admin.enabled=false", "app.bootstrap.admin.authorized=false",
        "app.jwt.enabled=true", "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=validate", "server.address=127.0.0.1"})
@ActiveProfiles("dev")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AuthLoginReadOnlySqlServerTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Value("${local.server.port}") int port;

    @Test void loginRealPorHttpRechazaUsuarioInexistenteSinEscrituras() throws Exception {
        assertEquals("LM_Medical_supplies", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        assertFalse(environment.getProperty("app.bootstrap.admin.enabled", Boolean.class, true));
        assertFalse(environment.getProperty("spring.flyway.enabled", Boolean.class, true));
        long before = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.usuarios", Long.class);
        String email = UUID.randomUUID() + "@example.invalid";
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.usuarios WHERE correo=?", Integer.class, email));
        String body = JsonMapper.builder().build().writeValueAsString(new LoginRequest(email, UUID.randomUUID().toString()));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
            assertEquals("Credenciales invalidas", JsonMapper.builder().build().readTree(response.body()).get("error").asText());
        }
        assertEquals(before, jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.usuarios", Long.class));
    }
}
