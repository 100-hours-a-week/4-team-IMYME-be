package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 챌린지 도전 기록 Repository
 */
public interface ChallengeAttemptRepository extends JpaRepository<ChallengeAttempt, Long> {

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
}