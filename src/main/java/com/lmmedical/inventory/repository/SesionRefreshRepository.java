package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.SesionRefresh;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SesionRefreshRepository extends JpaRepository<SesionRefresh, UUID> {
    Optional<SesionRefresh> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionRefresh s join fetch s.usuario where s.tokenHash = :hash")
    Optional<SesionRefresh> bloquearPorHash(@Param("hash") String hash);
    boolean  existsByFamiliaIdAndUsuario_IdAndFechaRevocacionIsNullAndFechaExpiracionAfter(
            UUID familiaId, Long usuarioId, OffsetDateTime ahora
    );
    List<SesionRefresh> findByFamiliaIdAndUsuario_Id(UUID familiaId, Long usuarioId);
}
