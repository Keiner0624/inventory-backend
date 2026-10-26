package com.lmmedical.inventory.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "usuarios", schema = "dbo", uniqueConstraints = @UniqueConstraint(name = "UQ_usuarios_correo", columnNames = "correo"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {
    private static final String HASH_PATTERN = "\\{bcrypt}\\$2[aby]\\$(1[2-9]|2[0-9]|3[01])\\$[./A-Za-z0-9]{53}";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 100) @Nationalized
    @Column(nullable = false, length = 100)
    private String nombre;

    @NotBlank @Size(max = 100) @Nationalized
    @Column(nullable = false, length = 100)
    private String apellido;

    @NotBlank @Email @Size(max = 254)
    @Pattern(regexp = "[!-~]+")
    @Column(nullable = false, length = 254)
    private String correo;

    @JsonIgnore
    @NotBlank @Pattern(regexp = HASH_PATTERN)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean activo = true;

    @NotNull @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "fecha_creacion", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime fechaCreacion;

    @NotNull @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "fecha_actualizacion", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime fechaActualizacion;

    public Usuario(String nombre, String apellido, String correo, String passwordHash) {
        this.nombre = Objects.requireNonNull(nombre, "El nombre es obligatorio").strip();
        this.apellido = Objects.requireNonNull(apellido, "El apellido es obligatorio").strip();
        this.correo = Objects.requireNonNull(correo, "El correo es obligatorio").strip().toLowerCase(Locale.ROOT);
        if (passwordHash == null || !passwordHash.matches(HASH_PATTERN)) {
            throw new IllegalArgumentException("passwordHash debe ser un hash BCrypt con prefijo {bcrypt} y coste minimo 12");
        }
        this.passwordHash = passwordHash;
        crearFechas();
    }

    public void cambiarEstado(boolean activo) { this.activo = activo; }

    @PrePersist
    private void crearFechas() {
        if (fechaCreacion == null) fechaCreacion = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (fechaActualizacion == null) fechaActualizacion = fechaCreacion;
    }

    @PreUpdate
    private void actualizarFecha() {
        fechaActualizacion = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
