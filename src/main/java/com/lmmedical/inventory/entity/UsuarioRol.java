package com.lmmedical.inventory.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "usuario_rol", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsuarioRol {
    @EmbeddedId
    private Id id;

    @JsonIgnore @NotNull @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @JsonIgnore @NotNull @MapsId("rolId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    public UsuarioRol(Usuario usuario, Rol rol) {
        this.usuario = Objects.requireNonNull(usuario, "El usuario es obligatorio");
        this.rol = Objects.requireNonNull(rol, "El rol es obligatorio");
        this.id = new Id(Objects.requireNonNull(usuario.getId(), "Persiste el usuario primero"),
                Objects.requireNonNull(rol.getId(), "Persiste el rol primero"));
    }

    @Embeddable
    @Getter @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Id implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull @Column(name = "usuario_id")
        private Long usuarioId;
        @NotNull @Column(name = "rol_id")
        private Long rolId;

        public Id(Long usuarioId, Long rolId) {
            this.usuarioId = Objects.requireNonNull(usuarioId);
            this.rolId = Objects.requireNonNull(rolId);
        }
    }
}
