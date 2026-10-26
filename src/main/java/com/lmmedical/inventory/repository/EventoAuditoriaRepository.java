package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.EventoAuditoria;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/** Expone únicamente consultas; la inserción pertenece a AuditoriaService. */
public interface EventoAuditoriaRepository extends Repository<EventoAuditoria, Long> {
    Optional<EventoAuditoria> findById(Long id);
    Page<EventoAuditoria> findAll(Specification<EventoAuditoria> specification, Pageable pageable);
}
