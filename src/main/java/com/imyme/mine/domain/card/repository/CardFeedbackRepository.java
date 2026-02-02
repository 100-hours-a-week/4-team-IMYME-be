package com.imyme.mine.domain.card.repository;

import com.imyme.mine.domain.card.entity.CardFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CardFeedbackRepository extends JpaRepository<CardFeedback, Long> {

    /**
     * attemptId로 피드백 조회
     * CardFeedback의 PK가 attemptId이므로 findById와 동일
     */
    Optional<CardFeedback> findByAttemptId(Long attemptId);

    /**
     * 특정 키워드에 대한 모든 피드백 조회
     * - Knowledge 배치 작업에서 사용
     * - CardFeedback -> CardAttempt -> Card -> Keyword 조인
     */
    @Query("SELECT cf FROM CardFeedback cf " +
           "JOIN FETCH cf.attempt ca " +
           "JOIN FETCH ca.card c " +
           "WHERE c.keyword.id = :keywordId")
    List<CardFeedback> findByKeywordId(@Param("keywordId") Long keywordId);

    /**
     * 특정 키워드에 대한 기간별 피드백 조회
     * - Knowledge 배치 작업에서 날짜 범위 지정 시 사용
     */
    @Query("SELECT cf FROM CardFeedback cf " +
           "JOIN FETCH cf.attempt ca " +
           "JOIN FETCH ca.card c " +
           "WHERE c.keyword.id = :keywordId " +
           "AND ca.createdAt BETWEEN :startDate AND :endDate")
    List<CardFeedback> findByKeywordIdAndCreatedAtBetween(
        @Param("keywordId") Long keywordId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
