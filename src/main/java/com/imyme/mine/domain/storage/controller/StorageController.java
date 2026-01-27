package com.imyme.mine.domain.storage.controller;

import com.imyme.mine.domain.storage.dto.PresignedUrlRequest;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.domain.storage.service.StorageService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "9. Study Audio", description = "학습 오디오 업로드용 Presigned URL 발급 API")
@Slf4j
@RestController
@RequestMapping("/learning")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
        summary = "학습 오디오 Presigned URL 발급",
        description = "학습 오디오를 S3에 직접 업로드하기 위한 서명된 URL을 발급합니다. URL 유효기간은 5분입니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Presigned URL 발급 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - Validation 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "카드를 찾을 수 없음 - CARD_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping("/presigned-url")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PresignedUrlResponse> generatePresignedUrl(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody PresignedUrlRequest request
    ) {
        Long userId = extractUserId(authorization);
        log.info("POST /learning/presigned-url - userId: {}, cardId: {}", userId, request.cardId());

        PresignedUrlResponse response = storageService.generatePresignedUrl(userId, request);

        return ApiResponse.success(response);
    }

    private Long extractUserId(String authorization) {
        String token = jwtTokenProvider.extractToken(authorization);

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
