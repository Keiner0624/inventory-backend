package com.lmmedical.inventory.dto.auditoria;
import java.util.List;

public record AuditoriaPaginaResponse(List<EventoAuditoriaResponse> eventos, long totalElementos,
                                      int pagina, int tamanio, int totalPaginas) {}
