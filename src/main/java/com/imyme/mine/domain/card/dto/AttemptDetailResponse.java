package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.card.entity.CardAttempt;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptDetailResponse(

    @JsonProperty("card_id")
    Long cardId,

    @JsonProperty("attempt_id")
    Long attemptId,

    @JsonProperty("attempt_no")
    Short attemptNo,

    String status,

    @JsonProperty("audio_url")
    String audioUrl,

    @JsonProperty("duration_seconds")
    Integer durationSeconds,

    @JsonProperty("stt_text")
    String sttText,

    @JsonProperty("created_at")
    LocalDateTime createdAt,

    @JsonProperty("submitted_at")
    LocalDateTime submittedAt,

    @JsonProperty("finished_at")
    LocalDateTime finishedAt,

    @JsonProperty("expires_at")
    LocalDateTime expiresAt,

    String message

) {

    public static AttemptDetailResponse fromPending(CardAttempt attempt, LocalDateTime expiresAt) {
        return new AttemptDetailResponse(
            attempt.getCard().getId(),
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            null,
            null,
            null,
            attempt.getCreatedAt(),
            null,
            null,
            expiresAt,
            "업로드 대기 중입니다."
        );
    }

    public static AttemptDetailResponse fromUploaded(CardAttempt attempt) {
        return new AttemptDetailResponse(
            attempt.getCard().getId(),
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getAudioUrl(),
            attempt.getDurationSeconds(),
            null,
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            null,
            null,
            "AI 분석 대기 중입니다."
        );
    }

    public static AttemptDetailResponse fromProcessing(CardAttempt attempt) {
        return new AttemptDetailResponse(
            attempt.getCard().getId(),
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getAudioUrl(),
            attempt.getDurationSeconds(),
            null,
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            null,
            null,
            "AI 분석 중입니다. 잠시만 기다려주세요."
        );
    }

    public static AttemptDetailResponse fromCompleted(CardAttempt attempt) {
        return new AttemptDetailResponse(
            attempt.getCard().getId(),
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getAudioUrl(),
            attempt.getDurationSeconds(),
            attempt.getSttText(),
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            attempt.getFinishedAt(),
            null,
            "분석이 완료되었습니다."
        );
    }

    public static AttemptDetailResponse fromFailed(CardAttempt attempt) {
        return new AttemptDetailResponse(
            attempt.getCard().getId(),
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getAudioUrl(),
            attempt.getDurationSeconds(),
            null,
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            attempt.getFinishedAt(),
            null,
            "분석에 실패했습니다. 다시 시도해주세요."
        );
    }
}