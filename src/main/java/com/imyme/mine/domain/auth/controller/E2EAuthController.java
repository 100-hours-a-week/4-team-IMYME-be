package com.imyme.mine.domain.auth.controller;

import com.imyme.mine.domain.auth.dto.E2ELoginRequest;
import com.imyme.mine.domain.auth.dto.OAuthLoginResponse;
import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.service.OAuthService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E2E 테스트 전용 인증 컨트롤러
 * - 테스트 환경에서만 활성화됩니다 (@Profile("test"))
 * - Swagger 문서에 노출되지 않습니다 (@Hidden)
 * - 실제 OAuth 프로세스를 거치지 않고 고정된 테스트 계정으로 로그인할 수 있습니다.
 */
@Hidden
// test(자동화테스트) 또는 dev(개발) 환경에서 활성화
@Profile({"test", "dev"})
@Slf4j
@RestController
@RequestMapping("/e2e")
@RequiredArgsConstructor
public class E2EAuthController {

    private static final String E2E_TEST_USER_OAUTH_ID = "e2e_test_user";

    private final UserRepository userRepository;
    private final OAuthService oauthService;

    /**
     * E2E 테스트 로그인 엔드포인트
     * - 사전에 정의된 테스트 계정으로 즉시 로그인합니다.
     * - OAuth 인증 과정 없이 Access Token과 Refresh Token을 발급합니다.
     *
     * @param request deviceUuid를 포함한 로그인 요청
     * @return Access Token과 Refresh Token을 포함한 로그인 응답
     */
    @PostMapping("/login")
    @Transactional
    public ApiResponse<OAuthLoginResponse> e2eLogin(@Valid @RequestBody E2ELoginRequest request) {
        log.info("E2E test login attempt: deviceUuid={}", request.deviceUuid());

        // E2E 테스트 유저 조회
        User testUser = userRepository
            .findByOauthIdAndOauthProvider(E2E_TEST_USER_OAUTH_ID, OAuthProviderType.E2E_TEST)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 공통 로그인 로직 실행 (토큰 발급)
        OAuthLoginResponse response = oauthService.login(testUser, request.deviceUuid(), false);

        log.info("E2E test login successful: userId={}, deviceUuid={}", testUser.getId(), request.deviceUuid());

        return ApiResponse.success(response, "E2E 테스트 로그인 성공");
    }
}
