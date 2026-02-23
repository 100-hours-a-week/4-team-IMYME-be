package com.imyme.mine.domain.pvp.controller;

import com.imyme.mine.domain.pvp.dto.request.CreateRoomRequest;
import com.imyme.mine.domain.pvp.dto.response.RoomListResponse;
import com.imyme.mine.domain.pvp.dto.response.RoomResponse;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.service.PvpRoomService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "PvP Room", description = "PvP 대결 방 API")
@Slf4j
@RestController
@RequestMapping("/pvp/rooms")
@RequiredArgsConstructor
public class PvpRoomController {

    private final PvpRoomService pvpRoomService;

    /**
     * 4.1 방 목록 조회
     */
    @Operation(summary = "방 목록 조회", description = "PvP 대결 방 목록을 커서 페이징으로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @GetMapping
    public ApiResponse<RoomListResponse> getRooms(
            @CurrentUser UserPrincipal principal,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "OPEN") PvpRoomStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {

        log.info("방 목록 조회: userId={}, categoryId={}, status={}", principal.getId(), categoryId, status);
        return ApiResponse.success(pvpRoomService.getRooms(categoryId, status, cursor, size));
    }

    /**
     * 4.2 방 생성
     */
    @Operation(summary = "방 생성", description = "PvP 대결 방을 생성합니다.")
    @SecurityRequirement(name = "JWT")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomResponse> createRoom(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateRoomRequest request) {

        log.info("방 생성: userId={}, categoryId={}, roomName={}", principal.getId(), request.categoryId(), request.roomName());
        RoomResponse response = pvpRoomService.createRoom(principal.getId(), request);
        return ApiResponse.success(response, "방이 생성되었습니다.");
    }

    /**
     * 4.4 방 상태 조회
     */
    @Operation(summary = "방 상태 조회", description = "PvP 대결 방의 현재 상태를 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long roomId) {

        log.info("방 상태 조회: userId={}, roomId={}", principal.getId(), roomId);
        return ApiResponse.success(pvpRoomService.getRoom(principal.getId(), roomId));
    }
}