package com.lmmedical.inventory.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AdminInicialBootstraps implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInicialBootstraps.class);
    private final AdminInicialService service;
    private final Environment environment;

    public AdminInicialBootstraps(AdminInicialService service, Environment environment) {
        this.service = service;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.getProperty("app.bootstrap.admin.authorized", Boolean.class, false)) {
            throw new IllegalStateException("Bootstrap bloqueado: falta BOOTSTRAP_ADMIN_AUTHORIZED=true tras aprobar la escritura.");
        }
        if (!environment.getProperty("app.jwt.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Bootstrap bloqueado: activa JWT_ENABLED con una clave segura antes de crear la cuenta.");
        }
        boolean created = service.crear(
                environment.getProperty("app.bootstrap.admin.database", ""),
                environment.getProperty("app.bootstrap.admin.nombre", ""),
                environment.getProperty("app.bootstrap.admin.apellido", ""),
                environment.getProperty("app.bootstrap.admin.correo", ""),
                environment.getProperty("app.bootstrap.admin.password", ""));
        // El proxy transaccional ya confirmó el commit antes de llegar aquí.
        log.info(created ? "Administrador inicial creado en LM_Medical_supplies. Desactiva el bootstrap."
                : "Bootstrap omitido: ya existe un administrador. Desactiva el bootstrap.");
    }
}
