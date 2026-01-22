package com.imyme.mine.domain.auth.controller;

import com.imyme.mine.domain.auth.dto.OAuthLoginRequest;
import com.imyme.mine.domain.auth.dto.OAuthLoginResponse;
import com.imyme.mine.domain.auth.entity.OAuthProvider;
import com.imyme.mine.domain.auth.service.OAuthService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OAuthService oauthService;


    @PostMapping("/oauth/{provider}")
    public ApiResponse<OAuthLoginResponse> oauthLogin(
        @PathVariable String provider,
        @Valid @RequestBody OAuthLoginRequest request
    ) {
        log.info("OAuth login attempt: provider={}", provider);

        OAuthProvider oauthProvider;
        try {
            oauthProvider = OAuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }

        if (oauthProvider != OAuthProvider.KAKAO) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }

        OAuthLoginResponse response = oauthService.loginWithKakao(request);

        if (Boolean.TRUE.equals(response.getUser().getIsNewUser())) {
            return ApiResponse.success(response, "회원가입이 완료되었습니다.");
        } else {
            return ApiResponse.success(response, "로그인되었습니다.");
        }
    }
}
