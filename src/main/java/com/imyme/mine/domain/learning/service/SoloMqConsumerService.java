package com.imyme.mine.domain.learning.service;

import com.imyme.mine.domain.ai.dto.solo.SoloFeedback;
import com.imyme.mine.domain.ai.dto.solo.SoloResult;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.service.AttemptSttService;
import com.imyme.mine.domain.knowledge.service.KnowledgeBaseService;
import com.imyme.mine.domain.learning.messaging.SoloMqPublisher;
import com.imyme.mine.domain.learning.messaging.dto.SoloFeedbackRequestDto;
import com.imyme.mine.domain.learning.messaging.dto.SoloFeedbackResponseDto;
import com.imyme.mine.domain.learning.messaging.dto.SoloSttResponseDto;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Solo MQ Consumer 비즈니스 로직
 * - STT Response: STT 결과 저장 + Feedback Request 발행
 * - Feedback Response: 피드백 저장 or 실패 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloMqConsumerService {

    private final AttemptSttService attemptSttService;
    private final SoloFeedbackSaveService feedbackSaveService;
    private final SoloMqPublisher soloMqPublisher;
    private final CardAttemptRepository cardAttemptRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * STT Response 처리 (AI → Main)
     * - SUCCESS: STT 텍스트 저장 + Feedback Request 발행
     * - FAIL: 시도 실패 상태 저장
     */
    @Transactional
    public void handleSttResponse(SoloSttResponseDto dto) {
        Long attemptId = dto.attemptId();
        log.info("[Solo MQ] STT Response 처리 - attemptId: {}, status: {}", attemptId, dto.status());

        if ("FAIL".equalsIgnoreCase(dto.status())) {
            log.warn("[Solo MQ] STT 실패 - attemptId: {}, error: {}", attemptId, dto.error());
            attemptSttService.recordSttFailure(attemptId, "STT_RECOGNIZE_FAILED");
            return;
        }

        // STT 성공: 텍스트 저장
        attemptSttService.recordSttSuccess(attemptId, dto.sttText());

        // Feedback Request 발행 (트랜잭션 내 Lazy Loading 가능)
        publishFeedbackRequest(attemptId, dto.userId(), dto.sttText());
    }

    /**
     * Feedback Response 처리 (AI → Main)
     * - SUCCESS: 피드백 저장 (SoloFeedbackSaveService 위임)
     * - FAIL: 시도 실패 상태 저장
     */
    @Transactional
    public void handleFeedbackResponse(SoloFeedbackResponseDto dto) {
        Long attemptId = dto.attemptId();
        log.info("[Solo MQ] Feedback Response 처리 - attemptId: {}, status: {}", attemptId, dto.status());

        if ("FAIL".equalsIgnoreCase(dto.status())) {
            log.warn("[Solo MQ] Feedback 실패 - attemptId: {}, error: {}", attemptId, dto.error());
            markAttemptFailed(attemptId, "AI_FEEDBACK_FAILED");
            return;
        }

        SoloFeedbackResponseDto.FeedbackDto fb = dto.feedback();
        if (fb == null) {
            log.warn("[Solo MQ] Feedback 데이터 없음 - attemptId: {}", attemptId);
            markAttemptFailed(attemptId, "AI_FEEDBACK_FAILED");
            return;
        }

        SoloFeedback soloFeedback = new SoloFeedback(
            fb.summary(), fb.keywords(), fb.facts(), fb.understanding(), fb.personalizedFeedback()
        );
        SoloResult soloResult = new SoloResult(fb.score(), null, soloFeedback);
        feedbackSaveService.save(attemptId, soloResult);
    }

    /**
     * STT 성공 후 Feedback Request 발행
     * - Card/Keyword Lazy Loading: @Transactional 컨텍스트 내에서 실행됨
     * - 발행 실패 시 시도를 FAILED 처리 (STT 텍스트는 이미 저장됨)
     */
    private void publishFeedbackRequest(Long attemptId, Long userId, String sttText) {
        try {
            CardAttempt attempt = cardAttemptRepository.findByIdWithCardAndUser(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

            Card card = attempt.getCard();
            String keywordName = card.getKeyword().getName();
            List<String> modelAnswers = knowledgeBaseService.getModelAnswersByKeyword(card.getKeyword().getId());

            SoloFeedbackRequestDto feedbackRequest = SoloFeedbackRequestDto.builder()
                .attemptId(attemptId)
                .userId(userId)
                .sttText(sttText)
                .criteria(SoloFeedbackRequestDto.CriteriaDto.builder()
                    .keyword(keywordName)
                    .modelAnswer(modelAnswers)
                    .build())
                .history(List.of())
                .timestamp(System.currentTimeMillis())
                .build();

            soloMqPublisher.publishFeedbackRequest(feedbackRequest);
            log.info("[Solo MQ] Feedback Request 발행 완료 - attemptId: {}", attemptId);

        } catch (Exception e) {
            log.error("[Solo MQ] Feedback Request 발행 실패 - attemptId: {}", attemptId, e);
            markAttemptFailed(attemptId, "AI_FEEDBACK_FAILED");
        }
    }

    private void markAttemptFailed(Long attemptId, String errorCode) {
        cardAttemptRepository.findById(attemptId).ifPresent(attempt -> {
            attempt.fail(errorCode);
            log.info("[Solo MQ] 시도 실패 상태 저장 - attemptId: {}, errorCode: {}", attemptId, errorCode);
        });
    }
}