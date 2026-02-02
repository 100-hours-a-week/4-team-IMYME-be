package com.imyme.mine.domain.card.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 학습 시도 AI 피드백 엔티티
 * - CardAttempt와 1:1 관계 (@MapsId 사용)
 * - feedback_json: JSONB 타입으로 상세 피드백 저장
 */
@Entity
@Table(name = "card_feedbacks")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CardFeedback {

    @Id
    @Column(name = "attempt_id")
    private Long attemptId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "attempt_id")
    private CardAttempt attempt;

    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    @Column(nullable = false)
    private Short level;

    @Column(name = "feedback_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String feedbackJson;

    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;
}
