package com.imyme.mine.domain.perf.controller;

import com.imyme.mine.domain.auth.dto.OAuthLoginResponse;
import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.RoleType;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.service.OAuthService;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.perf.dto.MatchedRoomSeed;
import com.imyme.mine.domain.perf.dto.PerfUserToken;
import com.imyme.mine.domain.perf.service.PerfRoomSaveService;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.service.PvpAsyncService;
import com.imyme.mine.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Hidden
@Profile("perf")
@RestController
@RequestMapping("/test/perf")
@RequiredArgsConstructor
public class PerfBootstrapController {

    private final UserRepository userRepository;
    private final OAuthService oauthService;
    private final CategoryRepository categoryRepository;
    private final KeywordRepository keywordRepository;
    private final PvpRoomRepository pvpRoomRepository;
    private final PvpAsyncService pvpAsyncService;
    private final PerfRoomSaveService perfRoomSaveService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * perf 유저 N명 find-or-create + JWT 발급
     * 반환: [{userId, nickname, deviceUuid, accessToken}, ...]
     */
    @PostMapping("/bootstrap-users")
    public ApiResponse<List<PerfUserToken>> bootstrapUsers(
            @RequestParam(defaultValue = "100") int count) {

        List<PerfUserToken> tokens = new ArrayList<>(count);

        for (int i = 1; i <= count; i++) {
            String oauthId = String.format("perf_user_%03d", i);
            String nickname = String.format("perf%03d", i);
            String deviceUuid = "00000000-0000-0000-0000-" + String.format("%012d", i);

            User user = userRepository.findByOauthIdAndOauthProvider(oauthId, OAuthProviderType.E2E_TEST)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .oauthId(oauthId)
                            .oauthProvider(OAuthProviderType.E2E_TEST)
                            .nickname(nickname)
                            .role(RoleType.USER)
                            .level(1)
                            .totalCardCount(0)
                            .activeCardCount(0)
                            .consecutiveDays(1)
                            .winCount(0)
                            .build()));

