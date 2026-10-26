package com.lmmedical.inventory.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfiguration {
    private static final Map<String, String> DATABASES = Map.of(
            "dev", "LM_Medical_supplies",
            "test", "LM_Medical_Test",
            "flyway-check", "LM_Medical_Flyway_Prueba");
    private static final Pattern DATABASE_NAME = Pattern.compile("(?:^|;)databaseName=([^;]+)", Pattern.CASE_INSENSITIVE);

    @Bean
    static BeanFactoryPostProcessor validateDatabaseConfiguration(Environment environment) {
        return beanFactory -> validate(environment);
    }

    static void validate(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        var databaseProfiles = Arrays.stream(profiles).filter(DATABASES::containsKey).toList();
        if (databaseProfiles.size() != 1) {
            throw new IllegalStateException("Activa exactamente un perfil de base de datos: dev, test o flyway-check.");
        }
        for (String name : new String[]{"DB_USERNAME", "DB_PASSWORD"}) {
            String value;
            try {
                value = environment.getProperty(name);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("No se puede resolver " + name + ". Configurala en el entorno del proceso.");
            }
            if (value == null || value.isBlank() || value.startsWith("${")) {
                throw new IllegalStateException("Falta " + name + ". Configurala en las variables de entorno de la configuracion de ejecucion de IntelliJ o del proceso.");
            }
        }
        var matcher = DATABASE_NAME.matcher(environment.getProperty("spring.datasource.url", ""));
        String profile = databaseProfiles.getFirst();
        if (!matcher.find() || !DATABASES.get(profile).equalsIgnoreCase(matcher.group(1))) {
            throw new IllegalStateException("El perfil " + profile + " debe apuntar a " + DATABASES.get(profile) + ". Revisa las sobrescrituras de spring.datasource.url.");
        }
        for (String property : new String[]{"spring.flyway.url", "spring.flyway.user", "spring.flyway.password"}) {
            if (environment.containsProperty(property)) {
                throw new IllegalStateException("Elimina " + property + ": Flyway debe reutilizar el DataSource de Spring Boot.");
            }
        }
        if (environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false)) {
            throw new IllegalStateException("El baseline debe ser explicito; baseline-on-migrate debe permanecer desactivado.");
        }
    }
}
