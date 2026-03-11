package com.imyme.mine.domain.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * 챌린지 랭킹 조회 응답 (GET /challenges/{id}/rankings)
 */
@Builder
public record ChallengeRankingResponse(
        ChallengeInfo challenge,
        List<RankingItem> rankings,
        Pagination pagination,
        MyRank myRank
) {
    @Builder
    public record ChallengeInfo(
            Long id,
            String keywordName,
            LocalDate challengeDate,
            ChallengeStatus status,
            int participantCount
    ) {}

    @Builder
    public record RankingItem(
            Integer rank,
            Long userId,
            String nickname,
            String profileImageUrl,
            Integer score,
            boolean isMe
    ) {}

    @Builder
    public record Pagination(
            int currentPage,
            int totalPages,
            long totalCount,
            int size,
            boolean hasNext,
            boolean hasPrevious
    ) {}

    /**
     * 내 순위 정보 (참여하지 않았으면 null)
     */
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyRank(
            Integer rank,
            Integer score,
            Double percentile
    ) {}
}