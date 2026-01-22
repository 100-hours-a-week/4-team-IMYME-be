package com.imyme.mine.domain.category.controller;

import com.imyme.mine.domain.category.dto.CategoryResponse;
import com.imyme.mine.domain.category.service.CategoryService;
import com.imyme.mine.domain.keyword.dto.CategoryKeywordsResponse;
import com.imyme.mine.domain.keyword.service.KeywordService;
import com.imyme.mine.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final KeywordService keywordService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @RequestParam(required = false) Boolean isActive
    ) {
        List<CategoryResponse> categories = categoryService.getCategories(isActive);
        return ResponseEntity.ok(ApiResponse.success(categories, "categories_fetched"));
    }

    @GetMapping("/{categoryId}/keywords")
    public ResponseEntity<CategoryKeywordsResponse> getKeywordsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(required = false) Boolean isActive
    ) {
        CategoryKeywordsResponse response = keywordService.getKeywordsByCategory(categoryId, isActive);
        return ResponseEntity.ok(response);
    }
}
