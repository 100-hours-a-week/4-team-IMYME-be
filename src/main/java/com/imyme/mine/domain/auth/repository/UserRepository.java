package com.imyme.mine.domain.auth.repository;

import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // OAuth ID로 회원 조회
    Optional<User> findByOauthId(String oauthId);

    // OAuth ID와 OauthProviderType으로 회원 조회
    Optional<User> findByOauthIdAndOauthProvider(String oauthId, OAuthProviderType oauthProvider);

    // 닉네임으로 회원 조회
    Optional<User> findByNickname(String nickname);

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);

    // 이메일로 회원 조회
    Optional<User> findByEmail(String email);

    // 이메일 중복 확인
    boolean existsByEmail(String email);

    // ID 목록으로 회원 조회
    List<User> findByIdIn(List<Long> userIds);

    // 특정 레벨 이상의 회원 조회
    List<User> findByLevelGreaterThanEqual(Integer level);

    // 연속 접속일 기준으로 상위 N명 조회
    @Query("SELECT u FROM User u ORDER BY u.consecutiveDays DESC, u.createdAt ASC")
    List<User> findTopByConsecutiveDays(Pageable pageable);

    // 레벨 기준으로 상위 N명 조회
    @Query("SELECT u FROM User u ORDER BY u.level DESC, u.totalCardCount DESC, u.createdAt ASC")
    List<User> findTopByLevel(Pageable pageable);

    // 특정 기간 동안 미접속 회원 조회 (휴면 계정)
    List<User> findByLastLoginAtBefore(LocalDateTime date);

    // 탈퇴 후 일정 기간 경과한 회원 조회 (배치 삭제용)
    // 중요: @SQLRestriction 때문에 JPQL로는 deletedUser 조회가 안 됩니다. nativeQuery를 써야 합니다.
    @Query(value = "SELECT * FROM users u WHERE u.deleted_at IS NOT NULL AND u.deleted_at < :date", nativeQuery = true)
    List<User> findDeletedUsersBefore(@Param("date") LocalDateTime date);

    // 특정 OAuth Provider의 회원 수 조회
    long countByOauthProvider(OAuthProviderType oauthProvider);

    // 전체 활성 회원 수 조회
    long count();

    // 오늘 가입한 회원 수 조회
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :startOfDay")
    long countNewUsersToday(@Param("startOfDay") LocalDateTime startOfDay);
}
