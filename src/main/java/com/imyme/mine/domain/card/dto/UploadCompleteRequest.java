package com.imyme.mine.domain.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UploadCompleteRequest(

    @NotBlank(message = "오디오 URL은 필수입니다.")
    String audioUrl,

    @Positive(message = "재생 시간은 양수여야 합니다.")
    Integer durationSeconds

) {}