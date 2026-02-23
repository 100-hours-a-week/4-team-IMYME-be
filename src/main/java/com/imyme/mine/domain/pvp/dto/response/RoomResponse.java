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
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String roomName;
    private PvpRoomStatus status;
    private Long hostUserId;
    private String hostNickname;
    private Long guestUserId;
    private String guestNickname;
    private KeywordInfo keyword;
    private LocalDateTime createdAt;
    private LocalDateTime matchedAt;
    private LocalDateTime startedAt;
    private LocalDateTime thinkingEndsAt;
    private String message;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class KeywordInfo {
        private Long id;
        private String name;
    }
}
