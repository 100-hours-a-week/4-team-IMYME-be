package com.imyme.mine.domain.challenge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.challenge.dto.message.ChallengeFeedbackResponseDto;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 챌린지 STT 응답 처리 서비스
 *
 * <p>MQ Consumer({@link com.imyme.mine.domain.challenge.messaging.ChallengeMqConsumer})에서 호출.
 * STT 결과만 수신하며, 채점/피드백은 토너먼트 완료 후 {@code RankingCompletedConsumer}에서 처리.
 *
 * <p>트랜잭션 흐름:
 * <ol>
 *   <li>ChallengeAttempt: sttText 저장, status → PROCESSING</li>
 *   <li>DB 커밋 후 → Redis RPUSH participants + DECR pending_count</li>
 *   <li>pending_count == 0 → RankingInitService.initRanking() 트리거</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeAsyncService {

    private static final String REDIS_PENDING_KEY = "challenge:%d:pending_count";
    private static final String REDIS_PARTICIPANTS_KEY = "challenge:%d:participants";
    private static final Duration PARTICIPANTS_TTL = Duration.ofHours(4);

    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RankingInitService rankingInitService;
    private final ObjectMapper objectMapper;

    /**
     * STT 응답 처리
     *
     * <p>채점/피드백은 토너먼트 완료 후 {@code RankingCompletedConsumer}에서 처리.
     *
     * @param dto AI 서버로부터 수신한 STT 결과
     */
    @Transactional
    public void handleFeedbackResponse(ChallengeFeedbackResponseDto dto) {
        Long attemptId = dto.getAttemptId();
        Long challengeId = dto.getChallengeId();

        if (attemptId == null || challengeId == null) {
            log.warn("[Challenge MQ] 필수 필드 누락: attemptId={}, challengeId={}", attemptId, challengeId);
            return;
        }

        ChallengeAttempt attempt = challengeAttemptRepository
                .findByIdAndChallengeIdWithUser(attemptId, challengeId)
                .orElse(null);

        if (attempt == null) {
            log.warn("[Challenge MQ] attempt 없음 — 무시: attemptId={}, challengeId={}", attemptId, challengeId);
            return;
        }

        // Idempotent guard: UPLOADED 상태에서만 처리 (중복 메시지 방어)
        if (attempt.getStatus() != ChallengeAttemptStatus.UPLOADED) {
            log.info("[Challenge MQ] 스킵 (이미 처리됨): attemptId={}, status={}",
                    attemptId, attempt.getStatus());
            return;
        }

        attempt.startProcessing();

        boolean success = !"FAIL".equalsIgnoreCase(dto.getStatus());

        if (success) {
            attempt.saveSttText(dto.getSttText());
            log.info("[Challenge MQ] STT 완료: attemptId={}", attemptId);
        } else {
            attempt.fail();
            log.warn("[Challenge MQ] STT 실패 (FAIL): attemptId={}", attemptId);
        }

        // 커밋 후 Redis 작업 (DB 롤백 시 실행 안 됨)
        Long userId = attempt.getUser() != null ? attempt.getUser().getId() : null;
        String sttText = dto.getSttText();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 1. Redis HSET: AI 서버가 attemptId로 sttText를 조회할 수 있도록 Hash 저장
                //    field=attemptId, value={"userId":..., "sttText":"..."}
                if (success && userId != null) {
                    try {
                        Map<String, Object> participant = new HashMap<>();
                        participant.put("userId", userId);
                        participant.put("sttText", sttText);
                        String hashKey = String.format(REDIS_PARTICIPANTS_KEY, challengeId);
                        stringRedisTemplate.opsForHash().put(
                                hashKey,
                                String.valueOf(attemptId),
                                objectMapper.writeValueAsString(participant)
                        );
                        stringRedisTemplate.expire(hashKey, PARTICIPANTS_TTL);
                    } catch (JsonProcessingException e) {
                        log.error("[Challenge MQ] participants HSET 직렬화 실패: attemptId={}", attemptId, e);
                    }
                }

                // 2. pending_count DECR — 마지막 처리 완료 시 토너먼트 초기화
                Long remaining = stringRedisTemplate.opsForValue()
                        .decrement(String.format(REDIS_PENDING_KEY, challengeId));

                log.info("[Challenge MQ] pending_count DECR: challengeId={}, remaining={}",
                        challengeId, remaining);

                if (remaining != null && remaining <= 0) {
                    log.info("[Challenge MQ] 모든 STT 완료 → 토너먼트 초기화 시작: challengeId={}", challengeId);
                    rankingInitService.initRanking(challengeId);
                }
            }
        });
    }
}