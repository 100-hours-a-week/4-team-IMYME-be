package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    /** 날짜로 챌린지 조회 */
    Optional<Challenge> findByChallengeDate(LocalDate date);

    /** 특정 상태의 챌린지 조회 */
    Optional<Challenge> findByStatus(ChallengeStatus status);

    /** 날짜 + 상태로 챌린지 조회 (스케줄러용) */
    Optional<Challenge> findByChallengeDateAndStatus(LocalDate date, ChallengeStatus status);

    /** 지난 챌린지 목록 — COMPLETED 상태, 커서 기반 페이지 */
    @Query("""
            SELECT c FROM Challenge c
            WHERE c.status = com.imyme.mine.domain.challenge.entity.ChallengeStatus.COMPLETED
              AND (:cursor IS NULL OR c.challengeDate < :cursor)
            ORDER BY c.challengeDate DESC
            """)
    List<Challenge> findCompletedBeforeCursor(
            @Param("cursor") LocalDate cursor,
            Pageable pageable
    );

    /** 내일 챌린지 존재 여부 확인 (생성 멱등성) */
    boolean existsByChallengeDate(LocalDate date);
}