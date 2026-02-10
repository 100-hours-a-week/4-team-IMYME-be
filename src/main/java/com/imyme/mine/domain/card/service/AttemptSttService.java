package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptSttService {

    private final CardAttemptRepository cardAttemptRepository;

    @Transactional
    public void recordSttSuccess(Long attemptId, String sttText) {
        CardAttempt attempt = cardAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));
        attempt.recordSttResult(sttText);
        log.info("STT 처리 성공 - attemptId: {}, 텍스트 길이: {}", attemptId, sttText.length());
    }

    @Transactional
    public void recordSttFailure(Long attemptId, String errorCode) {
        CardAttempt attempt = cardAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));
        attempt.fail(errorCode);
        log.info("STT 처리 실패 상태 저장 - attemptId: {}, errorCode: {}", attemptId, errorCode);
    }
}
