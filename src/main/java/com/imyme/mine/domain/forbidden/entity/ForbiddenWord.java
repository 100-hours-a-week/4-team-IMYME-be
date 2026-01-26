package com.imyme.mine.domain.forbidden.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 금지어 엔티티
 * - 닉네임 등에서 사용할 수 없는 단어 목록
 */
@Entity
@Table(
    name = "forbidden_words",
    indexes = @Index(name = "idx_forbidden_type", columnList = "type")
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ForbiddenWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 금지어 (중복 불가)
    @Column(name = "word", nullable = false, unique = true, length = 50)
    private String word;

    // 금지어 유형 (적용 범위)
    @Enumerated(EnumType.STRING) // DB에는 'COMMON' 문자열로 저장
    @Column(name = "type", nullable = false, length = 20)
    @ColumnDefault("'COMMON'") // DDL 생성 시 기본값
    private ForbiddenWordType type;

    // 생성 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.type == null) {
            this.type = ForbiddenWordType.COMMON;
        }
    }
}
