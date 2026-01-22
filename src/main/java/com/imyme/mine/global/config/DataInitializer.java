package com.imyme.mine.global.config;

import com.imyme.mine.domain.auth.entity.OAuthProvider;
import com.imyme.mine.domain.auth.entity.Role;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 애플리케이션 시작 시 초기 데이터 생성
 * - 카테고리 및 키워드 기본값 삽입
 */
// TODO : DB 배포 시 제거 예정
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;

    // 데이터 초기화 로직
    @Override
    @Transactional
    public void run(String... args) {
        // 테스트용 사용자는 항상 체크
        createTestUser();

        if (categoryRepository.count() > 0) {
            log.info("카테고리/키워드 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("초기 데이터 생성 시작...");

        // 카테고리 생성
        Category os = createCategory("운영체제", 1);
        Category network = createCategory("네트워크", 2);
        Category database = createCategory("데이터베이스", 3);
        Category dataStructure = createCategory("자료구조", 4);
        Category algorithm = createCategory("알고리즘", 5);

        // 키워드 생성
        Map<Category, List<String>> keywordMap = Map.of(
            os, List.of("프로세스", "스레드", "메모리 관리"),
            network, List.of("TCP/IP", "HTTP"),
            database, List.of("SQL", "인덱스", "트랜잭션"),
            dataStructure, List.of("배열", "링크드리스트", "스택", "큐"),
            algorithm, List.of("정렬", "탐색", "동적 프로그래밍")
        );

        keywordMap.forEach((category, keywords) -> {
            for (int i = 0; i < keywords.size(); i++) {
                createKeyword(category, keywords.get(i), i + 1);
            }
        });

        log.info("초기 데이터 생성 완료!");
    }

    // 테스트용 사용자 생성 헬퍼 메서드
    private void createTestUser() {
        if (userRepository.count() > 0) {
            log.info("테스트 사용자가 이미 존재합니다.");
            return;
        }

        User testUser = User.builder()
            .oauthId("test_user_1")
            .oauthProvider(OAuthProvider.KAKAO)
            .email("test@example.com")
            .nickname("테스트유저")
            .role(Role.USER)
            .level(1)
            .totalCardCount(0)
            .activeCardCount(0)
            .consecutiveDays(1)
            .winCount(0)
            .build();

        userRepository.save(testUser);
        log.info("테스트 사용자 생성 완료 - id: {}", testUser.getId());
    }

    // 카테고리 생성 헬퍼 메서드
    private Category createCategory(String name, int displayOrder) {
        Category category = Category.builder()
            .name(name)
            .displayOrder(displayOrder)
            .isActive(true)
            .build();
        return categoryRepository.save(category);
    }

    // 키워드 생성 헬퍼 메서드
    private void createKeyword(Category category, String name, int displayOrder) {
        Keyword keyword = Keyword.builder()
            .category(category)
            .name(name)
            .displayOrder(displayOrder)
            .isActive(true)
            .build();
        keywordRepository.save(keyword);
    }
}
