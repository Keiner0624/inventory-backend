package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
    Optional<Transferencia> findByClaveIdempotencia(String claveIdempotencia);

    @Query(
            value = """
                SELECT *
                FROM dbo.transferencias
                WITH (UPDLOCK, HOLDLOCK)
                WHERE clave_idempotencia = :clave
                """,
            nativeQuery = true
    )
    Optional<Transferencia> bloquearPorClave(@Param("clave") String clave);
}
