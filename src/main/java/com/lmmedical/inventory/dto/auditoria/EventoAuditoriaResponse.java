package com.lmmedical.inventory.dto.auditoria;
import com.lmmedical.inventory.enums.*;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record EventoAuditoriaResponse(Long id, OffsetDateTime fecha, Long usuarioId,
        String usuarioIdentificador, AccionAuditoria accion, EntidadAuditoria entidad,
        Long entidadId, Long almacenId, String descripcion, JsonNode detalles) {}
