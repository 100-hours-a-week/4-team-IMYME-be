package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.card.entity.CardAttempt;

import java.time.LocalDateTime;

public record AttemptResponse(

    Long id,

    @JsonProperty("attempt_no")
    Short attemptNo,

    String status,

    @JsonProperty("audio_url")
    String audioUrl,

    @JsonProperty("duration_seconds")
    Integer durationSeconds,

    @JsonProperty("created_at")
    LocalDateTime createdAt,

    @JsonProperty("finished_at")
    LocalDateTime finishedAt

) {

    public static AttemptResponse from(CardAttempt attempt) {
        return new AttemptResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getAudioUrl(),
            attempt.getDurationSeconds(),
            attempt.getCreatedAt(),
            attempt.getFinishedAt()
        );
    }
}
