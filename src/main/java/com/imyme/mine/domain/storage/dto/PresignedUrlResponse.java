package com.imyme.mine.domain.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PresignedUrlResponse(

    @JsonProperty("attempt_id")
    Long attemptId,

    @JsonProperty("upload_url")
    String uploadUrl,

    @JsonProperty("object_key")
    String objectKey,

    @JsonProperty("expires_at")
    LocalDateTime expiresAt

) {

    public static PresignedUrlResponse of(
        Long attemptId,
        String uploadUrl,
        String objectKey,
        LocalDateTime expiresAt
    ) {
        return new PresignedUrlResponse(attemptId, uploadUrl, objectKey, expiresAt);
    }
}