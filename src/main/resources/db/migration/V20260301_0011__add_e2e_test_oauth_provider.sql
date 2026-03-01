-- E2E 테스트용 OAuth Provider 추가
-- E2E 로그인 API (/e2e/login)에서 사용하는 Provider 타입

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_oauth_provider_check;

ALTER TABLE users ADD CONSTRAINT users_oauth_provider_check
CHECK (oauth_provider IN ('KAKAO', 'GOOGLE', 'APPLE', 'E2E_TEST'));
