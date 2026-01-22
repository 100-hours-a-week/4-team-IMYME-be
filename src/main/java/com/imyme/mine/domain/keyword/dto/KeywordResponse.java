package com.imyme.mine.domain.keyword.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.keyword.entity.Keyword;

public record KeywordResponse(
    Long id,
    String name,
    @JsonProperty("display_order") Integer displayOrder,
    @JsonProperty("is_active") Boolean isActive
) {
    public static KeywordResponse from(Keyword keyword) {
        return new KeywordResponse(
            keyword.getId(),
            keyword.getName(),
            keyword.getDisplayOrder(),
            keyword.getIsActive()
        );
    }
}