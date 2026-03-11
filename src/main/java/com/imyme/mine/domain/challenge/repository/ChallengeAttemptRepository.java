package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChallengeAttemptRepository extends JpaRepository<ChallengeAttempt, Long> {

    /** 중복 참여 확인 */
    boolean existsByChallengeIdAndUserId(Long challengeId, Long userId);

    /** 내 도전 기록 조회 */
    Optional<ChallengeAttempt> findByChallengeIdAndUserId(Long challengeId, Long userId);

    /** 특정 상태의 제출 목록 조회 */
    List<ChallengeAttempt> findByChallengeIdAndStatus(Long challengeId, ChallengeAttemptStatus status);

    /** 분석 큐 일괄 발행용 — UPLOADED 상태, 제출 시각 순 정렬 (22:12 스케줄러) */
    List<ChallengeAttempt> findByChallengeIdAndStatusOrderBySubmittedAtAsc(
            Long challengeId, ChallengeAttemptStatus status
    );
}