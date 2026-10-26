package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categorias", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    public Category(String nombre) {
        cambiarNombre(nombre);
    }

    public void cambiarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()){
            throw new IllegalArgumentException("El nombre de la categoria es obligatorio");
        }
        String nombreActualizado = nombre.trim();
        if (nombreActualizado.length() > 100){
            throw new IllegalArgumentException("El nombre no puede superar los 100 caracteres");
        }
        this.nombre = nombreActualizado;
    }
}
