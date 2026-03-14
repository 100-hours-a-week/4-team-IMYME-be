package com.imyme.mine.domain.challenge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 챌린지 참여 시작 요청 (POST /challenges/{challengeId}/attempts)
 */
public record CreateAttemptRequest(
        @Schema(description = "파일 이름", example = "recording.webm")
        @NotBlank String fileName,

        @Schema(description = "파일 MIME 타입 (audio/webm, audio/mp4, audio/m4a)", example = "audio/webm")
        @NotBlank String contentType,

        @Schema(description = "파일 크기 (바이트, 최대 10MB)", example = "1048576")
        @NotNull @Positive Long fileSize
) {}