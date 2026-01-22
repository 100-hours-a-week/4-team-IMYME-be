package com.imyme.mine.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * OAuth 로그인 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class OAuthLoginResponse {

    private String accessToken;
    private String refreshToken;
    private String deviceId;
    private UserInfo user;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String oauthId;
        private String oauthProvider;
        private String nickname;
        private String profileImageUrl;
        private Integer level;
        private Integer totalCardCount;
        private Integer activeCardCount;
        private Integer consecutiveDays;
        private Integer winCount;
        private Boolean isNewUser;
    }
}
