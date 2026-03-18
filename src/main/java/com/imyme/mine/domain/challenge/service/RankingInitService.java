package com.imyme.mine.domain.challenge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.global.config.ChallengeMqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 챌린지 토너먼트 랭킹 초기화 서비스
 *
 * <p>개별 채점이 모두 완료({@code pending_count == 0})된 시점에
 * {@link ChallengeAsyncService}에서 호출됨.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>Redis ZSet {@code challenge:{id}:ranking}에서 점수 순위 조회</li>
 *   <li>각 항목을 {@code pairs:job:{id}:level:0} List에 RPUSH</li>
 *   <li>홀수일 경우 마지막 항목을 {@code level:1}로 바이(bye) 처리</li>
 *   <li>2개씩 짝지어 {@code challenge.pairs.eval} 큐로 토너먼트 미션 발행</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingInitService {

    private static final String REDIS_RANKING_KEY = "challenge:%d:ranking";
    private static final String REDIS_PAIRS_KEY = "pairs:job:%d:level:%d";
    private static final Duration PAIRS_TTL = Duration.ofHours(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ChallengeMqProperties mqProperties;
    private final ObjectMapper objectMapper;

    /**
     * 토너먼트 랭킹 초기화
     *
     * @param challengeId 완료된 챌린지 ID
     */
    public void initRanking(Long challengeId) {
        String rankingKey = String.format(REDIS_RANKING_KEY, challengeId);

        // score DESC 순으로 {attemptId, score} 조회
        Set<ZSetOperations.TypedTuple<String>> entries =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(rankingKey, 0, -1);

        if (entries == null || entries.isEmpty()) {
            log.warn("[RankingInit] 채점 완료된 항목 없음 (모두 FAIL?): challengeId={}", challengeId);
            return;
        }

        // JSON 직렬화
        List<String> items = new ArrayList<>(entries.size());
        for (ZSetOperations.TypedTuple<String> entry : entries) {
            try {
                Map<String, Object> item = new HashMap<>();
                item.put("attemptId", Long.parseLong(entry.getValue()));
                item.put("score", entry.getScore() != null ? entry.getScore().intValue() : 0);
                items.add(objectMapper.writeValueAsString(item));
            } catch (JsonProcessingException e) {
                log.error("[RankingInit] 항목 직렬화 실패: entry={}", entry.getValue(), e);
            }
        }

        if (items.isEmpty()) {
            log.warn("[RankingInit] 직렬화 성공 항목 없음: challengeId={}", challengeId);
            return;
        }

        // Redis List에 RPUSH
        String baseKey = String.format(REDIS_PAIRS_KEY, challengeId, 0);
        for (String item : items) {
            stringRedisTemplate.opsForList().rightPush(baseKey, item);
        }
        stringRedisTemplate.expire(baseKey, PAIRS_TTL);

        // 홀수 처리: 짝 없는 마지막 항목 → level:1 bye
        if (items.size() % 2 == 1) {
            String byeKey = String.format(REDIS_PAIRS_KEY, challengeId, 1);
            stringRedisTemplate.opsForList().rightPush(byeKey, items.get(items.size() - 1));
            stringRedisTemplate.expire(byeKey, PAIRS_TTL);
            log.info("[RankingInit] bye 처리: challengeId={}, byeItem={}", challengeId, items.get(items.size() - 1));
        }

        // 2개씩 짝지어 q.pairs.eval 발행
        int pairCount = 0;
        for (int i = 0; i + 1 < items.size(); i += 2) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("jobId", "job:" + challengeId);
            payload.put("challengeId", challengeId);
            payload.put("level", 0);
            payload.put("arrayA", items.get(i));
            payload.put("arrayB", items.get(i + 1));

            rabbitTemplate.convertAndSend(
                    mqProperties.getExchange(),
                    mqProperties.getQueue().getPairsEval(),
                    payload
            );
            pairCount++;
        }

        log.info("[RankingInit] 토너먼트 초기화 완료: challengeId={}, 참가자={}, 페어={}",
                challengeId, items.size(), pairCount);
    }
}