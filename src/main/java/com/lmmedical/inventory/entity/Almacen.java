package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "almacenes", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Almacen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 200)
    String ubicacion;

    @Column(nullable = false)
    private boolean activo = true;

    public Almacen(String codigo, String nombre, String ubicacion) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
    }

    public void actualizar(String codigo, String nombre, String ubicacion) {
        if (!activo){
            throw new IllegalArgumentException("No se puede modificar un almacén inactivo");
        }

        if (codigo == null || codigo.isBlank()){
            throw new IllegalArgumentException("El código es obligatorio");
        }

        if (nombre == null || nombre.isBlank()){
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        if (ubicacion == null || ubicacion.isBlank()){
            throw new IllegalArgumentException("El ubicación es obligatoria");
        }

        this.codigo = codigo;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
    }

    public void desactivar(){
        this.activo = false;
    }
}
