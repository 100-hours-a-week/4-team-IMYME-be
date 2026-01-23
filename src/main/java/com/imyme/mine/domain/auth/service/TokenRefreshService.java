package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.dto.TokenRefreshRequest;
import com.imyme.mine.domain.auth.dto.TokenRefreshResponse;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.entity.UserSession;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.global.config.JwtProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 토큰 갱신 서비스
 * - Refresh Token Rotation 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;

    /**
     * Refresh Token으로 Access Token 및 Refresh Token 갱신
     * - RT Rotation: 새로운 Refresh Token 발급
     * - DB 동기화: user_sessions 테이블 업데이트
     */
    @Transactional
    public TokenRefreshResponse refreshTokens(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. Refresh Token 검증 (JWT 형식 및 서명 검증)
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. DB에서 Refresh Token 조회
        UserSession userSession = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 3. Refresh Token 만료 확인
        if (userSession.isExpired()) {
            // 만료된 세션 삭제
            userSessionRepository.delete(userSession);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // 4. 사용자 정보 조회
        User user = userSession.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 5. 새로운 Access Token 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId());

        // 6. 새로운 Refresh Token 생성 (RT Rotation)
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        LocalDateTime newExpiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000);

        // 7. UserSession 업데이트 (RT Rotation)
        userSession.updateRefreshToken(newRefreshToken, newExpiresAt);
        userSessionRepository.save(userSession);

        log.info("Token refreshed successfully for user: {}", user.getId());

        // 8. 응답 반환
        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}