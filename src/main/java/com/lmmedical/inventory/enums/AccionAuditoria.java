package com.lmmedical.inventory.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccionAuditoria {
    PRODUCTO_CREADO(EntidadAuditoria.PRODUCTO, "Producto creado"),
    PRODUCTO_ACTUALIZADO(EntidadAuditoria.PRODUCTO, "Producto actualizado"),
    PRODUCTO_DESACTIVADO(EntidadAuditoria.PRODUCTO, "Producto desactivado"),
    ENTRADA_REGISTRADA(EntidadAuditoria.MOVIMIENTO_INVENTARIO, "Entrada de inventario registrada"),
    SALIDA_REGISTRADA(EntidadAuditoria.MOVIMIENTO_INVENTARIO, "Salida FEFO registrada"),
    TRANSFERENCIA_REGISTRADA(EntidadAuditoria.TRANSFERENCIA, "Transferencia entre almacenes registrada"),
    USUARIO_CREADO(EntidadAuditoria.USUARIO, "Usuario creado"),
    USUARIO_ACTIVADO(EntidadAuditoria.USUARIO, "Usuario activado"),
    USUARIO_DESACTIVADO(EntidadAuditoria.USUARIO, "Usuario desactivado"),
    ROLES_MODIFICADOS(EntidadAuditoria.USUARIO, "Roles de usuario modificados"),
    CATEGORIA_CREADA(EntidadAuditoria.CATEGORIA, "Categoría creada"),
    CATEGORIA_ACTUALIZADA(EntidadAuditoria.CATEGORIA, "Categoría actualizada"),
    PROVEEDOR_CREADO(EntidadAuditoria.PROVEEDOR, "Proveedor creado"),
    PROVEEDOR_ACTUALIZADO(EntidadAuditoria.PROVEEDOR, "Proveedor actualizado"),
    PROVEEDOR_DESACTIVADO(EntidadAuditoria.PROVEEDOR, "Proveedor desactivado"),
    ALMACEN_CREADO(EntidadAuditoria.ALMACEN, "Almacén creado"),
    ALMACEN_ACTUALIZADO(EntidadAuditoria.ALMACEN, "Almacén actualizado"),
    ALMACEN_DESACTIVADO(EntidadAuditoria.ALMACEN, "Almacén desactivado"),
    LOTE_CREADO(EntidadAuditoria.LOTE, "Lote creado");

    private final EntidadAuditoria entidad;
    private final String descripcion;
}
