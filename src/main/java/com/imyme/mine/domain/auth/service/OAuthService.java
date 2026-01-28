package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.client.KakaoOAuthClient;
import com.imyme.mine.domain.auth.dto.OAuthLoginRequest;
import com.imyme.mine.domain.auth.dto.OAuthLoginResponse;
import com.imyme.mine.domain.auth.entity.*;
import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.global.config.JwtProperties;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import com.imyme.mine.global.security.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

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

    // 카카오 로그인 메인 로직
    public OAuthLoginResponse loginWithKakao(OAuthLoginRequest request) {
        KakaoOAuthClient.KakaoUserInfo userInfo = fetchKakaoUserInfo(request);
        String oauthId = "kakao_" + userInfo.getId();
        return processLogin(oauthId, request.deviceUuid(), userInfo);
    }

    private KakaoOAuthClient.KakaoUserInfo fetchKakaoUserInfo(OAuthLoginRequest request) {
        KakaoOAuthClient.KakaoTokenResponse tokenResponse =
            kakaoOAuthClient.getAccessToken(request.code(), request.redirectUri());

        return kakaoOAuthClient.getUserInfo(tokenResponse.getAccessToken());
    }

    @Transactional
    protected OAuthLoginResponse processLogin(String oauthId, String deviceUuid, KakaoOAuthClient.KakaoUserInfo userInfo) {

        User user = userRepository.findByOauthId(oauthId)
            .orElseGet(() -> createNewUser(oauthId, userInfo));

        boolean isNewUser = user.getCreatedAt().isEqual(user.getUpdatedAt());
        user.updateLastLogin();

        Device device = deviceRepository.findByDeviceUuid(deviceUuid)
            .orElseGet(() -> deviceRepository.save(Device.builder()
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

        UserSession userSession = UserSession.builder()
                .user(user)
                .device(device)
                .refreshToken(hashedRefreshToken)  // 해싱된 값 저장
                .expiresAt(expiresAt)
                .build();

        userSessionRepository.save(userSession);

        return OAuthLoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(expiresIn)
            .user(OAuthLoginResponse.UserInfo.from(user, isNewUser))
            .build();
    }

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
        SecureRandom random = new SecureRandom();

        // 1. 일단 닉네임만으로 존재 여부 확인
        if (!userRepository.existsByNickname(nickname)) {
            return nickname;
        }

        // 2. 중복되면 뒤에 랜덤 숫자 붙여서 시도
        while (true) {
            int suffix = random.nextInt(9999);
            String candidate = nickname + "#" + suffix;
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
    }
}