            OAuthLoginResponse loginResponse = oauthService.login(user, deviceUuid, false);
            tokens.add(new PerfUserToken(user.getId(), user.getNickname(), deviceUuid, loginResponse.accessToken()));
        }

        log.info("[perf] bootstrap-users 완료: {}명", count);
        return ApiResponse.success(tokens);
    }

    /**
     * OPEN 방 N개 생성 (perf 유저 호스트, 카테고리 라운드로빈)
     * 반환: {created: N, categoryIdsUsed: [...]}
     */
    @PostMapping("/bootstrap-open-rooms")
    public ApiResponse<Map<String, Object>> bootstrapOpenRooms(
            @RequestParam(defaultValue = "200") int count) {

        List<Category> categories = categoryRepository.findAllByIsActiveOrderByDisplayOrderAsc(true);
        if (categories.isEmpty()) {
            return ApiResponse.success(Map.of("created", 0, "categoryIdsUsed", List.of()));
        }

        List<User> perfUsers = loadPerfUsers(100);
        if (perfUsers.isEmpty()) {
            return ApiResponse.success(Map.of("created", 0, "categoryIdsUsed", List.of()));
        }

        for (int i = 0; i < count; i++) {
            Category category = categories.get(i % categories.size());
            User host = perfUsers.get(i % perfUsers.size());

            PvpRoom room = PvpRoom.builder()
                    .category(category)
                    .keyword(null)
                    .roomName("perf-open-" + (i + 1))
                    .hostUser(host)
                    .hostNickname(host.getNickname())
                    .status(PvpRoomStatus.OPEN)
                    .version(0L)
                    .build();

            pvpRoomRepository.save(room);
        }

        List<Long> categoryIdsUsed = categories.stream()
                .map(Category::getId)
                .collect(Collectors.toList());

        log.info("[perf] bootstrap-open-rooms 완료: {}개", count);
        return ApiResponse.success(Map.of("created", count, "categoryIdsUsed", categoryIdsUsed));
    }

    /**
     * MATCHED 방 N개 생성 + scheduleThinkingTransition 예약
     * - active keyword가 있는 category만 사용 (THINKING 전환 조건)
     * - PerfRoomSaveService로 방 단위 commit (self-call 프록시 우회)
     * 반환: {created: N, rooms: [{roomId, hostUserId, guestUserId}]}
     */
    @PostMapping("/bootstrap-matched-rooms")
    public ApiResponse<Map<String, Object>> bootstrapMatchedRooms(
            @RequestParam(defaultValue = "50") int count) {

        // active keyword가 있는 categoryId 집합
        Set<Long> categoriesWithKeywords = keywordRepository.findAllWithCategoryByIsActive(true)
                .stream()
                .map(k -> k.getCategory().getId())
                .collect(Collectors.toSet());

        List<Category> categories = categoryRepository.findAllByIsActiveOrderByDisplayOrderAsc(true)
                .stream()
                .filter(c -> categoriesWithKeywords.contains(c.getId()))
                .collect(Collectors.toList());

        if (categories.isEmpty()) {
            log.warn("[perf] bootstrap-matched-rooms 실패: active keyword가 있는 category 없음");
            return ApiResponse.success(Map.of("created", 0, "rooms", List.of()));
        }

        List<User> perfUsers = loadPerfUsers(100);
        if (perfUsers.size() < 2) {
            log.warn("[perf] bootstrap-matched-rooms 실패: perf 유저 2명 이상 필요");
            return ApiResponse.success(Map.of("created", 0, "rooms", List.of()));
        }

        List<MatchedRoomSeed> rooms = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Category category = categories.get(i % categories.size());
            User host = perfUsers.get((i * 2) % perfUsers.size());
            User guest = perfUsers.get((i * 2 + 1) % perfUsers.size());

            PvpRoom room = PvpRoom.builder()
                    .category(category)
                    .keyword(null)
                    .roomName("perf-matched-" + (i + 1))
                    .hostUser(host)
                    .hostNickname(host.getNickname())
                    .status(PvpRoomStatus.OPEN)
                    .version(0L)
                    .build();

            // MATCHED 전환: joinGuest()가 status=MATCHED + matchedAt 세팅
            room.joinGuest(guest, guest.getNickname());

            // 별도 @Service @Transactional로 저장 → commit 보장 후 schedule
            PvpRoom saved = perfRoomSaveService.saveRoom(room);
            pvpAsyncService.scheduleThinkingTransition(saved.getId());

            rooms.add(new MatchedRoomSeed(saved.getId(), host.getId(), guest.getId()));
            log.info("[perf] MATCHED 방 생성: roomId={}, hostId={}, guestId={}",
                    saved.getId(), host.getId(), guest.getId());
        }

        log.info("[perf] bootstrap-matched-rooms 완료: {}개", count);
        return ApiResponse.success(Map.of("created", count, "rooms", rooms));
    }

    /**
     * perf 데이터 전체 hard delete
     * - User·PvpFeedback 모두 soft delete이므로 native hard delete 필수 (unique 제약 충돌 방지)
     * 삭제 순서 (FK): feedbacks → submissions → histories → rooms → user_sessions → users
     */
    @Transactional
    @DeleteMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        // 1. pvp_feedbacks
        entityManager.createNativeQuery(
                "DELETE FROM pvp_feedbacks WHERE room_id IN " +
                "(SELECT id FROM pvp_rooms WHERE host_user_id IN " +
                "(SELECT id FROM users WHERE oauth_id LIKE 'perf_user_%' AND oauth_provider = 'E2E_TEST'))"
        ).executeUpdate();

        // 2. pvp_submissions
        entityManager.createNativeQuery(
                "DELETE FROM pvp_submissions WHERE room_id IN " +
                "(SELECT id FROM pvp_rooms WHERE host_user_id IN " +
                "(SELECT id FROM users WHERE oauth_id LIKE 'perf_user_%' AND oauth_provider = 'E2E_TEST'))"
        ).executeUpdate();

        // 3. pvp_histories
        entityManager.createNativeQuery(
                "DELETE FROM pvp_histories WHERE room_id IN " +
                "(SELECT id FROM pvp_rooms WHERE host_user_id IN " +
                "(SELECT id FROM users WHERE oauth_id LIKE 'perf_user_%' AND oauth_provider = 'E2E_TEST'))"
        ).executeUpdate();

        // 4. pvp_rooms
        int deletedRooms = entityManager.createNativeQuery(
                "DELETE FROM pvp_rooms WHERE host_user_id IN " +
                "(SELECT id FROM users WHERE oauth_id LIKE 'perf_user_%' AND oauth_provider = 'E2E_TEST')"
        ).executeUpdate();

        // 5. user_sessions (users 삭제 전 FK 해제)
        entityManager.createNativeQuery(
                "DELETE FROM user_sessions WHERE user_id IN " +
                "(SELECT id FROM users WHERE oauth_id LIKE 'perf_user_%' AND oauth_provider = 'E2E_TEST')"
        ).executeUpdate();

        // 6. users (hard delete — soft delete unique 제약 충돌 방지)
        int deletedUsers = entityManager.createNativeQuery(
                "DELETE FROM users WHERE oauth_id LIKE 'perf_user_%' AND oauth_provider = 'E2E_TEST'"
        ).executeUpdate();

        log.info("[perf] reset 완료: users={}, rooms={}", deletedUsers, deletedRooms);
        return ApiResponse.success(Map.of("deletedUsers", deletedUsers, "deletedRooms", deletedRooms));
    }

    /**
     * oauthId 순서대로 perf 유저 조회 (최대 maxCount명)
     */
    private List<User> loadPerfUsers(int maxCount) {
        List<User> users = new ArrayList<>(maxCount);
        for (int i = 1; i <= maxCount; i++) {
            String oauthId = String.format("perf_user_%03d", i);
            userRepository.findByOauthIdAndOauthProvider(oauthId, OAuthProviderType.E2E_TEST)
                    .ifPresent(users::add);
        }
        return users;
    }
}