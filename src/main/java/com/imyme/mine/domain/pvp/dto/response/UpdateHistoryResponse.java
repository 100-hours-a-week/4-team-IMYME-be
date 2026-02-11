package com.imyme.mine.domain.pvp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 기록 숨기기 응답 (4.9)
 */
@Getter
@AllArgsConstructor
@Builder
public class UpdateHistoryResponse {
    private Long historyId;
    private Boolean isHidden;
    private LocalDateTime updatedAt;
}
