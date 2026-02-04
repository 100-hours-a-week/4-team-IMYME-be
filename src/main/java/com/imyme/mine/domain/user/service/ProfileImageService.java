package com.imyme.mine.domain.user.service;

import com.imyme.mine.domain.user.dto.ProfileImagePresignedUrlRequest;
import com.imyme.mine.domain.user.dto.ProfileImagePresignedUrlResponse;
import com.imyme.mine.global.config.S3Properties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 프로필 이미지 업로드 서비스
 * - Presigned URL 발급 및 Content-Type 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(5); // 300초
    private static final int EXPIRES_IN_SECONDS = 300;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/heic",
        "image/webp"
    );

    // 프로필 이미지 업로드용 Presigned URL 발급
    public ProfileImagePresignedUrlResponse generatePresignedUrl(
        Long userId,
        ProfileImagePresignedUrlRequest request
    ) {
        log.info("프로필 이미지 Presigned URL 생성 시작 - userId: {}, contentType: {}",
            userId, request.contentType());

        validateContentType(request.contentType());

        String extension = extractExtension(request.contentType());
        String objectKey = generateObjectKey(userId, extension);

        // Presigned URL 생성
        PresignedPutObjectRequest presignedRequest;
        try {
            presignedRequest = generatePresignedPutRequest(objectKey, request.contentType());
        } catch (SdkException e) {
            log.error("S3 Presigned URL 생성 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR); // 적절한 500 에러 코드 사용
        }

        // CDN URL 생성 (현재는 S3 URL 사용)
        String profileImageUrl = generatePublicUrl(objectKey);

        log.info("프로필 이미지 Presigned URL 생성 완료 - userId: {}, objectKey: {}", userId, objectKey);

        return ProfileImagePresignedUrlResponse.of(
            presignedRequest.url().toString(),
            profileImageUrl,
            objectKey,
            EXPIRES_IN_SECONDS,
            MAX_FILE_SIZE,
            List.copyOf(ALLOWED_CONTENT_TYPES)
        );
    }

    // Content-Type 검증
    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            log.warn("유효하지 않은 Content-Type: {}", contentType);
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }
    }

    // Content-Type에서 확장자 추출
    private String extractExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/heic" -> "heic";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        };
    }

    // S3 Object Key 생성 - 형식: profiles/{userId}/{uuid}.{확장자}
    private String generateObjectKey(Long userId, String extension) {
        String uuid = UUID.randomUUID().toString();
        return String.format("profiles/%d/%s.%s", userId, uuid, extension);
    }

    // Presigned PUT 요청 생성
    private PresignedPutObjectRequest generatePresignedPutRequest(
        String objectKey,
        String contentType
    ) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGNED_URL_EXPIRATION)
            .putObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
            )
            .build();

        return s3Presigner.presignPutObject(presignRequest);
    }

    /**
     * Public URL 생성 (S3 또는 CDN)
     * TODO: CDN 도메인 설정 후 CDN URL로 변경
     */
    private String generatePublicUrl(String objectKey) {
        // [Optional] 만약 CloudFront URL이 설정에 있다면 그것을 사용
        // if (s3Properties.getCdnUrl() != null) {
        //     return s3Properties.getCdnUrl() + "/" + objectKey;
        // }

        // 기본 S3 URL (Virtual-hosted-style)
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
            s3Properties.getBucket(),
            s3Properties.getRegion(),
            objectKey
        );
    }
}
