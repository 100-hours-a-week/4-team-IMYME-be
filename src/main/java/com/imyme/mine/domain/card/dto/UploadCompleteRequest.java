package com.imyme.mine.domain.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 오디오 업로드 완료 요청 DTO
 * - Solo 모드 분석을 위한 필드 포함
 */
public record UploadCompleteRequest(

    @NotBlank(message = "오디오 URL은 필수입니다.")
    String audioUrl,

    @Positive(message = "재생 시간은 양수여야 합니다.")
    Integer durationSeconds,

    @NotBlank(message = "사용자 텍스트는 필수입니다.")
    @Size(min = 1, max = 5000, message = "텍스트는 1~5000자 사이여야 합니다.")
    String userText,

    @NotNull(message = "채점 기준(criteria)은 필수입니다.")
    Map<String, Object> criteria,

    List<Map<String, Object>> history

) {}