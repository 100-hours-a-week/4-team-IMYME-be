package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.card.dto.AttemptCreateRequest;
import com.imyme.mine.domain.card.dto.AttemptCreateResponse;
import com.imyme.mine.domain.card.dto.AttemptDetailResponse;
import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptService {

    private final CardRepository cardRepository;
    private final CardAttemptRepository cardAttemptRepository;

    private static final int MAX_ATTEMPTS_PER_CARD = 5;
    private static final Duration UPLOAD_EXPIRATION = Duration.ofMinutes(10);

    @Transactional
    public AttemptCreateResponse createAttempt(Long userId, Long cardId, AttemptCreateRequest request) {
        log.debug("시도 생성 시작 - userId: {}, cardId: {}", userId, cardId);

        Card card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        long attemptCount = cardAttemptRepository.countByCardId(card.getId());
        if (attemptCount >= MAX_ATTEMPTS_PER_CARD) {
            throw new BusinessException(ErrorCode.MAX_ATTEMPTS_EXCEEDED);
        }

        Short nextAttemptNo = calculateNextAttemptNo(card.getId());

        CardAttempt attempt = CardAttempt.builder()
            .card(card)
            .attemptNo(nextAttemptNo)
            .status(AttemptStatus.PENDING)
            .durationSeconds(request.durationSeconds())
            .build();

        CardAttempt savedAttempt = cardAttemptRepository.save(attempt);

        LocalDateTime expiresAt = LocalDateTime.now().plus(UPLOAD_EXPIRATION);

        log.info("시도 생성 완료 - attemptId: {}, attemptNo: {}", savedAttempt.getId(), savedAttempt.getAttemptNo());

        return AttemptCreateResponse.of(savedAttempt, expiresAt);
    }

    @Transactional(readOnly = true)
    public AttemptDetailResponse getAttemptDetail(Long userId, Long cardId, Long attemptId) {
        log.debug("시도 상세 조회 - userId: {}, cardId: {}, attemptId: {}", userId, cardId, attemptId);

        Card card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        CardAttempt attempt = cardAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (!attempt.getCard().getId().equals(card.getId())) {
            throw new BusinessException(ErrorCode.INVALID_CARD_ATTEMPT_MISMATCH);
        }

        return buildAttemptDetailResponse(attempt);
    }

    private Short calculateNextAttemptNo(Long cardId) {
        Short maxAttemptNo = cardAttemptRepository.findMaxAttemptNoByCardId(cardId);
        return (maxAttemptNo == null) ? 1 : (short) (maxAttemptNo + 1);
    }

    private AttemptDetailResponse buildAttemptDetailResponse(CardAttempt attempt) {
        return switch (attempt.getStatus()) {
            case PENDING -> {
                LocalDateTime expiresAt = attempt.getCreatedAt().plus(UPLOAD_EXPIRATION);
                yield AttemptDetailResponse.fromPending(attempt, expiresAt);
            }
            case UPLOADED -> AttemptDetailResponse.fromUploaded(attempt);
            case PROCESSING -> AttemptDetailResponse.fromProcessing(attempt);
            case COMPLETED -> AttemptDetailResponse.fromCompleted(attempt);
            case FAILED -> AttemptDetailResponse.fromFailed(attempt);
        };
    }
}