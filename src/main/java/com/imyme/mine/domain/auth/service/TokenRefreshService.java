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
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenRefreshResponse refreshTokens(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserSession userSession = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (userSession.isExpired()) {
            userSessionRepository.delete(userSession);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = userSession.getUser();

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        long expiresIn = jwtProperties.getAccessTokenExpiration() / 1000;
        LocalDateTime newExpiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000);

        userSession.rotateRefreshToken(newRefreshToken, newExpiresAt);

        log.info("Token refreshed for user: {}", user.getId());

        return TokenRefreshResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .expiresIn(expiresIn)
            .build();
    }
}
