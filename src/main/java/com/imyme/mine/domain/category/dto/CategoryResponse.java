package com.imyme.mine.domain.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.category.entity.Category;

public record CategoryResponse(
    Long id,
    String name,
    @JsonProperty("display_order") Integer displayOrder,
    @JsonProperty("is_active") Boolean isActive
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getDisplayOrder(),
            category.getIsActive()
        );
    }
}
