package com.lmmedical.inventory.dto.auditoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lista cerrada de datos de negocio. Nunca recibe DTOs HTTP, credenciales u observaciones libres. */
public sealed interface DetalleAuditoria {
    record Movimiento(Long productoId, Long loteId, int cantidad) implements DetalleAuditoria {}
    record LoteTrasladado(Long loteId, int cantidad) {}
    record Traslado(Long productoId, Long origenId, Long destinoId, int cantidad,
                    List<LoteTrasladado> lotes) implements DetalleAuditoria {
        public Traslado { lotes = List.copyOf(lotes); }
    }
    record Roles(List<String> antes, List<String> despues) implements DetalleAuditoria {
        public Roles {
            antes = List.copyOf(antes); despues = List.copyOf(despues);
            var permitidos = List.of("ADMIN", "ENCARGADO_ALMACEN", "EMPLEADO");
            if (!permitidos.containsAll(antes) || !permitidos.containsAll(despues))
                throw new IllegalArgumentException("Rol de auditoría inválido");
        }
    }
    record Estado(Boolean antes, boolean despues) implements DetalleAuditoria {}
    record Producto(String codigo, String nombre, Long categoriaId, BigDecimal precioCompra, BigDecimal precioVenta,
                    boolean activo) implements DetalleAuditoria {}
    record CambioProducto(Producto antes, Producto despues) implements DetalleAuditoria {}
    record Catalogo(String codigo, String nombre, Boolean activo) implements DetalleAuditoria {}
    record CambioCatalogo(Catalogo antes, Catalogo despues) implements DetalleAuditoria {}
    record LoteCreado(Long productoId, LocalDate fechaVencimiento) implements DetalleAuditoria {}
}
