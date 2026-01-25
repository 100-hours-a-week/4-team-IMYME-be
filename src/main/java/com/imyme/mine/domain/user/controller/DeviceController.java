package com.imyme.mine.domain.user.controller;

import com.imyme.mine.domain.user.dto.RegisterDeviceRequest;
import com.imyme.mine.domain.user.dto.RegisterDeviceResponse;
import com.imyme.mine.domain.user.service.DeviceService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 기기 관리 컨트롤러
 * - FCM 토큰 등록 및 기기 정보 관리
 */
@Slf4j
@RestController
@RequestMapping("/users/me/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    // 기기 등록 또는 업데이트
    @PostMapping
    public ApiResponse<RegisterDeviceResponse> registerDevice(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody RegisterDeviceRequest request
    ) {
        log.info("기기 등록 요청: userId={}, deviceUuid={}", userPrincipal.getId(), request.deviceUuid());

        RegisterDeviceResponse response = deviceService.registerDevice(userPrincipal.getId(), request);

        return ApiResponse.success(response, "기기가 등록되었습니다.");
    }

    // 기기 삭제 (Soft Delete)
    @DeleteMapping("/{deviceUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable String deviceUuid
    ) {
        log.info("기기 삭제 요청: userId={}, deviceUuid={}", userPrincipal.getId(), deviceUuid);

        deviceService.deleteDevice(userPrincipal.getId(), deviceUuid);

        log.info("기기 삭제 완료: deviceUuid={}", deviceUuid);
    }
}
