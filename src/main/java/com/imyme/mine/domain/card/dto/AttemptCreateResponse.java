package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.card.entity.CardAttempt;

import java.time.LocalDateTime;

public record AttemptCreateResponse(

    @JsonProperty("card_id")
    Long cardId,

    @JsonProperty("attempt_id")
    Long attemptId,

    @JsonProperty("attempt_no")
    Short attemptNo,

    String status,

    @JsonProperty("created_at")
    LocalDateTime createdAt,

    @JsonProperty("expires_at")
    LocalDateTime expiresAt,

    String message

) {

    public static AttemptCreateResponse of(CardAttempt attempt, LocalDateTime expiresAt) {
        return new AttemptCreateResponse(
            attempt.getCard().getId(),
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getCreatedAt(),
            expiresAt,
            "시도가 생성되었습니다. 10분 내에 업로드를 완료해주세요."
        );
    }
}