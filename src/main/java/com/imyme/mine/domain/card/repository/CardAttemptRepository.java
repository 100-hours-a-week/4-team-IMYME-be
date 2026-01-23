package com.imyme.mine.domain.card.repository;

import com.imyme.mine.domain.card.entity.CardAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardAttemptRepository extends JpaRepository<CardAttempt, Long> {

    @Query("SELECT ca FROM CardAttempt ca WHERE ca.card.id = :cardId ORDER BY ca.attemptNo DESC")
    List<CardAttempt> findByCardIdOrderByAttemptNoDesc(@Param("cardId") Long cardId);

    @Query("SELECT MAX(ca.attemptNo) FROM CardAttempt ca WHERE ca.card.id = :cardId")
    Short findMaxAttemptNoByCardId(@Param("cardId") Long cardId);

    long countByCardId(Long cardId);
}