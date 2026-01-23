package com.imyme.mine.domain.auth.entity;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원 엔티티
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_oauth_id_provider", columnNames = {"oauth_id", "oauth_provider"}),
        @UniqueConstraint(name = "uk_users_nickname", columnNames = {"nickname"})
    },
    indexes = {
        @Index(name = "idx_users_email", columnList = "email")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // OAuth 고유 ID (예: kakao_123456789)
    @Column(name = "oauth_id", nullable = false, length = 100)
    private String oauthId;

    // OAuth 제공자 (KAKAO, GOOGLE, APPLE)
    @Column(name = "oauth_provider", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OAuthProviderType oauthProvider;

    // 이메일 (선택)
    @Column(name = "email", length = 255)
    private String email;

    // 닉네임 (1~20자, 중복 불가)
    @Column(name = "nickname", nullable = false, unique = true, length = 20)
    private String nickname;

    // 프로필 이미지 URL
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // 프로필 이미지 S3 키 (삭제 관리용)
    @Column(name = "profile_image_key", length = 200)
    private String profileImageKey;

    // 권한 (USER, ADMIN)
    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'USER'")
    @Builder.Default
    private RoleType role = RoleType.USER;

    // 유저 레벨 (누적 카드 수 기반)
    @Column(name = "level", nullable = false)
    @ColumnDefault("1")
    @Builder.Default
    private Integer level = 1;

    // 누적 생성 학습 카드 수
    @Column(name = "total_card_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer totalCardCount = 0;

    // 현재 보유 학습 카드 수 (삭제 제외)
    @Column(name = "active_card_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer activeCardCount = 0;

    // 연속 접속일
    @Column(name = "consecutive_days", nullable = false)
    @ColumnDefault("1")
    @Builder.Default
    private Integer consecutiveDays = 1;

    // PvP 승리 횟수
    @Column(name = "win_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer winCount = 0;

    // 마지막 로그인 일시
    @Column(name = "last_login_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime lastLoginAt;

    // 가입 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // 수정 일시
    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    // 탈퇴 일시 (Soft Delete)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 생성 시점에 자동으로 timestamp 설정
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.lastLoginAt == null) {
            this.lastLoginAt = now;
        }
    }

    // 수정 시점에 자동으로 updated_at 갱신
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 로그인 시 마지막 접속일 갱신 및 연속 접속일 계산
    public void updateLastLogin() {
        LocalDateTime now = LocalDateTime.now();

        if (this.lastLoginAt != null) {
            LocalDate lastDate = this.lastLoginAt.toLocalDate();
            LocalDate today = now.toLocalDate();

            // 마지막 로그인이 어제라면 -> 연속 접속 +1
            if (lastDate.equals(today.minusDays(1))) {
                this.consecutiveDays++;
            }
            // 마지막 로그인이 어제보다 더 과거라면 (오늘도 아니고 어제도 아님) -> 1로 초기화
            else if (lastDate.isBefore(today.minusDays(1))) {
                this.consecutiveDays = 1;
            }
            // 오늘 이미 로그인했다면(lastDate equals today) -> 변경 없음
        } else {
            // 최초 로그인 시
            this.consecutiveDays = 1;
        }

        this.lastLoginAt = now;
    }

    // 레벨 재계산 (누적 카드 수 기반) -> 레벨업 조건: 5, 15, 30, 50, 75... (+5씩 증가)
    public void recalculateLevel() {
        int threshold = 0;
        int newLevel = 1;

        while (threshold < this.totalCardCount) {
            threshold += (newLevel * 5);
            if (threshold <= this.totalCardCount) {
                newLevel++;
            }
        }

        this.level = newLevel;
    }

    // 누적 카드 수 증가 (카드 생성 시)
    public void incrementTotalCardCount() {
        this.totalCardCount++;
        recalculateLevel();
    }

    // 활성 카드 수 증가 (카드 생성 시)
    public void incrementActiveCardCount() {
        this.activeCardCount++;
    }

    // 활성 카드 수 감소 (카드 삭제 시)
    public void decrementActiveCardCount() {
        if (this.activeCardCount > 0) {
            this.activeCardCount--;
        }
    }

    // PvP 승리 횟수 증가
    public void incrementWinCount() {
        this.winCount++;
    }
}
