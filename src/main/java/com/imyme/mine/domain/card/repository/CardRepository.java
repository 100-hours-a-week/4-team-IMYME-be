package com.imyme.mine.domain.card.repository;

import com.imyme.mine.domain.card.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Card 엔티티 데이터 접근 레이어
 *
 * [ JpaRepository 상속 ]
 * - 기본 CRUD 메서드 자동 제공: save(), findById(), findAll(), delete() 등
 * - 페이징/정렬 지원: findAll(Pageable)
 *
 * [ @SQLRestriction 자동 적용 ]
 * - Card 엔티티에 @SQLRestriction("deleted_at IS NULL") 설정
 * - 모든 조회 쿼리에 자동으로 WHERE deleted_at IS NULL 추가
 * - 별도의 soft delete 필터링 로직 불필요
 */
@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    /**
     * 특정 사용자의 특정 카드 조회
     *
     * [ 사용 목적 ]
     * - 카드 수정/삭제 시 소유권 검증
     * - user_id와 card_id로 동시에 조회하여 권한 확인
     *
     * [ 쿼리 자동 생성 ]
     * SELECT * FROM cards
     * WHERE id = ? AND user_id = ? AND deleted_at IS NULL
     *
     * @param id 카드 ID
     * @param userId 사용자 ID
     * @return 조건에 맞는 카드 (없으면 Optional.empty())
     */
    Optional<Card> findByIdAndUserId(Long id, Long userId);
}