package com.imyme.mine.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 프로필 이미지 Presigned URL 요청 DTO
 * - 클라이언트가 S3에 직접 업로드하기 위한 서명된 URL 발급 요청
 */
public record ProfileImagePresignedUrlRequest(

    @NotBlank(message = "Content-Type은 필수입니다.")
    String contentType

) {}
