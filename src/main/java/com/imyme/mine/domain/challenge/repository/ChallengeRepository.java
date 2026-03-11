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

/**
 * 챌린지 Repository
 */
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    // ===== Read API 용 (keyword fetch join) =====

    /**
     * 오늘의 챌린지 조회 (keyword fetch join)
     */
    @Query("""
            SELECT c FROM Challenge c
            JOIN FETCH c.keyword
            WHERE c.challengeDate = :date
            """)
    Optional<Challenge> findByChallengeDateWithKeyword(@Param("date") LocalDate date);

    /**
     * 챌린지 단건 조회 (keyword fetch join) - rankings 조회용
     */
    @Query("""
            SELECT c FROM Challenge c
            JOIN FETCH c.keyword
            WHERE c.id = :id
            """)
    Optional<Challenge> findByIdWithKeyword(@Param("id") Long id);

    /**
     * 히스토리 조회 - 전체 (status 필터 없음, cursor 기반)
     * cursorDate/cursorId는 sentinel 값(LocalDate.MAX / Long.MAX_VALUE)으로 첫 페이지 처리
     */
    @Query("""
            SELECT c FROM Challenge c
            JOIN FETCH c.keyword
            WHERE c.challengeDate < :cursorDate
               OR (c.challengeDate = :cursorDate AND c.id < :cursorId)
            ORDER BY c.challengeDate DESC, c.id DESC
            """)
    List<Challenge> findHistoryPage(
            @Param("cursorDate") LocalDate cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    /**
     * 히스토리 조회 - status 필터 포함 (cursor 기반)
     */
    @Query("""
            SELECT c FROM Challenge c
            JOIN FETCH c.keyword
            WHERE c.status = :status
              AND (c.challengeDate < :cursorDate
                OR (c.challengeDate = :cursorDate AND c.id < :cursorId))
            ORDER BY c.challengeDate DESC, c.id DESC
            """)
    List<Challenge> findHistoryPageByStatus(
            @Param("status") ChallengeStatus status,
            @Param("cursorDate") LocalDate cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    /**
     * 히스토리 조회 - 내가 참여한 것만 (cursor 기반)
     */
    @Query("""
            SELECT c FROM Challenge c
            JOIN FETCH c.keyword
            WHERE EXISTS (
                SELECT 1 FROM ChallengeAttempt a
                WHERE a.challenge = c AND a.user.id = :userId
            )
              AND (c.challengeDate < :cursorDate
                OR (c.challengeDate = :cursorDate AND c.id < :cursorId))
            ORDER BY c.challengeDate DESC, c.id DESC
            """)
    List<Challenge> findParticipatedHistoryPage(
            @Param("userId") Long userId,
            @Param("cursorDate") LocalDate cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    /**
     * 히스토리 조회 - 내가 참여한 것만 + status 필터 (cursor 기반)
     */
    @Query("""
            SELECT c FROM Challenge c
            JOIN FETCH c.keyword
            WHERE c.status = :status
              AND EXISTS (
                SELECT 1 FROM ChallengeAttempt a
                WHERE a.challenge = c AND a.user.id = :userId
              )
              AND (c.challengeDate < :cursorDate
                OR (c.challengeDate = :cursorDate AND c.id < :cursorId))
            ORDER BY c.challengeDate DESC, c.id DESC
            """)
    List<Challenge> findParticipatedHistoryPageByStatus(
            @Param("userId") Long userId,
            @Param("status") ChallengeStatus status,
            @Param("cursorDate") LocalDate cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // ===== 스케줄러 용 =====

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