package com.imyme.mine.domain.card.repository;

import com.imyme.mine.domain.card.entity.CardFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardFeedbackRepository extends JpaRepository<CardFeedback, Long> {

    /**
     * attemptId로 피드백 조회
     * CardFeedback의 PK가 attemptId이므로 findById와 동일
     */
    Optional<CardFeedback> findByAttemptId(Long attemptId);
}
