package com.imyme.mine.domain.learning.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Solo STT 요청 DTO (Main → AI)
 * Schema: {attempt_id, user_id, audio_url, timestamp}
 */
@Builder
public record SoloSttRequestDto(
    @JsonProperty("attempt_id") Long attemptId,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("audio_url") String audioUrl,
    @JsonProperty("timestamp") Long timestamp
) {}