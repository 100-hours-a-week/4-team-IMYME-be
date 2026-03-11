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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChallengeScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ChallengeSchedulerTest {

    @Mock ChallengeRepository challengeRepository;
    @Mock ChallengeAttemptRepository challengeAttemptRepository;
    @Mock KeywordRepository keywordRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock ChallengeMqProperties mqProperties;
    @Mock ChallengeMqProperties.Routing routing;
    @Mock ChallengeMqProperties.Queue queue;

    @InjectMocks
    ChallengeScheduler scheduler;

    @BeforeEach
    void setUpMqProperties() {
        lenient().when(mqProperties.getExchange()).thenReturn("challenge.direct");
        lenient().when(mqProperties.getRouting()).thenReturn(routing);
        lenient().when(routing.getFeedbackRequest()).thenReturn("challenge.feedback.request");
        lenient().when(mqProperties.getQueue()).thenReturn(queue);
    }

    // =========================================================================
    // 00:05 — 내일 챌린지 생성
    // =========================================================================

    @Test
    @DisplayName("내일 챌린지 생성 - 존재하지 않으면 활성 키워드 중 랜덤 선택 후 저장")
    void createTomorrowChallenge_savesChallenge() {
        Keyword keyword = mock(Keyword.class);
        when(keyword.getName()).thenReturn("발표");

        when(challengeRepository.existsByChallengeDate(any())).thenReturn(false);
        when(keywordRepository.findAllWithCategoryByIsActive(true)).thenReturn(List.of(keyword));

        scheduler.createTomorrowChallenge();

        verify(challengeRepository).save(any(Challenge.class));
    }

    @Test
    @DisplayName("내일 챌린지 생성 - 이미 존재하면 저장 건너뜀 (멱등성)")
    void createTomorrowChallenge_skipsIfAlreadyExists() {
        when(challengeRepository.existsByChallengeDate(any())).thenReturn(true);

        scheduler.createTomorrowChallenge();

        verify(challengeRepository, never()).save(any());
        verify(keywordRepository, never()).findAllWithCategoryByIsActive(anyBoolean());
    }

    @Test
    @DisplayName("내일 챌린지 생성 - 활성 키워드 없으면 저장 건너뜀")
    void createTomorrowChallenge_skipsIfNoActiveKeyword() {
        when(challengeRepository.existsByChallengeDate(any())).thenReturn(false);
        when(keywordRepository.findAllWithCategoryByIsActive(true)).thenReturn(List.of());

        scheduler.createTomorrowChallenge();

        verify(challengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("내일 챌린지 생성 - challengeDate가 내일 날짜로 설정됨")
    void createTomorrowChallenge_setsCorrectDate() {
        Keyword keyword = mock(Keyword.class);
        when(keyword.getName()).thenReturn("토론");

        when(challengeRepository.existsByChallengeDate(any())).thenReturn(false);
        when(keywordRepository.findAllWithCategoryByIsActive(true)).thenReturn(List.of(keyword));

        scheduler.createTomorrowChallenge();

        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(challengeRepository).save(captor.capture());

        assertThat(captor.getValue().getChallengeDate()).isEqualTo(LocalDate.now().plusDays(1));
    }

    // =========================================================================
    // 22:00 — 챌린지 OPEN
    // =========================================================================

    @Test
    @DisplayName("챌린지 OPEN - 오늘 SCHEDULED 챌린지 존재 시 open() 호출")
    void openChallenge_callsOpenOnChallenge() {
        Challenge challenge = mock(Challenge.class);
        when(challengeRepository.findByChallengeDateAndStatus(LocalDate.now(), ChallengeStatus.SCHEDULED))
                .thenReturn(Optional.of(challenge));

        scheduler.openChallenge();

        verify(challenge).open();
    }

    @Test
    @DisplayName("챌린지 OPEN - 대상 없으면 예외 없이 종료")
    void openChallenge_noTargetNoException() {
        when(challengeRepository.findByChallengeDateAndStatus(any(), any())).thenReturn(Optional.empty());

        scheduler.openChallenge(); // 예외 없이 정상 완료
    }

    // =========================================================================
    // 22:10 — 챌린지 CLOSED
    // =========================================================================

    @Test
    @DisplayName("챌린지 CLOSED - OPEN 챌린지 존재 시 close() 호출")
    void closeChallenge_callsCloseOnChallenge() {
        Challenge challenge = mock(Challenge.class);
        when(challengeRepository.findByStatus(ChallengeStatus.OPEN))
                .thenReturn(Optional.of(challenge));

        scheduler.closeChallenge();

        verify(challenge).close();
    }

    @Test
    @DisplayName("챌린지 CLOSED - 대상 없으면 예외 없이 종료")
    void closeChallenge_noTargetNoException() {
        when(challengeRepository.findByStatus(ChallengeStatus.OPEN)).thenReturn(Optional.empty());

        scheduler.closeChallenge(); // 예외 없이 정상 완료
    }

    // =========================================================================
    // 22:12 — ANALYZING + MQ 발행
    // =========================================================================

    @Test
    @DisplayName("ANALYZING 전환 - CLOSED 챌린지 존재 시 startAnalyzing() 호출")
    void startAnalyzing_callsStartAnalyzingOnChallenge() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getId()).thenReturn(1L);
        when(challengeRepository.findByStatus(ChallengeStatus.CLOSED))
                .thenReturn(Optional.of(challenge));
        when(challengeAttemptRepository.findByChallengeIdAndStatusOrderBySubmittedAtAsc(1L, ChallengeAttemptStatus.UPLOADED))
                .thenReturn(List.of());

        scheduler.startAnalyzing();

        verify(challenge).startAnalyzing();
    }

    @Test
    @DisplayName("ANALYZING 전환 - UPLOADED 제출 건수만큼 MQ 메시지 발행")
    void startAnalyzing_publishesMqMessagePerAttempt() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getId()).thenReturn(10L);

        ChallengeAttempt attempt1 = mock(ChallengeAttempt.class);
        when(attempt1.getId()).thenReturn(100L);
        when(attempt1.getAudioUrl()).thenReturn("s3://bucket/audio1.m4a");

        ChallengeAttempt attempt2 = mock(ChallengeAttempt.class);
        when(attempt2.getId()).thenReturn(101L);
        when(attempt2.getAudioUrl()).thenReturn("s3://bucket/audio2.m4a");

        when(challengeRepository.findByStatus(ChallengeStatus.CLOSED))
                .thenReturn(Optional.of(challenge));
        when(challengeAttemptRepository.findByChallengeIdAndStatusOrderBySubmittedAtAsc(10L, ChallengeAttemptStatus.UPLOADED))
                .thenReturn(List.of(attempt1, attempt2));

        scheduler.startAnalyzing();

        verify(rabbitTemplate, times(2)).convertAndSend(
                eq("challenge.direct"),
                eq("challenge.feedback.request"),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("ANALYZING 전환 - 제출 없어도 예외 없이 종료")
    void startAnalyzing_noAttempts_noException() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getId()).thenReturn(1L);
        when(challengeRepository.findByStatus(ChallengeStatus.CLOSED))
                .thenReturn(Optional.of(challenge));
        when(challengeAttemptRepository.findByChallengeIdAndStatusOrderBySubmittedAtAsc(1L, ChallengeAttemptStatus.UPLOADED))
                .thenReturn(List.of());

        scheduler.startAnalyzing(); // 예외 없이 정상 완료

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("ANALYZING 전환 - 대상 없으면 예외 없이 종료")
    void startAnalyzing_noTargetNoException() {
        when(challengeRepository.findByStatus(ChallengeStatus.CLOSED)).thenReturn(Optional.empty());

        scheduler.startAnalyzing(); // 예외 없이 정상 완료
    }
}