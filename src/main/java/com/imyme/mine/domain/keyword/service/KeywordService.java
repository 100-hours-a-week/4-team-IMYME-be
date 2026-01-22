package com.imyme.mine.domain.keyword.service;

import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import com.imyme.mine.domain.keyword.dto.CategoryKeywordsResponse;
import com.imyme.mine.domain.keyword.dto.CategoryWithKeywordsResponse;
import com.imyme.mine.domain.keyword.dto.CategoryWithKeywordsResponse.KeywordSimpleResponse;
import com.imyme.mine.domain.keyword.dto.KeywordResponse;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final CategoryRepository categoryRepository;

    public CategoryKeywordsResponse getKeywordsByCategory(Long categoryId, Boolean isActive) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CATEGORY_NOT_FOUND,
                Map.of("categoryId", categoryId)
            ));

        List<KeywordResponse> keywords;
        if (isActive == null) {
            keywords = keywordRepository.findAllByCategoryIdOrderByDisplayOrderAsc(categoryId).stream()
                .map(KeywordResponse::from)
                .toList();
        } else {
            keywords = keywordRepository.findAllByCategoryIdAndIsActiveOrderByDisplayOrderAsc(categoryId, isActive).stream()
                .map(KeywordResponse::from)
                .toList();
        }

        return CategoryKeywordsResponse.of(category, keywords);
    }

    public List<CategoryWithKeywordsResponse> getAllKeywordsGroupedByCategory(Boolean isActive) {
        List<Keyword> keywords;
        if (isActive == null) {
            keywords = keywordRepository.findAllWithCategory();
        } else {
            keywords = keywordRepository.findAllWithCategoryByIsActive(isActive);
        }

        return keywords.stream()
            .collect(Collectors.groupingBy(
                keyword -> keyword.getCategory(),
                Collectors.mapping(
                    keyword -> new KeywordSimpleResponse(keyword.getId(), keyword.getName()),
                    Collectors.toList()
                )
            ))
            .entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().getDisplayOrder().compareTo(e2.getKey().getDisplayOrder()))
            .map(entry -> new CategoryWithKeywordsResponse(
                entry.getKey().getId(),
                entry.getKey().getName(),
                entry.getValue()
            ))
            .toList();
    }
}