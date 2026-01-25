package com.imyme.mine.domain.auth.controller;

import com.imyme.mine.domain.auth.dto.*;
import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.service.LogoutService;
import com.imyme.mine.domain.auth.service.OAuthService;
import com.imyme.mine.domain.auth.service.TokenRefreshService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OAuthService oauthService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;


    // OAuth 로그인 및 회원가입
    @PostMapping("/oauth/{provider}")
    public ApiResponse<OAuthLoginResponse> oauthLogin(
        @PathVariable String provider,
        @Valid @RequestBody OAuthLoginRequest request
    ) {
        log.info("OAuth login attempt: provider={}", provider);

        OAuthProviderType oauthProvider;
        try {
            oauthProvider = OAuthProviderType.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }

        if (oauthProvider != OAuthProviderType.KAKAO) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }

        OAuthLoginResponse response = oauthService.loginWithKakao(request);

        if (Boolean.TRUE.equals(response.user().isNewUser())) {
            return ApiResponse.success(response, "회원가입이 완료되었습니다.");
        } else {
            return ApiResponse.success(response, "로그인되었습니다.");
        }
    }

    // 토큰 갱신
    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refreshToken(
        @Valid @RequestBody TokenRefreshRequest request
    ) {
        log.info("Token refresh attempt");

        TokenRefreshResponse response = tokenRefreshService.refreshTokens(request);

        return ApiResponse.success(response, "토큰이 갱신되었습니다.");
    }

    // 로그아웃
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
        @CurrentUser UserPrincipal userPrincipal,
        @Valid @RequestBody LogoutRequest request) {
        log.info("Logout attempt: userId={}, deviceUuid={}", userPrincipal.getId(), request.deviceUuid());

        logoutService.logout(userPrincipal.getId(), request.deviceUuid());

        log.info("Logout successful: userId={}, deviceUuid={}", userPrincipal.getId(), request.deviceUuid());
    }
}
