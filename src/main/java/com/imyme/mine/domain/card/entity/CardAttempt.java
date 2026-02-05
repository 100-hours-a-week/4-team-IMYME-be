package com.imyme.mine.domain.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "card_attempts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_attempts_card_no", columnNames = {"card_id", "attempt_no"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CardAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "attempt_no", nullable = false)
    private Short attemptNo;

    @Column(name = "audio_key", length = 500)
    private String audioKey;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AttemptStatus status = AttemptStatus.UPLOADED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void reserveAudioKey(String audioKey) {
        this.audioKey = audioKey;
    }

    public void markUploaded(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
        this.status = AttemptStatus.UPLOADED;
        this.submittedAt = LocalDateTime.now();
    }

    public void startProcessing() {
        this.status = AttemptStatus.PROCESSING;
        this.submittedAt = LocalDateTime.now();
    }

    public void complete(String sttText) {
        this.sttText = sttText;
        this.status = AttemptStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    public void recordSttResult(String sttText) {
        this.sttText = sttText;
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = AttemptStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = AttemptStatus.EXPIRED;
        this.finishedAt = LocalDateTime.now();
    }
}
