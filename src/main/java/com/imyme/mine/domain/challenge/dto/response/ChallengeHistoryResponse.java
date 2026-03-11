package com.imyme.mine.domain.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * 챌린지 히스토리 조회 응답 (GET /challenges)
 */
@Builder
public record ChallengeHistoryResponse(
        List<ChallengeItem> challenges,
        Meta meta
) {
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChallengeItem(
            Long id,
            String keywordName,
            LocalDate challengeDate,
            ChallengeStatus status,
            int participantCount,
            MyParticipation myParticipation
    ) {}

    /**
     * 내 참여 요약 (참여하지 않았거나 미완료면 null)
     */
    @Builder
    public record MyParticipation(
            Integer score,
            Integer rank,
            Double percentile
    ) {}

    @Builder
    public record Meta(
            int size,
            boolean hasNext,
            String nextCursor
    ) {}
}