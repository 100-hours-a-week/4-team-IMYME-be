package com.imyme.mine.domain.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PresignedUrlRequest(

    @NotNull(message = "카드 ID는 필수입니다.")
    @JsonProperty("card_id")
    Long cardId,

    @NotNull(message = "파일 확장자는 필수입니다.")
    @Pattern(regexp = "^(mp3|wav|m4a|webm)$", message = "지원하지 않는 오디오 형식입니다.")
    @JsonProperty("file_extension")
    String fileExtension

) {}