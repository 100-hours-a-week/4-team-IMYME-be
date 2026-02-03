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

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);
}
