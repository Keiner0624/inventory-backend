package com.lmmedical.inventory.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sesiones_refresh", schema = "dbo", uniqueConstraints = @UniqueConstraint(name = "UQ_sesiones_refresh_token_hash", columnNames = "token_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SesionRefresh {
    private static final String HASH_PATTERN = "[0-9a-f]{64}";

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @NotNull @Column(name = "familia_id", nullable = false, updatable = false)
    private UUID familiaId;

    @JsonIgnore @NotBlank @Pattern(regexp = HASH_PATTERN)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @NotNull @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "fecha_creacion", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime fechaCreacion;

    @NotNull @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "fecha_expiracion", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime fechaExpiracion;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "fecha_revocacion", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime fechaRevocacion;

    public SesionRefresh(Usuario usuario, String tokenHash, OffsetDateTime fechaExpiracion) {
        this(usuario, tokenHash, UUID.randomUUID(), fechaExpiracion);
    }

    public SesionRefresh(Usuario usuario, String tokenHash, UUID familiaId, OffsetDateTime fechaExpiracion) {
        this.usuario = Objects.requireNonNull(usuario, "El usuario es obligatorio");
        this.familiaId = Objects.requireNonNull(familiaId, "La familia es obligatoria");
        if (tokenHash == null || !tokenHash.matches(HASH_PATTERN)) {
            throw new IllegalArgumentException("tokenHash debe contener un SHA-256 hexadecimal en minusculas");
        }
        this.tokenHash = tokenHash;
        this.fechaCreacion = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        this.fechaExpiracion = Objects.requireNonNull(fechaExpiracion).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (!this.fechaExpiracion.isAfter(this.fechaCreacion)) {
            throw new IllegalArgumentException("La expiracion debe ser posterior a la creacion");
        }
    }

    public void revocar(OffsetDateTime instante) {
        OffsetDateTime utc = Objects.requireNonNull(instante).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (utc.isBefore(fechaCreacion)) throw new IllegalArgumentException("La revocacion no puede preceder a la creacion");
        if (fechaRevocacion == null) fechaRevocacion = utc;
    }

    public boolean estaVigente(OffsetDateTime ahora){
        Objects.requireNonNull(ahora, "La fecha actual es obligatoria");
        return fechaRevocacion == null && !ahora.isBefore(fechaCreacion) && fechaExpiracion.isAfter(ahora);
    }

}
