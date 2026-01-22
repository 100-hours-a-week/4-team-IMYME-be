package com.imyme.mine.domain.card.entity;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.keyword.entity.Keyword;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 학습 카드 엔티티
 *
 * [ 설계 의도 ]
 * - 사용자가 특정 카테고리/키워드에 대해 생성하는 학습 단위입니다.
 * - Soft Delete 방식으로 삭제하여 데이터 복구 가능성을 유지합니다.
 * - best_level, attempt_count는 Attempt 생성/완료 시 갱신됩니다.
 *
 * [ 상태 흐름 ]
 * 1. 카드 생성: attempt_count=0, best_level=0
 * 2. 학습 시도 완료: attempt_count++, best_level 갱신 (더 높은 레벨일 때)
 * 3. 카드 삭제: deleted_at 설정 (Soft Delete)
 */
@Entity
@Table(
    name = "cards",
    indexes = {
        // 사용자별 카드 목록 조회 성능 최적화
        @Index(name = "idx_cards_user_id", columnList = "user_id"),
        // 키워드별 카드 조회 성능 최적화
        @Index(name = "idx_cards_keyword_id", columnList = "keyword_id")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA는 기본 생성자 필요, protected로 외부 직접 생성 방지
@AllArgsConstructor(access = AccessLevel.PROTECTED) // Builder 패턴 사용을 위해 필요
@SQLRestriction("deleted_at IS NULL")  // 조회 시 삭제된 데이터 자동 제외 (Hibernate 6.3+)
@SQLDelete(sql = "UPDATE cards SET deleted_at = NOW() WHERE id = ?")  // delete() 호출 시 Soft Delete 수행
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 카드 소유자 (N:1 관계)
     * - FetchType.LAZY: 카드 조회 시 User를 즉시 로딩하지 않음 (성능 최적화)
     * - 실제 User 데이터가 필요할 때 추가 쿼리 실행
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 카테고리 (N:1 관계)
     * - 카드가 속한 대분류 (예: 운영체제, 네트워크)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * 키워드 (N:1 관계)
     * - 카드의 세부 주제 (예: 프로세스, TCP/IP)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    /**
     * 카드 제목
     * - 사용자가 직접 입력 (1~20자)
     * - 학습 내용을 식별하기 위한 이름
     */
    @Column(name = "title", nullable = false, length = 20)
    private String title;

    /**
     * 최고 달성 레벨 (0~5)
     * - 0: 아직 학습 시도 없음
     * - 1~5: AI 채점 결과 중 최고 레벨
     * - Attempt 완료 시 더 높은 레벨이면 갱신
     */
    @Column(name = "best_level", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer bestLevel = 0;

    /**
     * 총 학습 시도 횟수
     * - Attempt가 COMPLETED 상태가 될 때마다 증가
     */
    @Column(name = "attempt_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer attemptCount = 0;

    // ========== 시간 관련 필드 ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    /**
     * Soft Delete용 삭제 시각
     * - null이면 활성 상태
     * - 값이 있으면 삭제된 상태 (@SQLRestriction으로 자동 필터링)
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ========== JPA 콜백 메서드 ==========

    /**
     * 엔티티 저장 전 호출
     * - created_at, updated_at 자동 설정
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 엔티티 수정 전 호출
     * - updated_at 자동 갱신
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 카드 제목 수정
     * @param newTitle 새로운 제목 (1~20자)
     */
    public void updateTitle(String newTitle) {
        this.title = newTitle;
    }

    /**
     * 학습 시도 완료 시 호출
     * - 시도 횟수 증가
     * - 더 높은 레벨 달성 시 best_level 갱신
     *
     * @param achievedLevel 이번 시도에서 달성한 레벨
     */
    public void completeAttempt(int achievedLevel) {
        this.attemptCount++;
        if (achievedLevel > this.bestLevel) {
            this.bestLevel = achievedLevel;
        }
    }
}