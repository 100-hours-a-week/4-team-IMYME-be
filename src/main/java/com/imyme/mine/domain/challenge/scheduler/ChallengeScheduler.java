package com.imyme.mine.domain.challenge.scheduler;

import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.global.config.ChallengeMqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 챌린지 파이프라인 배치 스케줄러
 *
 * <pre>
 * 00:05  — 내일 챌린지 레코드 생성 (SCHEDULED)
 * 22:00  — 오늘 챌린지 OPEN
 * 22:10  — 오늘 챌린지 CLOSED (제출 마감)
 * 22:12  — 오늘 챌린지 ANALYZING + UPLOADED 제출 일괄 MQ 발행
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeScheduler {

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final KeywordRepository keywordRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ChallengeMqProperties mqProperties;

    private static final Random RANDOM = new Random();

    // -------------------------------------------------------------------------
    // 00:05 — 내일 챌린지 생성
    // -------------------------------------------------------------------------

    /**
     * 내일 챌린지 레코드 생성
     *
     * <p>활성 키워드 중 랜덤 선택하여 내일 날짜의 챌린지를 SCHEDULED 상태로 생성.
     * {@code challenge_date} UNIQUE 제약으로 중복 실행 시 INSERT 건너뜀(멱등성).
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void createTomorrowChallenge() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        if (challengeRepository.existsByChallengeDate(tomorrow)) {
            log.info("[Challenge] 내일 챌린지 이미 존재 - date={}", tomorrow);
            return;
        }

        List<Keyword> activeKeywords = keywordRepository.findAllWithCategoryByIsActive(true);
        if (activeKeywords.isEmpty()) {
            log.warn("[Challenge] 활성 키워드 없음 — 챌린지 생성 건너뜀");
            return;
        }

        Keyword keyword = activeKeywords.get(RANDOM.nextInt(activeKeywords.size()));

        Challenge challenge = Challenge.builder()
                .keyword(keyword)
                .keywordText(keyword.getName())
                .challengeDate(tomorrow)
                .startAt(LocalDateTime.of(tomorrow, LocalTime.of(22, 0)))
                .endAt(LocalDateTime.of(tomorrow, LocalTime.of(22, 9, 59)))
                .status(ChallengeStatus.SCHEDULED)
                .build();

        challengeRepository.save(challenge);
        log.info("[Challenge] 내일 챌린지 생성 완료 - date={}, keyword={}", tomorrow, keyword.getName());
    }

    // -------------------------------------------------------------------------
    // 22:00 — 챌린지 OPEN
    // -------------------------------------------------------------------------

    /**
     * 오늘 SCHEDULED 챌린지를 OPEN으로 전환
     *
     * <p>오늘 날짜 + SCHEDULED 상태인 챌린지가 없으면 로그만 기록 후 종료.
     */
    @Scheduled(cron = "0 0 22 * * *")
    @Transactional
    public void openChallenge() {
        challengeRepository
                .findByChallengeDateAndStatus(LocalDate.now(), ChallengeStatus.SCHEDULED)
                .ifPresentOrElse(
                        challenge -> {
                            challenge.open();
                            log.info("[Challenge] OPEN 전환 완료 - challengeId={}", challenge.getId());
                        },
                        () -> log.warn("[Challenge] OPEN 대상 챌린지 없음 - date={}", LocalDate.now())
                );
    }

    // -------------------------------------------------------------------------
    // 22:10 — 챌린지 CLOSED
    // -------------------------------------------------------------------------

    /**
     * OPEN 챌린지를 CLOSED로 전환 (신규 제출 차단)
     *
     * <p>OPEN 상태 챌린지가 없으면 로그만 기록 후 종료.
     */
    @Scheduled(cron = "0 10 22 * * *")
    @Transactional
    public void closeChallenge() {
        challengeRepository
                .findByStatus(ChallengeStatus.OPEN)
                .ifPresentOrElse(
                        challenge -> {
                            challenge.close();
                            log.info("[Challenge] CLOSED 전환 완료 - challengeId={}", challenge.getId());
                        },
                        () -> log.warn("[Challenge] CLOSED 대상 챌린지 없음")
                );
    }

    // -------------------------------------------------------------------------
    // 22:12 — AI 분석 큐 전송
    // -------------------------------------------------------------------------

    /**
     * CLOSED 챌린지를 ANALYZING으로 전환 후 UPLOADED 제출 일괄 MQ 발행
     *
     * <p>각 제출의 {@code audioUrl}을 포함한 피드백 요청을 AI 서버로 발행.
     * MQ 발행 실패 시 예외를 던져 트랜잭션 롤백 후 다음 배치에서 재시도.
     */
    @Scheduled(cron = "0 12 22 * * *")
    @Transactional
    public void startAnalyzing() {
        challengeRepository
                .findByStatus(ChallengeStatus.CLOSED)
                .ifPresentOrElse(
                        challenge -> {
                            challenge.startAnalyzing();

                            List<ChallengeAttempt> attempts = challengeAttemptRepository
                                    .findByChallengeIdAndStatusOrderBySubmittedAtAsc(
                                            challenge.getId(), ChallengeAttemptStatus.UPLOADED
                                    );

                            for (ChallengeAttempt attempt : attempts) {
                                rabbitTemplate.convertAndSend(
                                        mqProperties.getExchange(),
                                        mqProperties.getRouting().getFeedbackRequest(),
                                        Map.of(
                                                "attemptId", attempt.getId(),
                                                "challengeId", challenge.getId(),
                                                "audioUrl", attempt.getAudioUrl()
                                        )
                                );
                            }

                            log.info("[Challenge] ANALYZING 전환 완료 - challengeId={}, 발행 건수={}",
                                    challenge.getId(), attempts.size());
                        },
                        () -> log.warn("[Challenge] ANALYZING 대상 챌린지 없음")
                );
    }
}