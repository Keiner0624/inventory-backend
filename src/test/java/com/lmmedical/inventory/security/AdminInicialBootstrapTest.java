package com.lmmedical.inventory.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminInicialBootstrapTest {
    private final AdminInicialService service = mock(AdminInicialService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AdminInicialService.class, () -> service)
            .withUserConfiguration(AdminInicialBootstraps.class);

    @Test void desactivadoPorDefecto() {
        runner.withPropertyValues("spring.profiles.active=dev").run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertEquals(0, ctx.getBeansOfType(AdminInicialBootstraps.class).size());
            verifyNoInteractions(service);
        });
    }

    @Test void propiedadEnabledCorrectaActivaElRunnerSoloEnDev() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.bootstrap.admin.enabled=true").run(ctx -> {
            assertEquals(1, ctx.getBeansOfType(AdminInicialBootstraps.class).size());
        });
        runner.withPropertyValues("spring.profiles.active=test", "app.bootstrap.admin.enabled=true").run(ctx ->
                assertEquals(0, ctx.getBeansOfType(AdminInicialBootstraps.class).size()));
    }

    @Test void nombreAnteriorEnableNoAutorizaLaEjecucion() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.bootstrap.admin.enable=true").run(ctx ->
                assertEquals(0, ctx.getBeansOfType(AdminInicialBootstraps.class).size()));
    }

    @Test void enabledNoSustituyeLaAprobacionDeEscritura() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.bootstrap.admin.enabled=true").run(ctx -> {
            assertThrows(IllegalStateException.class, () -> ctx.getBean(AdminInicialBootstraps.class).run(new DefaultApplicationArguments()));
            verifyNoInteractions(service);
        });
    }

    @Test void noCreaCuentaSiJwtEstaDesactivado() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.bootstrap.admin.enabled=true",
                "app.bootstrap.admin.authorized=true").run(ctx -> {
            assertThrows(IllegalStateException.class, () -> ctx.getBean(AdminInicialBootstraps.class).run(new DefaultApplicationArguments()));
            verifyNoInteractions(service);
        });
    }

    @Test void ejecutaSoloTrasConfirmarLaOperacion() {
        when(service.crear(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        runner.withPropertyValues("spring.profiles.active=dev", "app.bootstrap.admin.enabled=true",
                "app.bootstrap.admin.authorized=true", "app.jwt.enabled=true",
                "app.bootstrap.admin.database=LM_Medical_supplies").run(ctx -> {
            ctx.getBean(AdminInicialBootstraps.class).run(new DefaultApplicationArguments());
            verify(service).crear("LM_Medical_supplies", "", "", "", "");
        });
    }
}
