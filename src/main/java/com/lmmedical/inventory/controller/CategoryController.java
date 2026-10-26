package com.lmmedical.inventory.controller;

import com.lmmedical.inventory.dto.CategoryRequest;
import com.lmmedical.inventory.dto.CategoryResponse;
import com.lmmedical.inventory.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> registrar(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse categoria = categoryService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoria);
    }

    @GetMapping
    public ResponseEntity<Page<CategoryResponse>> listar(@PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return ResponseEntity.ok(categoryService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> actualizar(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.actualizar(id,request));
    }
}
