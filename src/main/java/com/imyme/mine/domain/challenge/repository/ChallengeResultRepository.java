package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeResultRepository extends JpaRepository<ChallengeResult, Long> {

    /** attemptId = PK(@MapsId)이므로 findById와 동일하나 명시적 사용 */
    Optional<ChallengeResult> findByAttemptId(Long attemptId);
}