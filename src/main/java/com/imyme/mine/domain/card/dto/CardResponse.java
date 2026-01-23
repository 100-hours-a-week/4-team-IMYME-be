package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.card.entity.Card;

import java.time.LocalDateTime;

public record CardResponse(

    Long id,

    @JsonProperty("category_id")
    Long categoryId,

    @JsonProperty("category_name")
    String categoryName,

    @JsonProperty("keyword_id")
    Long keywordId,

    @JsonProperty("keyword_name")
    String keywordName,

    String title,

    @JsonProperty("best_level")
    Integer bestLevel,

    @JsonProperty("attempt_count")
    Integer attemptCount,

    @JsonProperty("created_at")
    LocalDateTime createdAt,

    @JsonProperty("updated_at")
    LocalDateTime updatedAt

) {

    public static CardResponse from(Card card) {
        return new CardResponse(
            card.getId(),
            card.getCategory().getId(),
            card.getCategory().getName(),
            card.getKeyword().getId(),
            card.getKeyword().getName(),
            card.getTitle(),
            card.getBestLevel(),
            card.getAttemptCount(),
            card.getCreatedAt(),
            card.getUpdatedAt()
        );
    }
}