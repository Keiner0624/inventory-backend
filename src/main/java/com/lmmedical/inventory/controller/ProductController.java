package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.ProductRequest;
import com.lmmedical.inventory.dto.ProductResponse;
import com.lmmedical.inventory.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> registrar(@Valid @RequestBody ProductRequest request){
        ProductResponse product = productService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> listar(
            @PageableDefault(size = 20, sort = "nombre")
            Pageable pageable
    ){
        return ResponseEntity.ok(productService.listar(pageable));
    }

     @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> buscarPorId(@PathVariable Long id){
        return ResponseEntity.ok(productService.buscarPorId(id));
     }

     @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> actualizar(@PathVariable Long id, @Valid @RequestBody ProductRequest request){
        ProductResponse productResponse = productService.actualizar(id, request);
        return ResponseEntity.ok(productResponse);
     }

     @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivar(@PathVariable Long id){
        productService.desactivar(id);
        return ResponseEntity.noContent().build();
     }
}
