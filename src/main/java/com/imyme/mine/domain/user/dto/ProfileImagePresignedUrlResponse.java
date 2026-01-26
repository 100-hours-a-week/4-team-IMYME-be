package com.imyme.mine.domain.user.dto;

import java.util.List;

/**
 * 프로필 이미지 Presigned URL 응답 DTO
 * - 클라이언트가 S3에 직접 업로드할 수 있는 정보 제공
 */
public record ProfileImagePresignedUrlResponse(
    String uploadUrl,
    String profileImageUrl,
    String profileImageKey,
    Integer expiresIn,
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
        Long maxSizeBytes,
        List<String> allowedContentTypes
    ) {}
}
