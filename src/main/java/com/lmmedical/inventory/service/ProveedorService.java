package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;

import com.lmmedical.inventory.dto.ProveedorRequest;
import com.lmmedical.inventory.dto.ProveedorResponse;
import com.lmmedical.inventory.dto.ProveedorUpdateRequest;
import com.lmmedical.inventory.entity.Proveedor;
import com.lmmedical.inventory.repository.ProveedorRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProveedorService {
    private final AuditoriaService auditoria;
    private final ProveedorRepository proveedorRepository;

    @Transactional
    public ProveedorResponse registrar(ProveedorRequest request) {
        String ruc = request.ruc().trim();

        if (proveedorRepository.existsByRuc(ruc)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un proveedor con este ruc.");
        }

        Proveedor proveedor = new Proveedor(
                ruc,
                request.razonSocial().trim(),
                normalizarCorreo(request.correo()),
                request.telefono()
        );
        Proveedor guardado =  proveedorRepository.save(proveedor);
        auditoria.registrar(PROVEEDOR_CREADO, guardado.getId(), null, new DetalleAuditoria.Catalogo(null, guardado.getRazonSocial(), guardado.isActivo()));
        return convertirResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Page<ProveedorResponse> listar(Pageable pageable) {
        return proveedorRepository.findAll(pageable).map(this::convertirResponse);
    }

    @Transactional(readOnly = true)
    public ProveedorResponse buscarPorId(Long id) {
        return convertirResponse(buscarEntidad(id));
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, @Valid ProveedorUpdateRequest request) {
        Proveedor proveedor = buscarEntidad(id);

        if (!proveedor.isActivo()){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede actualizar un proveedor inactivo.");
        }

        var anterior = new DetalleAuditoria.Catalogo(null, proveedor.getRazonSocial(), proveedor.isActivo());
        proveedor.actualizar(
                request.razonSocial().trim(),
                normalizarCorreo(request.correo()),
                request.telefono()
        );
        auditoria.registrar(PROVEEDOR_ACTUALIZADO, proveedor.getId(), null,
                new DetalleAuditoria.CambioCatalogo(anterior, new DetalleAuditoria.Catalogo(null, proveedor.getRazonSocial(), proveedor.isActivo())));
        return convertirResponse(proveedor);
    }

    @Transactional
    public void desactivar(Long id) {
        Proveedor proveedor = buscarEntidad(id);

        if (!proveedor.isActivo()){
            return;
        }
        proveedor.desactivar();
        auditoria.registrar(PROVEEDOR_DESACTIVADO, proveedor.getId(), null, new DetalleAuditoria.Estado(true, false));
    }

    private Proveedor buscarEntidad(Long id) {
        return proveedorRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    private String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()){
            return null;
        }
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private ProveedorResponse convertirResponse(Proveedor proveedor) {
        return new ProveedorResponse(
                proveedor.getId(),
                proveedor.getRuc(),
                proveedor.getRazonSocial(),
                proveedor.getCorreo(),
                proveedor.getTelefono(),
                proveedor.isActivo()
        );
    }
}
