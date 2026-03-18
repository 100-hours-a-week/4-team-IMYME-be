package com.imyme.mine.domain.challenge.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.challenge.dto.message.ChallengeRankingCompletedDto;
import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeRanking;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRankingRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 챌린지 토너먼트 랭킹 완료 MQ Consumer
 *
 * <p>AI 서버가 토너먼트를 마치면 {@code challenge.ranking.completed} 큐로 메시지를 발행.
 * 이 Consumer는:
 * <ol>
 *   <li>ChallengeRanking Bulk INSERT (1위~N위)</li>
 *   <li>Challenge.complete() → status COMPLETED + participantCount + resultSummaryJson</li>
 *   <li>Redis pairs:job:{id}:* 키 삭제</li>
 *   <li>커밋 후 CHALLENGE_OVERALL_RESULT 알림 발송</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCompletedConsumer {

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final ChallengeRankingRepository challengeRankingRepository;
    private final UserRepository userRepository;
    private final NotificationCreatorService notificationCreatorService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 토너먼트 랭킹 완료 처리
     *
     * @param channel RabbitMQ 채널 (Ack/Nack용)
     * @param message RabbitMQ 원본 메시지
     */
    @RabbitListener(queues = "#{challengeMqProperties.queue.rankingCompleted}")
    @Transactional
    public void handleRankingCompleted(Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("[Ranking MQ] 토너먼트 랭킹 완료 수신: deliveryTag={}", deliveryTag);

            ChallengeRankingCompletedDto dto =
                    objectMapper.readValue(message.getBody(), ChallengeRankingCompletedDto.class);

            processRankingCompleted(dto);

            channel.basicAck(deliveryTag, false);
            log.info("[Ranking MQ] 처리 완료 (Ack): deliveryTag={}", deliveryTag);

        } catch (Exception e) {
            log.error("[Ranking MQ] 처리 실패: deliveryTag={}", deliveryTag, e);
            try {
                channel.basicNack(deliveryTag, false, false);
                log.warn("[Ranking MQ] Nack → DLQ: deliveryTag={}", deliveryTag);
            } catch (IOException ioException) {
                log.error("[Ranking MQ] Nack 실패", ioException);
            }
        }
    }

    private void processRankingCompleted(ChallengeRankingCompletedDto dto) {
        Long challengeId = dto.getChallengeId();
        List<ChallengeRankingCompletedDto.RankedItem> rankedList = dto.getRankedList();

        if (challengeId == null || rankedList == null || rankedList.isEmpty()) {
            log.warn("[Ranking MQ] 필수 필드 누락: challengeId={}, rankedList size={}",
                    challengeId, rankedList == null ? 0 : rankedList.size());
            return;
        }

        // 멱등성 보호: 이미 랭킹이 저장된 경우 스킵
        if (challengeRankingRepository.existsByChallengeId(challengeId)) {
            log.info("[Ranking MQ] 스킵 (이미 처리됨): challengeId={}", challengeId);
            return;
        }

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalStateException("Challenge not found: " + challengeId));

        // 유저 데이터 배치 조회 (닉네임 스냅샷 + 프로필 이미지)
        List<Long> userIds = rankedList.stream()
                .map(ChallengeRankingCompletedDto.RankedItem::getUserId)
                .collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // ChallengeRanking Bulk INSERT
        List<ChallengeRanking> rankings = new ArrayList<>(rankedList.size());
        for (int i = 0; i < rankedList.size(); i++) {
            ChallengeRankingCompletedDto.RankedItem item = rankedList.get(i);
            User user = userMap.get(item.getUserId());

            ChallengeAttempt attempt = challengeAttemptRepository.getReferenceById(item.getAttemptId());

            rankings.add(ChallengeRanking.builder()
                    .challenge(challenge)
                    .user(user)
                    .attempt(attempt)
                    .rankNo(i + 1)
                    .score(item.getScore() != null ? item.getScore() : 0)
                    .userNickname(user != null ? user.getNickname() : item.getNickname())
                    .userProfileImageUrl(user != null ? user.getProfileImageUrl() : null)
                    .build());
        }
        challengeRankingRepository.saveAll(rankings);

        // Challenge COMPLETED 전환
        ChallengeAttempt bestAttempt = challengeAttemptRepository.getReferenceById(
                rankedList.get(0).getAttemptId());
        String resultSummaryJson = buildSummaryJson(rankedList, userMap);
        challenge.complete(bestAttempt, resultSummaryJson, rankedList.size());

        // Redis pairs:job:{id}:* 키 삭제 (pending_count는 DECR 완료 시 이미 0)
        Set<String> pairsKeys = stringRedisTemplate.keys("pairs:job:" + challengeId + ":*");
        if (pairsKeys != null && !pairsKeys.isEmpty()) {
            stringRedisTemplate.delete(pairsKeys);
        }

        log.info("[Ranking MQ] COMPLETED 전환 완료: challengeId={}, 참가자={}", challengeId, rankedList.size());

        // 커밋 후 CHALLENGE_OVERALL_RESULT 알림 발송
        List<Long> notifyUserIds = userIds;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long userId : notifyUserIds) {
                    notificationCreatorService.create(
                            userId,
                            NotificationType.CHALLENGE_OVERALL_RESULT,
                            "챌린지 최종 랭킹이 나왔어요!",
                            "내 순위를 확인해보세요.",
                            challengeId,
                            "CHALLENGE"
                    );
                }
                log.info("[Ranking MQ] OVERALL_RESULT 알림 발송 완료: challengeId={}, 대상={}",
                        challengeId, notifyUserIds.size());
            }
        });
    }

    private String buildSummaryJson(List<ChallengeRankingCompletedDto.RankedItem> rankedList,
                                    Map<Long, User> userMap) {
        List<Map<String, Object>> top3 = new ArrayList<>();
        int limit = Math.min(3, rankedList.size());
        for (int i = 0; i < limit; i++) {
            ChallengeRankingCompletedDto.RankedItem item = rankedList.get(i);
            User user = userMap.get(item.getUserId());
            Map<String, Object> entry = new HashMap<>();
            entry.put("rank", i + 1);
            entry.put("user_id", item.getUserId());
            entry.put("nickname", user != null ? user.getNickname() : item.getNickname());
            entry.put("score", item.getScore());
            entry.put("attempt_id", item.getAttemptId());
            top3.add(entry);
        }

        double avgScore = rankedList.stream()
                .mapToInt(i -> i.getScore() != null ? i.getScore() : 0)
                .average().orElse(0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("total_participants", rankedList.size());
        summary.put("average_score", Math.round(avgScore * 10.0) / 10.0);

        Map<String, Object> result = new HashMap<>();
        result.put("top3", top3);
        result.put("summary", summary);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("[Ranking MQ] resultSummaryJson 직렬화 실패", e);
            return "{}";
        }
    }
}