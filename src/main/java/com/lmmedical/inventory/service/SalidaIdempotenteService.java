package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.DetalleSalidaResponse;
import com.lmmedical.inventory.dto.SalidaInventarioRequest;
import com.lmmedical.inventory.dto.SalidaInventarioResponse;
import com.lmmedical.inventory.entity.OperacionSalida;
import com.lmmedical.inventory.repository.OperacionSalidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalidaIdempotenteService {
    private final OperacionSalidaRepository operacionSalidaRepository;
    private final SalidaInventarioService salidaInventarioService;

    @Transactional
    public SalidaInventarioResponse registrar(String clave, SalidaInventarioRequest request) {
        String claveNormalizada = validarClave(clave);
        var existente = operacionSalidaRepository.bloquearPorClave(claveNormalizada);

        if (existente.isPresent()){
            OperacionSalida operacion = existente.get();

            if (!operacion.coincideCon(
                    request.productoId(),
                    request.almacenId(),
                    request.cantidad(),
                    request.observacion()
            )){
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La clave pertenece a otra operación");
            }
            return reconstruirRespuesta(operacion);
        }

        SalidaInventarioResponse respuesta = salidaInventarioService.registrarSalida(request);
        OperacionSalida operacion = new OperacionSalida(
                claveNormalizada,
                request.productoId(),
                request.almacenId(),
                request.cantidad(),
                request.observacion()
        );
        for (DetalleSalidaResponse detalle : respuesta.detalles()){
            operacion.agregarDetalle(
                    detalle.loteId(),
                    detalle.codigoLote(),
                    detalle.cantidadRetirada(),
                    detalle.stockRestante()
            );
        }
        operacionSalidaRepository.saveAndFlush(operacion);
        return respuesta;
    }

    private String validarClave(String clave) {
        try {
            if (clave == null){
                throw new IllegalArgumentException();
            }
            String normalizada = clave.trim().toLowerCase(Locale.ROOT);
            UUID uuid  = UUID.fromString(normalizada);

            if (!uuid.toString().equals(normalizada)){
                throw new IllegalArgumentException();
            }
            return normalizada;
        }catch (IllegalArgumentException exception){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key debe ser un UUID válido");
        }
    }

    private SalidaInventarioResponse reconstruirRespuesta(OperacionSalida operacion){
        List<DetalleSalidaResponse> detalles = operacion.getDetalles().stream()
                .map(detalle -> new DetalleSalidaResponse(
                        detalle.getLoteId(),
                        detalle.getCodigoLote(),
                        detalle.getCantidadRetirada(),
                        detalle.getStockRestante()
                )).toList();
        return new SalidaInventarioResponse(
                operacion.getProductoId(),
                operacion.getAlmacenId(),
                operacion.getCantidad(),
                detalles
        );
    }
}
