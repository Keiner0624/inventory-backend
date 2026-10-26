package com.lmmedical.inventory.entity;

import com.lmmedical.inventory.enums.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "eventos_auditoria", schema = "dbo")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoAuditoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="fecha", nullable=false, updatable=false, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime fecha;
    @Column(name="usuario_id", nullable=false, updatable=false)
    private Long usuarioId;
    @Column(name="usuario_identificador", nullable=false, updatable=false, length=254)
    private String usuarioIdentificador;
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=40)
    private AccionAuditoria accion;
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=30)
    private EntidadAuditoria entidad;
    @Column(name="entidad_id", nullable=false, updatable=false)
    private Long entidadId;
    @Column(name="almacen_id", updatable=false)
    private Long almacenId;
    @Nationalized @Column(nullable=false, updatable=false, length=200)
    private String descripcion;
    @Nationalized @Column(name="detalles", updatable=false, columnDefinition="nvarchar(max)")
    private String detalles;

    public EventoAuditoria(Long usuarioId, String usuarioIdentificador, AccionAuditoria accion,
                           Long entidadId, Long almacenId, String detalles) {
        if (usuarioId == null || usuarioId <= 0 || entidadId == null || entidadId <= 0
                || (almacenId != null && almacenId <= 0))
            throw new IllegalArgumentException("Identificadores de auditoría inválidos");
        if (usuarioIdentificador == null || usuarioIdentificador.isBlank() || usuarioIdentificador.length() > 254)
            throw new IllegalArgumentException("Identidad de auditoría inválida");
        this.fecha = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        this.usuarioId = usuarioId;
        this.usuarioIdentificador = usuarioIdentificador;
        this.accion = Objects.requireNonNull(accion);
        this.entidad = accion.getEntidad();
        this.entidadId = entidadId;
        this.almacenId = almacenId;
        this.descripcion = accion.getDescripcion();
        this.detalles = detalles;
    }

    @PreRemove
    private void impedirBorrado() { throw new IllegalStateException("El historial de auditoría es inmutable"); }
}
