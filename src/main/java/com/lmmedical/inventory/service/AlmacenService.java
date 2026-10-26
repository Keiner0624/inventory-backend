package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;

import com.lmmedical.inventory.dto.AlmacenRequest;
import com.lmmedical.inventory.dto.AlmacenResponse;
import com.lmmedical.inventory.entity.Almacen;
import com.lmmedical.inventory.repository.AlmacenRepository;
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
public class AlmacenService {
    private final AuditoriaService auditoria;
    private final AlmacenRepository almacenRepository;

    @Transactional
    public AlmacenResponse registrar(AlmacenRequest request) {
        String codigo = normalizarCodigo(request.codigo());

        if (almacenRepository.existsByCodigo(codigo)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El código de almacén ya exite");
        }

        Almacen almacen = new Almacen(codigo, request.nombre().trim(), request.ubicacion().trim());
        Almacen guardado = almacenRepository.save(almacen);
        auditoria.registrar(ALMACEN_CREADO, guardado.getId(), guardado.getId(), new DetalleAuditoria.Catalogo(guardado.getCodigo(), guardado.getNombre(), guardado.isActivo()));
        return convertirResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Page<AlmacenResponse> listar(Pageable pageable) {
        return almacenRepository.findAll(pageable).map(this::convertirResponse);
    }

    @Transactional(readOnly = true)
    public AlmacenResponse buscarPorId(Long id) {
        Almacen almacen = buscarEntidad(id);
        return convertirResponse(almacen);
    }

    @Transactional
    public AlmacenResponse actualizar(Long id, AlmacenRequest request) {
        Almacen almacen = buscarEntidad(id);

        if (!almacen.isActivo()){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede actualizar un almacén inactivo");
        }

        String codigo = normalizarCodigo(request.codigo());

        if (almacenRepository.existsByCodigoAndIdNot(codigo, id)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El código pertenece a otro almacén");
        }

        var anterior = new DetalleAuditoria.Catalogo(almacen.getCodigo(), almacen.getNombre(), almacen.isActivo());
        almacen.actualizar(codigo,request.nombre().trim(),request.ubicacion().trim());
        auditoria.registrar(ALMACEN_ACTUALIZADO, almacen.getId(), almacen.getId(),
                new DetalleAuditoria.CambioCatalogo(anterior, new DetalleAuditoria.Catalogo(almacen.getCodigo(), almacen.getNombre(), almacen.isActivo())));
        return convertirResponse(almacen);
    }

    @Transactional
    public void desactivar(Long id) {
        Almacen almacen = buscarEntidad(id);

        if (!almacen.isActivo()){
            return;
        }
        almacen.desactivar();
        auditoria.registrar(ALMACEN_DESACTIVADO, almacen.getId(), almacen.getId(), new DetalleAuditoria.Estado(true, false));
    }

    private Almacen buscarEntidad(Long id) {
        return almacenRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Almacén no encontrado"));
    }

    private String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    private AlmacenResponse convertirResponse(Almacen almacen) {
        return new AlmacenResponse(
                almacen.getId(),
                almacen.getCodigo(),
                almacen.getNombre(),
                almacen.getUbicacion(),
                almacen.isActivo()
        );
    }
}
