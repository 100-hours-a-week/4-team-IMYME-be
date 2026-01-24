package com.imyme.mine.global.controller;

import com.imyme.mine.domain.card.dto.AttemptCreateRequest;
import com.imyme.mine.domain.card.dto.AttemptCreateResponse;
import com.imyme.mine.domain.card.dto.AttemptDetailResponse;
import com.imyme.mine.domain.card.dto.CardCreateRequest;
import com.imyme.mine.domain.card.dto.CardResponse;
import com.imyme.mine.domain.card.service.AttemptService;
import com.imyme.mine.domain.card.service.CardService;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AttemptService attemptService;
    private final CardService cardService;

    private static final Long TEST_USER_ID = 1L;

    @GetMapping("/token/{userId}")
    public ResponseEntity<Map<String, String>> generateTestToken(@PathVariable Long userId) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        return ResponseEntity.ok(Map.of(
            "access_token", accessToken,
            "token_type", "Bearer"
        ));
    }

    @PostMapping("/cards")
    public ResponseEntity<CardResponse> createCard(@RequestBody CardCreateRequest request) {
        CardResponse response = cardService.createCard(TEST_USER_ID, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/cards/{cardId}/attempts")
    public ResponseEntity<AttemptCreateResponse> createAttempt(
        @PathVariable Long cardId,
        @RequestBody(required = false) AttemptCreateRequest request
    ) {
        if (request == null) {
            request = new AttemptCreateRequest(null);
        }
        AttemptCreateResponse response = attemptService.createAttempt(TEST_USER_ID, cardId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/cards/{cardId}/attempts/{attemptId}")
    public ResponseEntity<AttemptDetailResponse> getAttemptDetail(
        @PathVariable Long cardId,
        @PathVariable Long attemptId
    ) {
        AttemptDetailResponse response = attemptService.getAttemptDetail(TEST_USER_ID, cardId, attemptId);
        return ResponseEntity.ok(response);
    }
}