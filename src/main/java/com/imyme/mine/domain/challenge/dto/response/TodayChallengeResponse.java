package com.imyme.mine.domain.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 오늘의 챌린지 조회 응답 (GET /challenges/today)
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodayChallengeResponse(
        Long id,
        KeywordDto keyword,
        LocalDate challengeDate,
        LocalDateTime startAt,
        LocalDateTime endAt,
        ChallengeStatus status,
        int participantCount,
        MyParticipation myParticipation,
        String message
) {
    @Builder
    public record KeywordDto(Long id, String name) {}

    /**
     * 내 참여 정보
     * - null: 미참여
     * - score/rank: COMPLETED 전 null
     */
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyParticipation(
            Long attemptId,
            ChallengeAttemptStatus status,
            Integer score,
            Integer rank
    ) {}
}