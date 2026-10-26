package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "roles", schema = "dbo", uniqueConstraints = @UniqueConstraint(name = "UQ_roles_codigo", columnNames = "codigo"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rol {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*")
    @Column(nullable = false, length = 50)
    private String codigo;

    @NotBlank @Size(max = 100) @Nationalized
    @Column(nullable = false, length = 100)
    private String nombre;

    public Rol(String codigo, String nombre) {
        this.codigo = Objects.requireNonNull(codigo, "El codigo es obligatorio").strip().toUpperCase(Locale.ROOT);
        this.nombre = Objects.requireNonNull(nombre, "El nombre es obligatorio").strip();
    }
}
