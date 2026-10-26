package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.*;
import com.lmmedical.inventory.entity.EventoAuditoria;
import com.lmmedical.inventory.enums.*;
import com.lmmedical.inventory.repository.EventoAuditoriaRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AuditoriaConsultaService {
    private final EventoAuditoriaRepository eventos;
    private final JsonMapper jsonMapper;

    public AuditoriaPaginaResponse listar(int page, int size, Long usuarioId, AccionAuditoria accion,
            EntidadAuditoria entidad, Long entidadId, Long almacenId, OffsetDateTime desde, OffsetDateTime hasta) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || invalido(usuarioId) || invalido(entidadId) || invalido(almacenId) || fechaInvalida(desde) || fechaInvalida(hasta)
                || (desde != null && hasta != null && desde.isAfter(hasta)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtros o paginación de auditoría inválidos");
        var pagina = eventos.findAll((root, query, cb) -> {
            var condiciones = new ArrayList<Predicate>();
            if (usuarioId != null) condiciones.add(cb.equal(root.get("usuarioId"), usuarioId));
            if (accion != null) condiciones.add(cb.equal(root.get("accion"), accion));
            if (entidad != null) condiciones.add(cb.equal(root.get("entidad"), entidad));
            if (entidadId != null) condiciones.add(cb.equal(root.get("entidadId"), entidadId));
            if (almacenId != null) condiciones.add(cb.equal(root.get("almacenId"), almacenId));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            if (hasta != null) condiciones.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta));
            return cb.and(condiciones.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fecha", "id")));
        return new AuditoriaPaginaResponse(pagina.getContent().stream().map(this::respuesta).toList(),
                pagina.getTotalElements(), pagina.getNumber(), pagina.getSize(), pagina.getTotalPages());
    }

    public EventoAuditoriaResponse buscar(Long id) {
        if (id == null || invalido(id))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido");
        return respuesta(eventos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento de auditoría no encontrado")));
    }

    private boolean fechaInvalida(OffsetDateTime fecha) { return fecha != null && (fecha.getYear() < 1 || fecha.getYear() > 9999); }
    private boolean invalido(Long id) { return id != null && id <= 0; }
    private EventoAuditoriaResponse respuesta(EventoAuditoria evento) {
        return new EventoAuditoriaResponse(evento.getId(), evento.getFecha().withOffsetSameInstant(ZoneOffset.UTC),
                evento.getUsuarioId(), evento.getUsuarioIdentificador(), evento.getAccion(), evento.getEntidad(),
                evento.getEntidadId(), evento.getAlmacenId(), evento.getDescripcion(),
                evento.getDetalles() == null ? null : jsonMapper.readTree(evento.getDetalles()));
    }
}
