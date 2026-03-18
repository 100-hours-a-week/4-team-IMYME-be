package com.imyme.mine.domain.challenge.dto.message;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 서버 → Spring: 챌린지 토너먼트 랭킹 완료 MQ DTO
 *
 * <p>AI 서버가 {@code challenge.ranking.completed} 큐로 발행하는 메시지.
 * rankedList는 1위부터 순서대로 정렬되며, 각 항목에 개인 피드백이 포함됨.
 * 채점 및 피드백 생성은 토너먼트 완료 후 AI 서버가 일괄 수행.
 */
@Getter
@NoArgsConstructor
public class ChallengeRankingCompletedDto {

    /** "job:{challengeId}" 형식 */
    private String jobId;

    private Long challengeId;

    /** 1위부터 순서대로 정렬된 참가자 목록 */
    private List<RankedItem> rankedList;

    @Getter
    @NoArgsConstructor
    public static class RankedItem {
        private Long attemptId;
        private Long userId;
        /** 토너먼트 기반 정규화 점수 (0~100) */
        private Integer score;
        /** AI 서버에서 제공한 닉네임 스냅샷 */
        private String nickname;
        /** AI 생성 개인 피드백 JSON 문자열 */
        private String feedbackJson;
        /** 분석에 사용된 모델 버전 */
        private String modelVersion;
    }
}