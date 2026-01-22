package com.imyme.mine.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OAuth 로그인 요청 DTO
 */
@Getter
@NoArgsConstructor
public class OAuthLoginRequest {

    @NotBlank(message = "Authorization code는 필수입니다")
    private String code;

    @JsonProperty("redirect_uri")
    @NotBlank(message = "Redirect URI는 필수입니다")
    private String redirectUri;

    @JsonProperty("device_uuid")
    @NotBlank(message = "Device UUID는 필수입니다")
    @Pattern(
        regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        message = "유효한 UUID 형식이 아닙니다"
    )
    private String deviceUuid;
}
