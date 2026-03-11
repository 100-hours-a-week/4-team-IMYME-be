package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeResultRepository extends JpaRepository<ChallengeResult, Long> {
    // attemptId = PK이므로 기본 findById 사용
}