package com.imyme.mine.domain.pvp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 방 생성 요청 (4.2)
 */
public record CreateRoomRequest(
        @NotNull(message = "카테고리를 선택해주세요")
        Long categoryId,

        @NotBlank(message = "방 이름을 입력해주세요")
        @Size(min = 2, max = 30, message = "방 이름은 2~30자 사이여야 합니다")
        String roomName
) {
}
