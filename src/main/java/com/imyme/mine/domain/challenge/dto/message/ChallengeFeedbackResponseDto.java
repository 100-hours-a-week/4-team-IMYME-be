package com.imyme.mine.domain.challenge.dto.message;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서버 → Spring: 챌린지 채점 결과 응답 MQ DTO
 *
 * <p>AI 서버가 {@code challenge.feedback.response} 큐로 발행하는 메시지.
 * status = "FAIL"인 경우 score / feedbackJson / sttText는 null일 수 있음.
 */
@Getter
@NoArgsConstructor
public class ChallengeFeedbackResponseDto {

    private Long attemptId;
    private Long challengeId;

    /** "SUCCESS" 또는 "FAIL" */
    private String status;

    /** AI 평가 점수 (0~100), FAIL 시 null */
    private Integer score;

    /** AI 피드백 JSON 문자열, FAIL 시 null */
    private String feedbackJson;

    /** 사용된 모델 버전 (예: "v1.0") */
    private String modelVersion;

    /** STT 변환 텍스트, FAIL 시 null */
    private String sttText;
}