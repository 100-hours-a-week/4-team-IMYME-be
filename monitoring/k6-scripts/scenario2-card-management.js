import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 시나리오 2: 카드 관리 부하 테스트
 * - JWT 인증 후 카드 CRUD 작업
 * - 카드 목록 조회, 카드 생성, 카드 상세 조회
 */

export const options = {
  stages: [
    { duration: '20s', target: 10 },  // 20초 동안 10명까지 증가
    { duration: '40s', target: 30 },  // 40초 동안 30명 유지
    { duration: '20s', target: 0 },   // 20초 동안 0명으로 감소
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95%의 요청이 500ms 이하
    http_req_failed: ['rate<0.05'],    // 실패율 5% 이하
  },
};

const BASE_URL = 'http://host.docker.internal:8080';

// ⚠️ 실제 테스트 시 유효한 JWT 토큰으로 교체 필요
// 토큰 발급 방법:
// 1. Swagger UI에서 로그인 후 토큰 복사
// 2. 또는 /auth/login API 호출 후 응답에서 추출
const JWT_TOKEN = 'YOUR_JWT_TOKEN_HERE';

export default function () {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${JWT_TOKEN}`,
  };

  // 1. 카드 목록 조회
  let res = http.get(`${BASE_URL}/cards`, { headers });
  check(res, {
    'cards list status is 200': (r) => r.status === 200,
    'cards list is array': (r) => Array.isArray(r.json('data')),
  });

  sleep(1);

  // 2. 카테고리 목록 조회
  res = http.get(`${BASE_URL}/categories`, { headers });
  check(res, {
    'categories status is 200': (r) => r.status === 200,
  });

  sleep(1);

  // 3. 카드 생성 (선택적 - 실제 데이터 생성됨 주의)
  // const cardPayload = JSON.stringify({
  //   categoryId: 1,
  //   question: `Load Test Question ${Date.now()}`,
  // });
  // res = http.post(`${BASE_URL}/cards`, cardPayload, { headers });
  // check(res, {
  //   'card created': (r) => r.status === 201,
  // });

  sleep(2);
}

export function handleSummary(data) {
  return {
    'summary-scenario2.json': JSON.stringify(data),
    stdout: '\n✅ 시나리오 2 완료: 카드 관리 테스트\n',
  };
}