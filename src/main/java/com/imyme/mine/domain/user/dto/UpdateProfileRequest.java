package com.imyme.mine.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 프로필 수정 요청 DTO
 * - 닉네임, 프로필 이미지 정보 부분 수정
 * - 주의: 프로필 이미지 변경 시 url과 key는 반드시 함께 전송되어야 함 (Service 레벨 검증 필요)
 */
public record UpdateProfileRequest(
    @Size(min = 1, max = 20, message = "닉네임은 1~20자여야 합니다.")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용 가능합니다.")
    String nickname,

    @Size(max = 200, message = "프로필 이미지 키는 200자 이하여야 합니다.")
    String profileImageKey
) {
}
