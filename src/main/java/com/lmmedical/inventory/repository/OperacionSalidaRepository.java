package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.OperacionSalida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OperacionSalidaRepository extends JpaRepository<OperacionSalida, String> {
    @Query(
            value = "SELECT * FROM dbo.operaciones_salida WITH (UPDLOCK, HOLDLOCK) WHERE clave = :clave",
            nativeQuery = true
    )
    Optional<OperacionSalida> bloquearPorClave(@Param("clave") String clave);
}
