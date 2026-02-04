package com.imyme.mine.domain.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.ai.dto.solo.SoloFeedback;
import com.imyme.mine.domain.ai.dto.solo.SoloResult;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.entity.CardFeedback;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardFeedbackRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoloFeedbackSaveService {

    private static final String SOLO_MODEL_VERSION = "gemini-3-pro-preview";

    private final CardAttemptRepository attemptRepository;
    private final CardFeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(Long attemptId, SoloResult result) {
        if (result == null) {
            log.warn("Solo feedback result is null - attemptId: {}", attemptId);
            throw new BusinessException(ErrorCode.FEEDBACK_SAVE_FAILED);
        }
        if (result.feedback() == null) {
            log.warn("Solo feedback is null - attemptId: {}", attemptId);
            throw new BusinessException(ErrorCode.FEEDBACK_SAVE_FAILED);
        }

        if (feedbackRepository.existsById(attemptId)) {
            log.info("Solo feedback already saved - attemptId: {}", attemptId);
            return;
        }

        // CardAttempt 조회 (존재 확인 + @MapsId 연관관계 설정용)
        CardAttempt attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        String feedbackJson = convertSoloFeedbackToJson(result.feedback());

        // @MapsId를 사용하므로 attemptId만 설정 (attempt는 JPA가 관리)
        CardFeedback newFeedback = CardFeedback.builder()
            .attempt(attempt)
            .overallScore(result.overallScore())
            .level(result.level().shortValue())
            .feedbackJson(feedbackJson)
            .modelVersion(SOLO_MODEL_VERSION)
            .build();

        feedbackRepository.save(newFeedback);

        // 피드백 저장 완료 시 상태를 COMPLETED로 전환 + 카드 통계 갱신
        Card card = attempt.getCard();
        boolean wasGhost = card.getAttemptCount() == null || card.getAttemptCount() == 0;
        attempt.complete(attempt.getSttText());
        card.completeAttempt(result.level().shortValue());
        if (wasGhost) {
            card.getUser().incrementActiveCardCount();
        }

        log.info("Solo feedback saved - attemptId: {}, score: {}, level: {}",
            attemptId, result.overallScore(), result.level());
    }

    private String convertSoloFeedbackToJson(SoloFeedback feedback) {
        try {
            Map<String, Object> feedbackMap = new HashMap<>();
            feedbackMap.put("summary", feedback.summarize());
            String keywords = (feedback.keyword() == null || feedback.keyword().isEmpty())
                ? ""
                : String.join(", ", feedback.keyword());
            feedbackMap.put("keywords", keywords);
            feedbackMap.put("facts", feedback.facts());
            feedbackMap.put("understanding", feedback.understanding());
            feedbackMap.put("socratic_feedback", feedback.personalized());

            return objectMapper.writeValueAsString(feedbackMap);

        } catch (Exception e) {
            log.error("Solo feedback JSON conversion failed", e);
            throw new BusinessException(ErrorCode.FEEDBACK_SAVE_FAILED);
        }
    }
}
