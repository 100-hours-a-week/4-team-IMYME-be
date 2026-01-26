package com.imyme.mine.domain.user.controller;

import com.imyme.mine.domain.user.dto.NicknameCheckResponse;
import com.imyme.mine.domain.user.dto.UpdateProfileRequest;
import com.imyme.mine.domain.user.dto.UserProfileResponse;
import com.imyme.mine.domain.user.service.UserService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 관리 컨트롤러
 * - 프로필 조회/수정, 닉네임 중복 확인, 회원 탈퇴
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 프로필 조회
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @CurrentUser UserPrincipal userPrincipal
    ) {
        log.info("프로필 조회 요청: userId={}", userPrincipal.getId());

        UserProfileResponse response = userService.getProfile(userPrincipal.getId());

        // 캐싱 비활성화 (MVP 단계)
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "프로필 조회 성공"));
    }

    // 프로필 수정
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        log.info("프로필 수정 요청: userId={}", userPrincipal.getId());

        UserProfileResponse response = userService.updateProfile(userPrincipal.getId(), request);

        return ApiResponse.success(response, "프로필이 수정되었습니다.");
    }

    // 닉네임 중복 확인
    @GetMapping("/nickname/check")
    public ApiResponse<NicknameCheckResponse> checkNickname(
            @RequestParam String nickname,
            @CurrentUser UserPrincipal userPrincipal // 로그인 안 했으면 null
    ) {
        log.info("닉네임 중복 확인 요청: nickname={}", nickname);

        Long currentUserId = (userPrincipal != null) ? userPrincipal.getId() : null;

        NicknameCheckResponse response = userService.checkNickname(nickname, currentUserId);

        return ApiResponse.success(response, "닉네임 확인 완료");
    }

    // 회원 탈퇴
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawUser(@CurrentUser UserPrincipal userPrincipal) {
        log.info("회원 탈퇴 요청: userId={}", userPrincipal.getId());

        userService.withdrawUser(userPrincipal.getId());

        log.info("회원 탈퇴 완료: userId={}", userPrincipal.getId());
    }
}
