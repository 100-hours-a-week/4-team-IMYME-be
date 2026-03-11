package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChallengeAttemptRepository extends JpaRepository<ChallengeAttempt, Long> {

    // ===== Read API 용 =====

    /**
     * 특정 챌린지에 대한 유저의 참여 기록 조회
     */
    @Query("""
            SELECT a FROM ChallengeAttempt a
            WHERE a.challenge.id = :challengeId AND a.user.id = :userId
            """)
    Optional<ChallengeAttempt> findByChallengeIdAndUserId(
            @Param("challengeId") Long challengeId,
            @Param("userId") Long userId
    );

    /**
     * 여러 챌린지에 대한 유저의 참여 기록 bulk 조회 (히스토리 맵 조립용)
     */
    @Query("""
            SELECT a FROM ChallengeAttempt a
            WHERE a.challenge.id IN :challengeIds AND a.user.id = :userId
            """)
    List<ChallengeAttempt> findByChallengeIdInAndUserId(
            @Param("challengeIds") List<Long> challengeIds,
            @Param("userId") Long userId
    );

    // ===== 스케줄러 용 =====

    /** 중복 참여 확인 */
    boolean existsByChallengeIdAndUserId(Long challengeId, Long userId);

    /** 특정 상태의 제출 목록 조회 */
    List<ChallengeAttempt> findByChallengeIdAndStatus(Long challengeId, ChallengeAttemptStatus status);

    /** 분석 큐 일괄 발행용 — UPLOADED 상태, 제출 시각 순 정렬 (22:12 스케줄러) */
    List<ChallengeAttempt> findByChallengeIdAndStatusOrderBySubmittedAtAsc(
            Long challengeId, ChallengeAttemptStatus status
    );
}