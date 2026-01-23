package com.imyme.mine.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 사용자 세션 엔티티
 * - Refresh Token 관리
 * - RT Rotation 지원
 */
@Entity
@Table(
    name = "user_sessions",
    indexes = {
        @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_user_sessions_refresh_token", columnList = "refresh_token")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Refresh Token (암호화 저장 권장)
    @Column(name = "refresh_token", nullable = false, unique = true, length = 500)
    private String refreshToken;

    // Refresh Token 만료 시간
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 생성 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // 수정 일시 (RT Rotation 시)
    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Refresh Token 갱신 (RT Rotation)
    public void updateRefreshToken(String newRefreshToken, LocalDateTime newExpiresAt) {
        this.refreshToken = newRefreshToken;
        this.expiresAt = newExpiresAt;
    }

    // Refresh Token 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
