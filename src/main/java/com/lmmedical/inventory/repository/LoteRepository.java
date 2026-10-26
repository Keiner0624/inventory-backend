package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.Lote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoteRepository extends JpaRepository<Lote, Long> {
    boolean existsByProduct_IdAndCodigo(Long productoId, String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lote FROM Lote lote WHERE lote.id = :id")
    Optional<Lote> buscarParaMovimiento(@Param("id") long id);
    List<Lote> findByProduct_IdOrderByIdAsc(Long productoId);
}
