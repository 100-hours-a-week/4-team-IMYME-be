package com.imyme.mine.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 프로필 이미지 Presigned URL 응답 DTO
 * - 클라이언트가 S3에 직접 업로드할 수 있는 정보 제공
 */
public record ProfileImagePresignedUrlResponse(

    @JsonProperty("upload_url")
    String uploadUrl,

    @JsonProperty("profile_image_url")
    String profileImageUrl,

    @JsonProperty("profile_image_key")
    String profileImageKey,

    @JsonProperty("expires_in")
    Integer expiresIn,

    @JsonProperty("constraints")
    Constraints constraints

) {

    public static ProfileImagePresignedUrlResponse of(
        String uploadUrl,
        String profileImageUrl,
        String profileImageKey,
        Integer expiresIn,
        Long maxSizeBytes,
        List<String> allowedContentTypes
    ) {
        Constraints constraints = new Constraints(maxSizeBytes, allowedContentTypes);
        return new ProfileImagePresignedUrlResponse(
            uploadUrl,
            profileImageUrl,
            profileImageKey,
            expiresIn,
            constraints
        );
    }

    public record Constraints(
        @JsonProperty("max_size_bytes")
        Long maxSizeBytes,

        @JsonProperty("allowed_content_types")
        List<String> allowedContentTypes
    ) {}
}