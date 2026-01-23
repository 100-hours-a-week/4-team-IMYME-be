package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.client.KakaoOAuthClient;
import com.imyme.mine.domain.auth.dto.OAuthLoginRequest;
import com.imyme.mine.domain.auth.dto.OAuthLoginResponse;
import com.imyme.mine.domain.auth.entity.OAuthProvider;
import com.imyme.mine.domain.auth.entity.Role;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.entity.UserSession;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.global.config.JwtProperties;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

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
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    // 카카오 로그인 메인 로직
    public OAuthLoginResponse loginWithKakao(OAuthLoginRequest request) {
        KakaoOAuthClient.KakaoUserInfo userInfo = fetchKakaoUserInfo(request);
        String oauthId = "kakao_" + userInfo.getId();
        return processLogin(oauthId, request.getDeviceUuid(), userInfo);
    }

    private KakaoOAuthClient.KakaoUserInfo fetchKakaoUserInfo(OAuthLoginRequest request) {
        KakaoOAuthClient.KakaoTokenResponse tokenResponse =
            kakaoOAuthClient.getAccessToken(request.getCode(), request.getRedirectUri());

        return kakaoOAuthClient.getUserInfo(tokenResponse.getAccessToken());
    }

    @Transactional
    protected OAuthLoginResponse processLogin(String oauthId, String deviceUuid, KakaoOAuthClient.KakaoUserInfo userInfo) {

        Optional<User> existingUser = userRepository.findByOauthId(oauthId);
        User user;
        boolean isNewUser;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            isNewUser = false;
            user.updateLastLogin();

            log.info("기존 유저 로그인: {}", user.getId());
        } else {
            user = createNewUser(oauthId, userInfo);
            isNewUser = true;
            log.info("신규 유저 가입: {}", user.getId());
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // UserSession 저장 (RT Rotation 지원)
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000);

        UserSession userSession = UserSession.builder()
                .user(user)
                .refreshToken(refreshToken)
                .expiresAt(expiresAt)
                .build();

        userSessionRepository.save(userSession);
        log.info("UserSession saved for user: {}", user.getId());

        return OAuthLoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .deviceId(deviceUuid)
            .user(OAuthLoginResponse.UserInfo.builder()
                .id(user.getId())
                .oauthId(user.getOauthId())
                .oauthProvider(user.getOauthProvider().name())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .level(user.getLevel())
                .isNewUser(isNewUser)
                .build())
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
            .oauthProvider(OAuthProvider.KAKAO)
            .email(email)
            .nickname(uniqueNickname)
            .profileImageUrl(profileImage)
            .role(Role.USER)
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
