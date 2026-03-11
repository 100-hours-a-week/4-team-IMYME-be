package com.imyme.mine.domain.challenge.controller;

import com.imyme.mine.domain.challenge.dto.response.ChallengeHistoryResponse;
import com.imyme.mine.domain.challenge.dto.response.ChallengeRankingResponse;
import com.imyme.mine.domain.challenge.dto.response.TodayChallengeResponse;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.service.ChallengeQueryService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "11. Challenge", description = "챌린지 API")
@RestController
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeQueryService challengeQueryService;

    @Operation(summary = "오늘의 챌린지 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/today")
    public ApiResponse<TodayChallengeResponse> getTodayChallenge(
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeQueryService.getTodayChallenge(principal.getId()));
    }

    @Operation(summary = "챌린지 랭킹 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{challengeId}/rankings")
    public ApiResponse<ChallengeRankingResponse> getRankings(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeQueryService.getRankings(challengeId, principal.getId(), page, size));
    }

    @Operation(summary = "챌린지 히스토리 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ApiResponse<ChallengeHistoryResponse> getChallengeHistory(
            @RequestParam(required = false) ChallengeStatus status,
            @RequestParam(defaultValue = "false") boolean participated,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeQueryService.getChallengeHistory(
                principal.getId(), cursor, size, status, participated));
    }
}