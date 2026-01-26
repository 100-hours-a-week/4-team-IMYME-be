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
        // 닉네임 유니크 제약조건(@UniqueConstraint) 삭제 후 DB에 직접 Partial Index 생성 완료
        // @UniqueConstraint(name = "uk_users_nickname", columnNames = {"nickname"})
    },
    indexes = {
        // JPA로는 Partial Index 생성이 불가능하여 주석 처리함. DB에서 직접 생성 필요.
        // @Index(name = "idx_users_email", columnList = "email")
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
    @Column(name = "nickname", nullable = false, length = 20)
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

    // 닉네임 변경
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    // 프로필 이미지 변경
    public void updateProfileImage(String profileImageUrl, String profileImageKey) {
        this.profileImageUrl = profileImageUrl;
        this.profileImageKey = profileImageKey;
    }

    // 다음 레벨업까지 남은 카드 수 계산
    public int getRemainingCardsForNextLevel() {
        int nextLevelThreshold = calculateLevelThreshold(this.level);
        return Math.max(0, nextLevelThreshold - this.totalCardCount);
    }

    // 현재 레벨 진행률(%) 계산
    public int getLevelProgressPercent() {
        int nextLevelThreshold = calculateLevelThreshold(this.level);
        int currentLevelBase = calculateLevelThreshold(this.level - 1);

        int totalForThisLevel = nextLevelThreshold - currentLevelBase;
        int gatheredForThisLevel = this.totalCardCount - currentLevelBase;

        if (totalForThisLevel <= 0) return 0;

        int progress = (int) ((double) gatheredForThisLevel / totalForThisLevel * 100);
        return Math.min(100, Math.max(0, progress));
    }

    // 레벨 재계산 및 갱신 (카드 생성 시 호출)
    public void incrementTotalCardCount() {
        this.totalCardCount++;
        recalculateLevel();
    }

    // 내부 로직: 실제 레벨 재계산 수행
    private void recalculateLevel() {
        // 현재 totalCardCount에 맞는 레벨을 찾을 때까지 루프
        int newLevel = 1;
        while (calculateLevelThreshold(newLevel) <= this.totalCardCount) {
            newLevel++;
        }
        this.level = newLevel;
    }

    // 특정 레벨 도달에 필요한 누적 카드 수 반환 (공식: 5, 15, 30...)
    private int calculateLevelThreshold(int targetLevel) {
        if (targetLevel <= 0) return 0;

        int threshold = 0;
        int increment = 5;

        for (int i = 1; i <= targetLevel; i++) {
            threshold += increment;
            increment += 5;
        }
        return threshold;
    }

    // ... (나머지 incrementActiveCardCount 등은 그대로 유지) ...
    public void incrementActiveCardCount() {
        this.activeCardCount++;
    }

    public void decrementActiveCardCount() {
        if (this.activeCardCount > 0) {
            this.activeCardCount--;
        }
    }

    public void incrementWinCount() {
        this.winCount++;
    }
}
