package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.client.KakaoOAuthClient;
import com.imyme.mine.domain.auth.dto.OAuthLoginRequest;
import com.imyme.mine.domain.auth.dto.OAuthLoginResponse;
import com.imyme.mine.domain.auth.entity.*;
import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.global.config.JwtProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import com.imyme.mine.global.security.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;

/*
 * OAuthService
 * - 카카오 OAuth 로그인 및 회원가입 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final DeviceRepository deviceRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    private static final int MAX_RETRIES = 100;
    private final SecureRandom secureRandom = new SecureRandom();

    // 카카오 로그인 메인 로직
    @Transactional
    public OAuthLoginResponse loginWithKakao(OAuthLoginRequest request) {
        KakaoOAuthClient.KakaoUserInfo userInfo = fetchKakaoUserInfo(request);
        String oauthId = "kakao_" + userInfo.getId();
        return processLogin(oauthId, request.deviceUuid(), userInfo);
    }

    // 카카오로부터 사용자 정보 조회
    private KakaoOAuthClient.KakaoUserInfo fetchKakaoUserInfo(OAuthLoginRequest request) {
        KakaoOAuthClient.KakaoTokenResponse tokenResponse =
            kakaoOAuthClient.getAccessToken(request.code(), request.redirectUri());

        return kakaoOAuthClient.getUserInfo(tokenResponse.getAccessToken());
    }

    // 로그인 처리 (회원가입 포함)
    protected OAuthLoginResponse processLogin(String oauthId, String deviceUuid, KakaoOAuthClient.KakaoUserInfo userInfo) {

        User user = userRepository.findByOauthId(oauthId)
            .orElseGet(() -> createNewUser(oauthId, userInfo));

        boolean isNewUser = user.getCreatedAt().isEqual(user.getUpdatedAt());
        user.updateLastLogin();

        Device device = deviceRepository.findByDeviceUuid(deviceUuid)
            .orElseGet(() -> deviceRepository.saveAndFlush(Device.builder()
                .deviceUuid(deviceUuid)
                .agentType(AgentType.CHROME) // TODO: 추후 헤더 파싱 필요
                .platformType(PlatformType.MOBILE_WEB) // TODO: 추후 헤더 파싱 필요
                .build()));

        device.login(user);

        // JWT 토큰 생성 (Access Token + Refresh Token)
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        long expiresIn = jwtProperties.getAccessTokenExpiration() / 1000;

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000);

        String hashedRefreshToken = TokenHasher.hash(refreshToken);

        try {
            UserSession userSession = userSessionRepository
                .findByUserIdAndDeviceUuid(user.getId(), deviceUuid)
                .orElse(null);

            if (userSession != null) {
                // 이미 있으면 업데이트 (기존 로직 동일)
                log.info("기존 세션 재사용 - sessionId: {}", userSession.getId());
                userSession.rotateRefreshToken(hashedRefreshToken, expiresAt);
            } else {
                // 없으면 생성 (INSERT)
                log.info("새 세션 생성 시도 - userId: {}, deviceUuid: {}", user.getId(), deviceUuid);
                UserSession newSession = UserSession.builder()
                    .user(user)
                    .device(device)
                    .refreshToken(hashedRefreshToken)
                    .expiresAt(expiresAt)
                    .build();

                userSessionRepository.save(newSession); // 여기서 중복 발생 시 예외 터짐
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("세션 생성 중 동시성 경합 발생! 업데이트로 전환합니다. userId={}", user.getId());
            UserSession existingSession = userSessionRepository
                .findByUserIdAndDeviceUuid(user.getId(), deviceUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_CREATION_FAILED));

            existingSession.rotateRefreshToken(hashedRefreshToken, expiresAt);
        }

        return OAuthLoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(expiresIn)
            .user(OAuthLoginResponse.UserInfo.from(user, isNewUser))
            .build();
    }

    // 신규 사용자 생성
    private User createNewUser(String oauthId, KakaoOAuthClient.KakaoUserInfo userInfo) {
        String baseNickname = "사용자";
        String profileImage = null;
        String email = null;

        try {
            if (userInfo.getKakaoAccount() != null) {
                email = userInfo.getKakaoAccount().getEmail();
                if (userInfo.getKakaoAccount().getProfile() != null) {
                    baseNickname = userInfo.getKakaoAccount().getProfile().getNickname();
                    profileImage = userInfo.getKakaoAccount().getProfile().getProfileImageUrl();
                }
            }
        } catch (Exception e) {
            log.warn("카카오 정보 파싱 실패 (기본값 사용): {}", e.getMessage());
        }

        // 닉네임 중복 방지 로직 적용
        String uniqueNickname = generateUniqueNickname(baseNickname);

        User user = User.builder()
            .oauthId(oauthId)
            .oauthProvider(OAuthProviderType.KAKAO)
            .email(email)
            .nickname(uniqueNickname)
            .profileImageUrl(profileImage)
            .role(RoleType.USER)
            .level(1)
            .totalCardCount(0)
            .activeCardCount(0)
            .consecutiveDays(1)
            .winCount(0)
            .build();

        return userRepository.save(user);
    }

    // 닉네임 중복 방지 (기본닉네임 + 랜덤숫자)
    private String generateUniqueNickname(String nickname) {
        // 닉네임만으로 존재 여부 확인
        if (!userRepository.existsByNickname(nickname)) {
            return nickname;
        }

        // 중복되면 뒤에 랜덤 숫자 붙여서 시도 (최대 100회)
        int attempts = 0;
        while (attempts++ < MAX_RETRIES) {
            long suffix = Math.abs(secureRandom.nextLong() % 1_000_000);
            String candidate = nickname + "#" + suffix;
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }

        // 최대 재시도 횟수 초과 시 예외 발생
        log.error("닉네임 생성 실패 - 최대 재시도 횟수({}) 초과 - baseNickname: {}", MAX_RETRIES, nickname);
        throw new BusinessException(ErrorCode.NICKNAME_GENERATION_FAILED);
    }
}
