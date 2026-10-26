package com.lmmedical.inventory.service;

import com.lmmedical.inventory.dto.CategoryRequest;
import com.lmmedical.inventory.dto.CategoryResponse;
import com.lmmedical.inventory.entity.Category;
import com.lmmedical.inventory.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CategoryService{
    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse registrar(CategoryRequest request) {
        String nombre = request.nombre().trim();

        if (categoryRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoria ya existe");
        }
        Category category = new Category(nombre);
        Category guardada = categoryRepository.save(category);
        return convertirResponse(guardada);
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listar(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(this::convertirResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse buscarPorId(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada"));
        return convertirResponse(category);
    }

    @Transactional
    public CategoryResponse actualizar(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada"));
        String nombre = request.nombre().trim();

        if (categoryRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoria con ese nombre");
        }
        category.cambiarNombre(nombre);
        return convertirResponse(category);
    }

    private CategoryResponse convertirResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getNombre());
    }

}
