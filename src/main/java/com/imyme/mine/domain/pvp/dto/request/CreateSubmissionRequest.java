package com.imyme.mine.domain.pvp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 녹음 제출 URL 발급 요청 (4.5)
 */
public record CreateSubmissionRequest(
        @NotBlank(message = "파일명을 입력해주세요")
        String fileName,

        @NotBlank(message = "Content-Type을 입력해주세요")
        String contentType,

        @NotNull(message = "파일 크기를 입력해주세요")
        @Positive(message = "파일 크기는 양수여야 합니다")
        Long fileSize
) {
}
