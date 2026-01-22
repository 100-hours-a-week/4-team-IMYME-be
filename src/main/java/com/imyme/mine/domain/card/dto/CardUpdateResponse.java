package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.card.entity.Card;

import java.time.LocalDateTime;

/**
 * 카드 제목 수정 응답 DTO
 *
 * [ 최소 응답 원칙 ]
 * - 수정된 필드와 관련 정보만 반환
 * - 불필요한 데이터 전송 방지
 * - 클라이언트가 필요한 정보: id, title, updated_at
 */
public record CardUpdateResponse(

    Long id,

    String title,

    @JsonProperty("updated_at")
    LocalDateTime updatedAt

) {

    /**
     * Card 엔티티 → CardUpdateResponse DTO 변환
     *
     * @param card 수정된 Card 엔티티
     * @return CardUpdateResponse DTO
     */
    public static CardUpdateResponse from(Card card) {
        return new CardUpdateResponse(
            card.getId(),
            card.getTitle(),
            card.getUpdatedAt()
        );
    }
}