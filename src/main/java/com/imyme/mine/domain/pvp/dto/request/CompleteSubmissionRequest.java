package com.imyme.mine.domain.pvp.dto.request;

import jakarta.validation.constraints.Positive;

/**
 * 녹음 제출 완료 요청 (4.6)
 */
public record CompleteSubmissionRequest(
        @Positive(message = "재생 시간은 양수여야 합니다")
        Integer durationSeconds
) {
}
