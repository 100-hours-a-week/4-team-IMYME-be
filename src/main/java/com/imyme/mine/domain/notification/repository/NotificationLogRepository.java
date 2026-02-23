package com.imyme.mine.domain.notification.repository;

import com.imyme.mine.domain.notification.entity.NotificationLog;
import com.imyme.mine.domain.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 전송 이력 Repository
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * 알림별 전송 이력 조회
     */
    List<NotificationLog> findByNotificationIdOrderByCreatedAtDesc(Long notificationId);

    /**
     * 재시도 가능한 실패 로그 조회
     */
    @Query("SELECT nl FROM NotificationLog nl WHERE nl.status = :status AND nl.retryCount < :maxRetries AND nl.createdAt >= :since ORDER BY nl.createdAt ASC")
    List<NotificationLog> findRetryableLogs(
            @Param("status") NotificationStatus status,
            @Param("maxRetries") int maxRetries,
            @Param("since") LocalDateTime since
    );

    /**
     * 사용자의 전송 이력 조회
     */
    List<NotificationLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}