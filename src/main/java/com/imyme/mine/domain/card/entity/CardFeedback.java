package com.imyme.mine.domain.card.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 카드 학습 시도별 AI 피드백 엔티티
 * - CardAttempt와 1:1 관계 (attempt_id가 PK이자 FK)
 * - AI 분석 결과를 JSONB 형태로 저장
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "card_feedbacks")
public class CardFeedback {

    // @Id만 선언 (GeneratedValue 없음): PK가 외래키에서 가져온 값이기 때문
    // attempt_id가 PK이자 FK 역할을 동시에 수행
    @Id
    @Column(name = "attempt_id")
    private Long attemptId;

    // @OneToOne: CardAttempt와 1:1 관계
    // @MapsId: attemptId 필드를 CardAttempt의 id와 매핑
    // - PK를 FK로 공유하는 1:1 관계 구현 방식
    // - CardAttempt의 id 값이 이 테이블의 PK로 자동 설정됨
    // @JoinColumn: FK 컬럼명 지정 및 ON DELETE CASCADE 동작
    // - foreignKey의 ConstraintMode.NO_CONSTRAINT는 JPA가 DDL 생성 시 FK 제약조건을 만들지 않게 함
    // - 실제 FK 제약조건은 DB에서 직접 관리 (migration 또는 수동 생성)
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "attempt_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private CardAttempt attempt;

    // 성취도 점수 (0~100)
    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    // 성취도 레벨 (1~5)
    // SMALLINT는 Java의 Short 타입과 매핑됨
    @Column(nullable = false)
    private Short level;

    // AI 피드백 원본 (JSONB 형태)
    // @JdbcTypeCode(SqlTypes.JSON): PostgreSQL의 JSONB 타입과 매핑
    // - String으로 저장하지만 DB에서는 JSONB로 처리됨
    // - 실제 사용 시 JSON 파싱이 필요 (Jackson ObjectMapper 등)
    @Column(name = "feedback_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String feedbackJson;

    // 프롬프트/모델 버전 (예: "v1.0", "gpt-4-turbo")
    // AI 모델이나 프롬프트 변경 시 버전 추적용
    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;

    // 피드백 생성 시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // @PrePersist: 엔티티가 처음 저장되기 직전에 자동 실행
    // - JPA 생명주기 콜백 메서드
    // - DB의 DEFAULT NOW()와 동일한 효과
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}