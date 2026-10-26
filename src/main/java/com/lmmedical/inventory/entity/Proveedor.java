package com.lmmedical.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "proveedores", schema = "dbo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(length = 15)
    private String correo;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    private boolean activo = true;

    public Proveedor(String ruc, String razonSocial, String correo, String telefono) {
        this.ruc = ruc;
        this.razonSocial = razonSocial;
        this.correo = correo;
        this.telefono = telefono;
        this.activo = true;
    }

    public void actualizar(String razonSocial, String correo, String telefono) {
        if (!activo){
            throw new IllegalArgumentException("No se puede actualizar un proveedor inactivo");
        }

        if (razonSocial == null || razonSocial.isBlank()){
            throw new IllegalArgumentException("La razón social es obligatoria");
        }

        this.razonSocial = razonSocial;
        this.correo = correo;
        this.telefono = telefono;
    }

    public void desactivar(){
        this.activo = false;
    }
}
