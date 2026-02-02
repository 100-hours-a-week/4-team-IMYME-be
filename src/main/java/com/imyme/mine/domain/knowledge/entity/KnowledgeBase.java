package com.imyme.mine.domain.knowledge.entity;

import com.imyme.mine.domain.keyword.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 지식 베이스 엔티티
 * - 사용자 업로드 지식 콘텐츠 저장
 * - OpenAI 임베딩 벡터 저장
 * - SHA-256 콘텐츠 해시로 중복 방지
 * - 활성화 여부로 검색 노출 제어
 */
@Entity
@Table(
    name = "knowledge_base",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_kb_content_hash",
            columnNames = {"content_hash"}
        )
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관 키워드
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id")
    private Keyword keyword;

    // 지식 콘텐츠 원문
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 벡터 임베딩: OpenAI text-embedding-3-small 모델 기준 1024차원
    // 의미적 유사도 검색용 벡터 데이터
    // PostgreSQL vector 타입으로 명시적 형변환(Casting) 추가
    @Column(name = "embedding", columnDefinition = "vector")
    @ColumnTransformer(write = "?::vector")
    private String embedding;

    // 콘텐츠 해시: SHA-256 해시값 (64자)
    // 중복 지식 저장 방지 및 OpenAI 임베딩 API 호출 최소화
    // 동일 내용이 이미 존재하면 임베딩 재생성 없이 기존 데이터 재사용
    @Column(name = "content_hash", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String contentHash;

    // 활성화 여부: 검색 노출 제어
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("TRUE")
    @Builder.Default
    private Boolean isActive = true;

    // 타임스탬프
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    // JPA 생명주기 콜백
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

    // 비즈니스 로직 메서드

    // 콘텐츠 및 해시 업데이트 : 콘텐츠 변경 시 임베딩 재생성 필요
    public void updateContent(String newContent, String newContentHash) {
        this.content = newContent;
        this.contentHash = newContentHash;
        this.embedding = null;  // 임베딩 재생성 필요 표시
    }

    // 임베딩 업데이트 : OpenAI API 호출 후 임베딩 저장
    public void updateEmbedding(String embeddingVector) {
        this.embedding = embeddingVector;
    }

    // 활성화 상태 토글
    public void toggleActive() {
        this.isActive = !this.isActive;
    }

    // 비활성화
    public void deactivate() {
        this.isActive = false;
    }

    // 활성화
    public void activate() {
        this.isActive = true;
    }

    // 임베딩 존재 여부 확인
    public boolean hasEmbedding() {
        return this.embedding != null && !this.embedding.isBlank();
    }
}
