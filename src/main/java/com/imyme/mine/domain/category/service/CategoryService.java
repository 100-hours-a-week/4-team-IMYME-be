package com.imyme.mine.domain.category.service;

import com.imyme.mine.domain.category.dto.CategoryResponse;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategories(Boolean isActive) {
        if (isActive == null) {
            return categoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(CategoryResponse::from)
                .toList();
        }
        return categoryRepository.findAllByIsActiveOrderByDisplayOrderAsc(isActive).stream()
            .map(CategoryResponse::from)
            .toList();
    }
}
