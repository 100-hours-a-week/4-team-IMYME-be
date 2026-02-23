package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpSubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 제출 정보 응답 (4.5, 4.6)
 */
@Getter
@AllArgsConstructor
@Builder
public class SubmissionResponse {
    private Long submissionId;
    private Long roomId;
    private String uploadUrl;
    private String audioUrl;
    private Integer expiresIn;
    private PvpSubmissionStatus status;
    private LocalDateTime submittedAt;
    private String message;
}
