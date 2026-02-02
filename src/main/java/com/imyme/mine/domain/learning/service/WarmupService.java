package com.imyme.mine.domain.learning.service;

import com.imyme.mine.domain.ai.client.AiServerClient;
import com.imyme.mine.domain.learning.dto.WarmupResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STT 워밍업 서비스
 * - GPU 콜드 스타트 방지
 * - Rate Limiting: 1분당 1회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarmupService {

    private final AiServerClient aiServerClient;

    // Rate Limiting: userId -> 마지막 호출 시간
    private final Map<Long, LocalDateTime> rateLimitMap = new ConcurrentHashMap<>();

    private static final int RATE_LIMIT_SECONDS = 60;

    /**
     * STT 워밍업 요청
     * - Rate Limiting 체크
     * - CompletableFuture로 비동기 AI 서버 호출
     */
    public WarmupResponse warmup(Long userId) {
        log.debug("STT 워밍업 요청 - userId: {}", userId);

        // Rate Limiting 체크
        LocalDateTime lastCallTime = rateLimitMap.get(userId);
        LocalDateTime now = LocalDateTime.now();

        if (lastCallTime != null && lastCallTime.plusSeconds(RATE_LIMIT_SECONDS).isAfter(now)) {
            log.warn("Rate Limit 초과 - userId: {}, 마지막 호출: {}", userId, lastCallTime);
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        // Rate Limit 갱신
        rateLimitMap.put(userId, now);

        // CompletableFuture로 비동기 워밍업 호출
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.debug("비동기 워밍업 실행 - userId: {}", userId);
                aiServerClient.warmup();
                log.info("비동기 워밍업 완료 - userId: {}", userId);
            } catch (Exception e) {
                // 워밍업 실패는 무시하고 로그만 기록
                log.error("비동기 워밍업 실패 - userId: {}, error: {}", userId, e.getMessage());
            }
        });

        log.info("STT 워밍업 시작 - userId: {}", userId);
        return WarmupResponse.warmingUp();
    }
}