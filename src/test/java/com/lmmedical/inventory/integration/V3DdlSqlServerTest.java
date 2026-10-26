package com.lmmedical.inventory.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static com.lmmedical.inventory.integration.V2MigrationSqlServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "V3_SQLSERVER_TESTS", matches = "true")
class V3DdlSqlServerTest {
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private Connection connection;
    private String schema;

    @BeforeEach
    void isolatedSchema() throws Exception {
        connection = connect("LM_Medical_Flyway_Prueba");
        assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='2' AND type='SQL' AND success=1"));
        assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=0"));
        connection.setAutoCommit(false);
        schema = "seg_" + UUID.randomUUID().toString().replace("-", "");
        execute(connection, "CREATE SCHEMA [" + schema + "]");
        try (var stream = getClass().getResourceAsStream("/db/migration/V3__usuarios_roles_seguridad.sql")) {
            assertNotNull(stream);
            execute(connection, new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("dbo.", schema + "."));
        }
    }

    @AfterEach
    void rollback() throws Exception {
        if (connection != null) try { connection.rollback(); } finally { connection.close(); }
    }

    private void sql(String command, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(command.replace("dbo.", schema + "."))) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            boolean result = statement.execute();
            while (result || statement.getUpdateCount() != -1) result = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
        }
    }

    private void user() throws SQLException {
        sql("INSERT dbo.usuarios(nombre,apellido,correo,password_hash) VALUES(N'Lucía',N'Pérez',?,?)", "persona@example.test", HASH);
    }

    private String hash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
    }

    private void session(String id, long user, String tokenHash, String expires) throws SQLException {
        sql("INSERT dbo.sesiones_refresh(id,usuario_id,familia_id,token_hash,fecha_expiracion) VALUES(?,?,?,?,?)", id, user, UUID.randomUUID().toString(), tokenHash, expires);
    }

    private String tomorrow() { return OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(); }

    @Test
    void createsOnlyFourTablesAndThreeRoles() throws Exception {
        assertEquals("4", scalar(connection, "SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID(N'" + schema + "')"));
        assertEquals("3", scalar(connection, "SELECT COUNT(*) FROM " + schema + ".roles"));
        assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM " + schema + ".usuarios"));
        user();
        sql("INSERT dbo.usuario_rol(usuario_id,rol_id) SELECT 1,id FROM dbo.roles WHERE codigo='ADMIN'");
        session(UUID.randomUUID().toString(), 1, hash(), tomorrow());
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        user();
        SQLException error = assertThrows(SQLException.class, this::user);
        assertEquals(2627, error.getErrorCode());
        assertTrue(error.getMessage().contains("UQ_usuarios_correo"));
    }

    @Test
    void rejectsDuplicateRoleAssignment() throws Exception {
        user();
        sql("INSERT dbo.usuario_rol(usuario_id,rol_id) SELECT 1,id FROM dbo.roles WHERE codigo='ADMIN'");
        SQLException error = assertThrows(SQLException.class, () -> sql("INSERT dbo.usuario_rol(usuario_id,rol_id) SELECT 1,id FROM dbo.roles WHERE codigo='ADMIN'"));
        assertEquals(2627, error.getErrorCode());
        assertTrue(error.getMessage().contains("PK_usuario_rol"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuario", "rol", "sesion"})
    void rejectsOrphanForeignKeys(String kind) throws Exception {
        user();
        SQLException error = assertThrows(SQLException.class, () -> {
            switch (kind) {
                case "usuario" -> sql("INSERT dbo.usuario_rol(usuario_id,rol_id) SELECT 999999,id FROM dbo.roles WHERE codigo='ADMIN'");
                case "rol" -> sql("INSERT dbo.usuario_rol(usuario_id,rol_id) VALUES(1,999999)");
                case "sesion" -> session(UUID.randomUUID().toString(), 999999, hash(), tomorrow());
                default -> fail(kind);
            }
        });
        assertEquals(547, error.getErrorCode());
        assertTrue(error.getMessage().contains("FK_"));
    }

    @Test
    void rejectsDuplicateRoleCode() throws Exception {
        SQLException error = assertThrows(SQLException.class, () -> sql("INSERT dbo.roles(codigo,nombre) VALUES('ADMIN',N'Otro')"));
        assertEquals(2627, error.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERSONA@example.test", "persona@example.test "})
    void rejectsNonCanonicalEmail(String email) throws Exception {
        SQLException error = assertThrows(SQLException.class, () -> sql("INSERT dbo.usuarios(nombre,apellido,correo,password_hash) VALUES(N'A',N'B',?,?)", email, HASH));
        assertEquals(547, error.getErrorCode());
        assertTrue(error.getMessage().contains("CK_usuarios_correo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-formato", "coste-bajo", "espacio"})
    void rejectsInvalidPasswordHash(String kind) throws Exception {
        String value = switch (kind) {
            case "coste-bajo" -> HASH.replace("$12$", "$04$");
            case "espacio" -> HASH.substring(0, HASH.length() - 1) + " ";
            default -> "valor-invalido";
        };
        SQLException error = assertThrows(SQLException.class, () -> sql("INSERT dbo.usuarios(nombre,apellido,correo,password_hash) VALUES(N'A',N'B',?,?)", "a@example.test", value));
        assertEquals(547, error.getErrorCode());
        assertTrue(error.getMessage().contains("CK_usuarios_password_hash"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hash", "identificador"})
    void rejectsDuplicateSessionIdentifiers(String kind) throws Exception {
        user();
        String id = UUID.randomUUID().toString();
        String hash = hash();
        session(id, 1, hash, tomorrow());
        SQLException error = assertThrows(SQLException.class, () -> session(kind.equals("identificador") ? id : UUID.randomUUID().toString(), 1, kind.equals("hash") ? hash : hash(), tomorrow()));
        assertEquals(2627, error.getErrorCode());
        assertTrue(error.getMessage().contains(kind.equals("hash") ? "UQ_sesiones_refresh_token_hash" : "PK_sesiones_refresh"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hash", "hash_mayusculas", "hash_espacio", "periodo", "zona", "revocacion"})
    void enforcesSessionChecks(String kind) throws Exception {
        user();
        SQLException error = assertThrows(SQLException.class, () -> {
            if (kind.equals("revocacion")) {
                sql("INSERT dbo.sesiones_refresh(id,usuario_id,familia_id,token_hash,fecha_expiracion,fecha_revocacion) VALUES(?,1,?,?,?,'1999-01-01T00:00:00+00:00')", UUID.randomUUID().toString(), UUID.randomUUID().toString(), hash(), tomorrow());
            } else {
                String expiry = kind.equals("periodo") ? "1999-01-01T00:00:00+00:00" : kind.equals("zona") ? OffsetDateTime.now(ZoneOffset.ofHours(2)).plusDays(1).toString() : tomorrow();
                String token = switch (kind) {
                    case "hash" -> "valor-invalido";
                    case "hash_mayusculas" -> "A" + hash().substring(1);
                    case "hash_espacio" -> hash().substring(0, 63) + " ";
                    default -> hash();
                };
                session(UUID.randomUUID().toString(), 1, token, expiry);
            }
        });
        assertEquals(547, error.getErrorCode());
        assertTrue(error.getMessage().contains("CK_sesiones_refresh_"));
    }
}
