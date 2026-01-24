package com.imyme.mine.domain.storage.controller;

import com.imyme.mine.domain.storage.dto.PresignedUrlRequest;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.domain.storage.service.StorageService;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/learning")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> generatePresignedUrl(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody PresignedUrlRequest request
    ) {
        Long userId = extractUserId(authorization);
        log.info("POST /learning/presigned-url - userId: {}, cardId: {}", userId, request.cardId());

        PresignedUrlResponse response = storageService.generatePresignedUrl(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Long extractUserId(String authorization) {
        String token = jwtTokenProvider.extractToken(authorization);

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
