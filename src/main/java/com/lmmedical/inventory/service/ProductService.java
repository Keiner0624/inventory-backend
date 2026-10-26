package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.auditoria.DetalleAuditoria;
import static com.lmmedical.inventory.enums.AccionAuditoria.*;

import com.lmmedical.inventory.dto.ProductRequest;
import com.lmmedical.inventory.dto.ProductResponse;
import com.lmmedical.inventory.entity.Category;
import com.lmmedical.inventory.entity.Product;
import com.lmmedical.inventory.repository.CategoryRepository;
import com.lmmedical.inventory.repository.ProductRepository;
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
public class ProductService {
    private final AuditoriaService auditoria;
    private final ProductRepository productoRepository;
    private final CategoryRepository categoriaRepository;

    @Transactional
    public ProductResponse registrar(ProductRequest request) {
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);

        if (productoRepository.existsByCodigo(codigo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El producto ya existe");
        }
        Category categoria = categoriaRepository.findById(request.categoriaId()).orElseThrow(()
        -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoria no exite"));

      Product product = new Product(
              codigo,
              request.nombre().trim(),
              request.descripcion(),
              request.precioCompra(),
              request.precioVenta(),
              categoria

      );

        Product guardado = productoRepository.save(product);
        auditoria.registrar(PRODUCTO_CREADO, guardado.getId(), null, detalle(guardado));
        return convertirResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listar(Pageable pageable) {
        return productoRepository.findAll(pageable).map(this::convertirResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse buscarPorId(Long id) {
        Product product = productoRepository.findById(id).orElseThrow(()
        -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return convertirResponse(product);
    }

    private ProductResponse convertirResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCodigo(),
                product.getNombre(),
                product.getDescripcion(),
                product.getPrecioCompra(),
                product.getPrecioVenda(),
                product.getCategoria().getNombre(),
                product.isActivo()
        );
    }
    @Transactional
    public ProductResponse actualizar(Long id, ProductRequest request) {
        Product product = productoRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!product.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede actualizar un producto inactivo");
        }
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        if (productoRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El código ya pertenece a otro producto");
        }
        Category category = categoriaRepository.findById(request.categoriaId()).orElseThrow(()
                -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoria no exite"));
        var anterior = detalle(product);
        product.actualizarDatos(
                codigo,
                request.nombre().trim(),
                request.descripcion(),
                request.precioCompra(),
                request.precioVenta(),
                category
        );
        auditoria.registrar(PRODUCTO_ACTUALIZADO, product.getId(), null,
                new DetalleAuditoria.CambioProducto(anterior, detalle(product)));
        return convertirResponse(product);
    }

    private DetalleAuditoria.Producto detalle(Product producto) {
        return new DetalleAuditoria.Producto(producto.getCodigo(), producto.getNombre(), producto.getCategoria().getId(), producto.getPrecioCompra(),
                producto.getPrecioVenda(), producto.isActivo());
    }

    @Transactional
    public void desactivar(Long id) {
        Product product = productoRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!product.isActivo()) {
            return;
        }
        product.desactivar();
        auditoria.registrar(PRODUCTO_DESACTIVADO, product.getId(), null, new DetalleAuditoria.Estado(true, false));
    }
}
