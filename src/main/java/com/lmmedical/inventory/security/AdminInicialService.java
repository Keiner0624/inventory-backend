package com.lmmedical.inventory.security;

import com.lmmedical.inventory.entity.Rol;
import com.lmmedical.inventory.entity.Usuario;
import com.lmmedical.inventory.entity.UsuarioRol;
import com.lmmedical.inventory.repository.RolRepository;
import com.lmmedical.inventory.repository.UsuarioRepository;
import com.lmmedical.inventory.repository.UsuarioRolRepository;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminInicialService {
    static final String DATABASE = "LM_Medical_supplies";
    static final String LOCK_SQL = """
            DECLARE @resultado int;
            EXEC @resultado = sys.sp_getapplock
                @Resource=N'LM_Medical_Primer_Admin', @LockMode='Exclusive',
                @LockOwner='Transaction', @LockTimeout=10000;
            SELECT @resultado;
            """;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository asignaciones;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final Validator validator;

    public AdminInicialService(UsuarioRepository usuarios, RolRepository roles,
            UsuarioRolRepository asignaciones, PasswordEncoder encoder, JdbcTemplate jdbc, Validator validator) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.asignaciones = asignaciones;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.validator = validator;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean crear(String destinoConfirmado, String nombre, String apellido, String correo, String password) {
        if (!DATABASE.equalsIgnoreCase(destinoConfirmado)) {
            throw new IllegalStateException("Bootstrap bloqueado: BOOTSTRAP_ADMIN_DATABASE debe confirmar LM_Medical_supplies.");
        }
        List<String> missing = new ArrayList<>();
        if (nombre == null || nombre.isBlank()) missing.add("BOOTSTRAP_ADMIN_NOMBRE");
        if (apellido == null || apellido.isBlank()) missing.add("BOOTSTRAP_ADMIN_APELLIDO");
        if (correo == null || correo.isBlank()) missing.add("BOOTSTRAP_ADMIN_CORREO");
        if (password == null || password.isBlank()) missing.add("BOOTSTRAP_ADMIN_PASSWORD");
        if (!missing.isEmpty()) throw new IllegalArgumentException("Faltan variables: " + String.join(", ", missing));
        if (password.codePointCount(0, password.length()) < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("BOOTSTRAP_ADMIN_PASSWORD debe tener al menos 12 caracteres y no superar 72 bytes UTF-8.");
        }
        String actual = jdbc.queryForObject("SELECT DB_NAME()", String.class);
        if (!DATABASE.equalsIgnoreCase(actual)) {
            throw new IllegalStateException("Bootstrap bloqueado: la conexión no apunta a LM_Medical_supplies.");
        }
        // SELECT DB_NAME() no inicia una transacción implícita en SQL Server.
        jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.usuarios", Long.class);
        Integer transaction = jdbc.queryForObject("SELECT @@TRANCOUNT", Integer.class);
        if (transaction == null || transaction < 1) throw new IllegalStateException("El bootstrap requiere una transacción activa.");
        // Serializa dos bootstraps; el bloqueo se libera al confirmar o revertir la transacción.
        Integer result = jdbc.queryForObject(LOCK_SQL, Integer.class);
        if (result == null || result < 0) throw new IllegalStateException("No se obtuvo el bloqueo exclusivo del bootstrap; no se creó la cuenta.");
        if (asignaciones.existsByRol_Codigo("ADMIN")) return false;
        String normalized = correo.strip().toLowerCase(Locale.ROOT);
        if (usuarios.existsByCorreo(normalized)) {
            throw new IllegalStateException("El correo ya está registrado; el bootstrap no modifica usuarios existentes.");
        }
        Usuario usuario = new Usuario(nombre, apellido, normalized, encoder.encode(password));
        if (!validator.validate(usuario).isEmpty()) {
            throw new IllegalArgumentException("Datos del administrador inválidos; revisa nombre, apellido y correo sin registrar sus valores.");
        }
        Rol admin = roles.findByCodigo("ADMIN")
                .orElseGet(() -> roles.saveAndFlush(new Rol("ADMIN", "Administrador")));
        Usuario persisted = usuarios.saveAndFlush(usuario);
        asignaciones.saveAndFlush(new UsuarioRol(persisted, admin));
        return true;
    }
}
