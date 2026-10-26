package com.lmmedical.inventory.dto.usuario;

import java.util.List;

public record UsuarioPaginaResponse(
        List<UsuarioConsultaResponse> usuarios,
        long totalElementos,
        int pagina,
        int tamano,
        int totalPaginas
) {
}
