package com.imyme.mine.global.sse;

import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SSE 스트림 서비스
 * - 토큰 발급 (소유권 검증 포함)
 * - 구독 처리 (Race Condition 방어)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SseService {

    private final SseTokenService sseTokenService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final CardAttemptRepository cardAttemptRepository;

    /**
     * SSE 스트림 토큰 발급
     * - cardId + userId + attemptId 3중 소유권 검증 후 1회용 토큰 발급
     * - 이미 완료된 상태여도 토큰 발급 허용 (subscribe에서 즉시 이벤트 전송)
     *
     * @param userId    요청자 ID
     * @param cardId    카드 ID
     * @param attemptId 시도 ID
     * @return 30초 유효 1회용 토큰
     */
    public String issueStreamToken(Long userId, Long cardId, Long attemptId) {
        CardAttempt attempt = cardAttemptRepository.findByIdWithCardAndUser(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (!attempt.getCard().getId().equals(cardId)
                || !attempt.getCard().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_CARD_ATTEMPT_MISMATCH);
        }

        return sseTokenService.issueToken(attemptId, userId);
    }

    /**
     * SSE 스트림 구독
     * - Race Condition 방어: 구독 시점에 DB 상태를 먼저 확인
     *   → 이미 COMPLETED/FAILED/EXPIRED: 즉시 이벤트 전송 후 emitter 완료
     *   → PROCESSING/UPLOADED/PENDING: SseEmitterRegistry에 등록하고 SoloService emit 대기
     *
     * @param attemptId 시도 ID (토큰 검증 완료 후 호출됨)
     * @return SseEmitter
     */
    public SseEmitter subscribe(Long attemptId) {
        AttemptStatus status = cardAttemptRepository.findStatusById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (status == AttemptStatus.COMPLETED
                || status == AttemptStatus.FAILED
                || status == AttemptStatus.EXPIRED) {
            log.debug("[SSE] 구독 시점에 이미 완료 - 즉시 전송: attemptId={}, status={}", attemptId, status);
            return emitImmediately(attemptId, status.name());
        }

        log.debug("[SSE] 에미터 등록 (분석 진행 중): attemptId={}, currentStatus={}", attemptId, status);
        return sseEmitterRegistry.register(attemptId);
    }

    /**
     * 즉시 이벤트 전송 후 완료
     * - 구독 시점에 이미 결과가 확정된 경우 (Race Condition 방어)
     */
    private SseEmitter emitImmediately(Long attemptId, String status) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event()
                .name("status-update")
                .data(Map.of("status", status)));
            emitter.complete();
        } catch (IOException e) {
            log.warn("[SSE] 즉시 이벤트 전송 실패: attemptId={}", attemptId, e);
            emitter.completeWithError(e);
        }
        return emitter;
    }
}