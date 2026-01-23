package com.imyme.mine.domain.auth.repository;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    // Refresh Token으로 세션 조회
    Optional<UserSession> findByRefreshToken(String refreshToken);

    // 사용자별 세션 조회 (최신순)
    List<UserSession> findByUserOrderByCreatedAtDesc(User user);

    // 사용자별 세션 조회 (사용자 ID)
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<UserSession> findByUserId(@Param("userId") Long userId);

    // 만료된 세션 삭제 (배치용)
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);

    // 사용자의 모든 세션 삭제 (로그아웃 전체)
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    // Refresh Token 존재 여부 확인
    boolean existsByRefreshToken(String refreshToken);
}
