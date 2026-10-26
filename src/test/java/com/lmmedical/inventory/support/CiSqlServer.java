package com.lmmedical.inventory.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.util.Map;
import java.util.Optional;

/** Solo infraestructura de tests; nunca acepta URLs externas ni el puerto local 1433. */
public final class CiSqlServer {
    private CiSqlServer() { }

    public record Target(int port, String marker) {
        public String url() {
            return "jdbc:sqlserver://127.0.0.1:" + port
                    + ";databaseName=LM_Medical_Test;encrypt=true;trustServerCertificate=true";
        }
    }

    public static Optional<Target> target(Map<String, String> environment) {
        boolean github = "true".equals(environment.get("GITHUB_ACTIONS"));
        String port = environment.get("CI_SQLSERVER_PORT");
        if (!github && port == null) return Optional.empty();
        if (!github || !"github-hosted".equals(environment.get("RUNNER_ENVIRONMENT"))) {
            throw new IllegalStateException("El destino CI requiere un runner hospedado por GitHub");
        }
        if (port == null || !port.matches("[0-9]{4,5}")) throw new IllegalStateException("Falta un puerto CI valido");
        int number = Integer.parseInt(port);
        if (number < 1024 || number > 65535 || number == 1433) throw new IllegalStateException("Puerto CI no autorizado");
        String marker = environment.get("CI_SQLSERVER_MARKER");
        if (marker == null || !marker.matches("[0-9a-f]{48}")) throw new IllegalStateException("Falta la marca CI");
        return Optional.of(new Target(number, marker));
    }

    public static void configure(DynamicPropertyRegistry properties) {
        target(System.getenv()).ifPresent(target -> {
            properties.add("spring.datasource.url", target::url);
            properties.add("logging.level.org.hibernate.orm.connections.pooling", () -> "WARN");
        });
    }

    public static void verify(JdbcTemplate jdbc) {
        verify(jdbc, System.getenv());
    }

    static void verify(JdbcTemplate jdbc, Map<String, String> environment) {
        target(environment).ifPresent(target -> {
            String marker = jdbc.queryForObject("SELECT CONVERT(varchar(48),value) FROM sys.extended_properties "
                    + "WHERE class=0 AND name=N'lmmedical_ci_marker'", String.class);
            String server = jdbc.queryForObject("SELECT CONVERT(varchar(128),SERVERPROPERTY('MachineName'))", String.class);
            if (!target.marker().equals(marker) || !"lmmedical-ci".equals(server)) {
                throw new IllegalStateException("La base no pertenece al contenedor efimero de esta ejecucion");
            }
        });
    }
}
