package com.imyme.mine.domain.keyword.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CategoryWithKeywordsResponse(
    @JsonProperty("category_id") Long categoryId,
    @JsonProperty("category_name") String categoryName,
    List<KeywordSimpleResponse> keywords
) {
    public CategoryWithKeywordsResponse {
        keywords = List.copyOf(keywords);
    }

    public record KeywordSimpleResponse(
        Long id,
        String name
    ) {}
}