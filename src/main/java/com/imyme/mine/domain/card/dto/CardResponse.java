package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.card.entity.Card;

import java.time.LocalDateTime;

/**
 * 카드 생성/조회 응답 DTO
 *
 * [ 응답 형식 ]
 * - API 명세에 따라 snake_case로 JSON 직렬화
 * - 중첩 객체 대신 flat한 구조 사용 (category_id, category_name 등)
 *
 * [ 정적 팩토리 메서드 패턴 ]
 * - from(Card card): Entity → DTO 변환
 * - 변환 로직을 DTO 내부에 캡슐화
 * - Controller/Service 코드 간결화
 */
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

    /**
     * Card 엔티티 → CardResponse DTO 변환
     *
     * [ LAZY 로딩 주의 ]
     * - card.getCategory().getName() 호출 시 추가 쿼리 발생
     * - 성능 최적화가 필요하면 JPQL JOIN FETCH 사용
     * - 현재는 카드 생성 직후 응답이므로 영속성 컨텍스트 내에서 안전
     *
     * @param card 변환할 Card 엔티티
     * @return CardResponse DTO
     */
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
