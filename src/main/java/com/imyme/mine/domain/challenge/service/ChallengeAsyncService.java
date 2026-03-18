package com.imyme.mine.domain.challenge.service;

import com.imyme.mine.domain.challenge.dto.message.ChallengeFeedbackResponseDto;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeResult;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeResultRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 챌린지 AI 채점 결과 처리 서비스
 *
 * <p>MQ Consumer({@link com.imyme.mine.domain.challenge.messaging.ChallengeMqConsumer})에서 호출.
 * 트랜잭션 경계 내에서 DB 저장 후, 커밋 성공 시 Redis 및 알림 처리.
 *
 * <p>트랜잭션 흐름:
 * <ol>
 *   <li>ChallengeResult INSERT (score, feedbackJson)</li>
 *   <li>ChallengeAttempt UPDATE (status → COMPLETED, finishedAt)</li>
 *   <li>DB 커밋 후 → Redis ZADD + DECR pending_count + 알림 발송</li>
 *   <li>pending_count == 0 → 랭킹 확정 단계 트리거 (commit 3에서 연결)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeAsyncService {

    private static final String DEFAULT_MODEL_VERSION = "v1";
    private static final String REDIS_PENDING_KEY = "challenge:%d:pending_count";
    private static final String REDIS_RANKING_KEY = "challenge:%d:ranking";

    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final ChallengeResultRepository challengeResultRepository;
    private final NotificationCreatorService notificationCreatorService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RankingInitService rankingInitService;

    /**
     * AI 채점 응답 처리
     *
     * @param dto AI 서버로부터 수신한 채점 결과
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
            ChallengeResult result = ChallengeResult.builder()
                    .attempt(attempt)
                    .score(dto.getScore())
                    .feedbackJson(dto.getFeedbackJson())
                    .modelVersion(dto.getModelVersion() != null ? dto.getModelVersion() : DEFAULT_MODEL_VERSION)
                    .build();
            challengeResultRepository.save(result);
            attempt.complete(dto.getSttText());

            log.info("[Challenge MQ] 채점 완료: attemptId={}, score={}", attemptId, dto.getScore());
        } else {
            attempt.fail();
            log.warn("[Challenge MQ] 채점 실패 (FAIL): attemptId={}", attemptId);
        }

        // 커밋 후 Redis 작업 및 알림 (DB 롤백 시 실행 안 됨)
        Long userId = attempt.getUser() != null ? attempt.getUser().getId() : null;
        int score = (success && dto.getScore() != null) ? dto.getScore() : 0;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 1. Redis ZADD: 토너먼트 랭킹에서 점수 비교에 사용 (commit 3)
                if (success) {
                    stringRedisTemplate.opsForZSet().add(
                            String.format(REDIS_RANKING_KEY, challengeId),
                            attemptId.toString(),
                            score
                    );
                }

                // 2. CHALLENGE_PERSONAL_RESULT 알림 발송
                if (userId != null && success) {
                    notificationCreatorService.create(
                            userId,
                            NotificationType.CHALLENGE_PERSONAL_RESULT,
                            "챌린지 채점이 완료됐어요!",
                            "내 결과를 확인해보세요.",
                            challengeId,
                            "CHALLENGE"
                    );
                }

                // 3. pending_count DECR — 마지막 처리 완료 시 랭킹 확정 단계로
                Long remaining = stringRedisTemplate.opsForValue()
                        .decrement(String.format(REDIS_PENDING_KEY, challengeId));

                log.info("[Challenge MQ] pending_count DECR: challengeId={}, remaining={}",
                        challengeId, remaining);

                if (remaining != null && remaining <= 0) {
                    log.info("[Challenge MQ] 모든 채점 완료 → 랭킹 초기화 시작: challengeId={}", challengeId);
                    rankingInitService.initRanking(challengeId);
                }
            }
        });
    }
}