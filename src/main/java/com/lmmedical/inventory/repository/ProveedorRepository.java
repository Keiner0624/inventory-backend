package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    boolean existsByRuc(String ruc);
}
