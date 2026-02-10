-- E2E 테스트용 고정 사용자 추가
-- 이 사용자는 E2E 테스트 환경에서만 사용되며, /e2e/login 엔드포인트를 통해 인증 토큰을 발급받을 수 있습니다.

-- E2E_TEST provider 값 추가하기 위해 기존 CHECK 제약조건 삭제 및 재생성
-- (repeatable migration이므로 매번 실행되어도 안전)

-- 참고: inline CHECK 제약조건은 PostgreSQL이 자동으로 이름을 생성합니다
-- users_oauth_provider_check 형태일 가능성이 높음

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_oauth_provider_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_oauth_provider_check1;

-- 새로운 CHECK 제약조건 추가 ('E2E_TEST' 포함)
ALTER TABLE users ADD CONSTRAINT users_oauth_provider_check
    CHECK (oauth_provider IN ('KAKAO', 'GOOGLE', 'APPLE', 'E2E_TEST'));

-- E2E 테스트용 고정 사용자 추가
INSERT INTO users (
    oauth_id,
    oauth_provider,
    email,
    nickname,
    profile_image_url,
    role,
    level,
    total_card_count,
    active_card_count,
    consecutive_days,
    win_count,
    last_login_at,
    created_at,
    updated_at
) VALUES (
    'e2e_test_user',
    'E2E_TEST',
    'e2e@test.com',
    'E2E테스터',
    NULL,
    'USER',
    1,
    0,
    0,
    1,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (oauth_id, oauth_provider) DO NOTHING;
