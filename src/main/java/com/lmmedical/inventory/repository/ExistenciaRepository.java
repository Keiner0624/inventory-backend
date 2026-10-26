package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.Existencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExistenciaRepository extends JpaRepository<Existencia, Long> {
    Optional<Existencia> findByLote_IdAndAlmacen_Id(long loteId, Long almacenId);

    @EntityGraph(attributePaths = {"lote", "lote.product", "almacen"})
    Page<Existencia> findByAlmacen_Id(Long almacenId, Pageable pageable);
}
