package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChallengeRankingRepository extends JpaRepository<ChallengeRanking, Long> {

    /** 랭킹 목록 조회 (idx_rankings_view 커버링 인덱스 활용) */
    List<ChallengeRanking> findByChallengeIdOrderByRankNoAsc(Long challengeId);

    /** 내 등수 조회 */
    Optional<ChallengeRanking> findByChallengeIdAndUserId(Long challengeId, Long userId);

    /** 배치 중복 방지 확인 */
    boolean existsByChallengeId(Long challengeId);
}