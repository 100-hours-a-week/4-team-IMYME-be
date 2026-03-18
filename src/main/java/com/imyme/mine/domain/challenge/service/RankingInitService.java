package com.imyme.mine.domain.challenge.service;

import com.imyme.mine.global.config.ChallengeMqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 챌린지 토너먼트 랭킹 초기화 서비스
 *
 * <p>개별 STT가 모두 완료({@code pending_count == 0})된 시점에
 * {@link ChallengeAsyncService}에서 호출됨.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>Redis Hash {@code challenge:{id}:participants}에서 전체 참가자 조회
 *       (field=attemptId, value={userId,sttText} JSON)</li>
 *   <li>attemptId 오름차순 정렬 후 {@code pairs:job:{id}:level:0} List에 RPUSH</li>
 *   <li>홀수일 경우 마지막 항목을 {@code level:1}로 바이(bye) 처리</li>
 *   <li>2개씩 짝지어 {@code challenge.pairs.eval} 큐로 토너먼트 미션 발행</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingInitService {

    private static final String REDIS_PARTICIPANTS_KEY = "challenge:%d:participants";
    private static final String REDIS_PAIRS_KEY = "pairs:job:%d:level:%d";
    private static final Duration PAIRS_TTL = Duration.ofHours(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ChallengeMqProperties mqProperties;

    /**
     * 토너먼트 랭킹 초기화
     *
     * @param challengeId 완료된 챌린지 ID
     */
    public void initRanking(Long challengeId) {
        String participantsKey = String.format(REDIS_PARTICIPANTS_KEY, challengeId);

        // Hash 전체 조회 (field=attemptId string, value=JSON string)
        Map<Object, Object> participantMap = stringRedisTemplate.opsForHash().entries(participantsKey);

        if (participantMap == null || participantMap.isEmpty()) {
            log.warn("[RankingInit] 참가자 없음 (모두 FAIL?): challengeId={}", challengeId);
            return;
        }

        // attemptId 기준 오름차순 정렬 (결정론적 페어링)
        List<Long> attemptIds = participantMap.keySet().stream()
                .map(k -> Long.parseLong(k.toString()))
                .sorted()
                .collect(Collectors.toList());

        // Redis List에 RPUSH (pairs:job:{id}:level:0)
        String baseKey = String.format(REDIS_PAIRS_KEY, challengeId, 0);
        for (Long attemptId : attemptIds) {
            stringRedisTemplate.opsForList().rightPush(baseKey, String.valueOf(attemptId));
        }
        stringRedisTemplate.expire(baseKey, PAIRS_TTL);

        // 홀수 처리: 짝 없는 마지막 항목 → level:1 bye
        if (attemptIds.size() % 2 == 1) {
            Long byeAttemptId = attemptIds.get(attemptIds.size() - 1);
            String byeKey = String.format(REDIS_PAIRS_KEY, challengeId, 1);
            stringRedisTemplate.opsForList().rightPush(byeKey, String.valueOf(byeAttemptId));
            stringRedisTemplate.expire(byeKey, PAIRS_TTL);
            log.info("[RankingInit] bye 처리: challengeId={}, byeAttemptId={}", challengeId, byeAttemptId);
        }

        // 2개씩 짝지어 challenge.pairs.eval 발행
        int pairCount = 0;
        for (int i = 0; i + 1 < attemptIds.size(); i += 2) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("jobId", "job:" + challengeId);
            payload.put("challengeId", challengeId);
            payload.put("level", 0);
            payload.put("array_a", List.of(attemptIds.get(i)));
            payload.put("array_b", List.of(attemptIds.get(i + 1)));

            rabbitTemplate.convertAndSend(
                    mqProperties.getExchange(),
                    mqProperties.getQueue().getPairsEval(),
                    payload
            );
            pairCount++;
        }

        log.info("[RankingInit] 토너먼트 초기화 완료: challengeId={}, 참가자={}, 페어={}",
                challengeId, attemptIds.size(), pairCount);
    }
}