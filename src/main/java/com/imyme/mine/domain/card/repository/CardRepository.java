package com.imyme.mine.domain.card.repository;

import com.imyme.mine.domain.card.entity.Card;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.keyword
        JOIN FETCH c.user
        WHERE c.id = :cardId
        AND c.user.id = :userId
        AND c.deletedAt IS NULL
        """)
    Optional<Card> findByIdAndUserIdWithKeyword(
        @Param("cardId") Long cardId,
        @Param("userId") Long userId
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        ORDER BY c.createdAt DESC, c.id DESC
        """)
    List<Card> findCardsRecentFirst(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        AND (c.createdAt < :cursorCreatedAt
             OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))
        ORDER BY c.createdAt DESC, c.id DESC
        """)
    List<Card> findCardsRecentAfterCursor(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
        @Param("cursorId") Long cursorId,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        ORDER BY c.createdAt ASC, c.id ASC
        """)
    List<Card> findCardsOldestFirst(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        AND (c.createdAt > :cursorCreatedAt
             OR (c.createdAt = :cursorCreatedAt AND c.id > :cursorId))
        ORDER BY c.createdAt ASC, c.id ASC
        """)
    List<Card> findCardsOldestAfterCursor(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
        @Param("cursorId") Long cursorId,
        Pageable pageable
    );
}
