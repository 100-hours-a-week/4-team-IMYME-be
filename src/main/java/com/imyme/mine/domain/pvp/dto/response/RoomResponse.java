package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 방 상세 응답 (4.2, 4.3, 4.4)
 */
@Getter
@AllArgsConstructor
@Builder
public class RoomResponse {
    private RoomInfo room;
    private CategoryInfo category;
    private PvpRoomStatus status;
    private KeywordInfo keyword;
    private UserInfo host;
    private UserInfo guest;
    private LocalDateTime createdAt;
    private LocalDateTime matchedAt;
    private LocalDateTime startedAt;
    private LocalDateTime thinkingEndsAt;
    private String message;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class RoomInfo {
        private Long id;
        private String name;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private Long id;
        private String nickname;
        private String profileImageUrl;
        private Integer level;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class KeywordInfo {
        private Long id;
        private String name;
    }
}
