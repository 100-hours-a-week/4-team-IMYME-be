package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방 목록 조회 응답 (4.1)
 */
@Getter
@AllArgsConstructor
@Builder
public class RoomListResponse {
    private List<RoomItem> rooms;
    private PageMeta meta;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class RoomItem {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private String roomName;
        private PvpRoomStatus status;
        private Long hostUserId;
        private String hostNickname;
        private LocalDateTime createdAt;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class PageMeta {
        private Integer size;
        private Boolean hasNext;
        private String nextCursor;
    }
}
