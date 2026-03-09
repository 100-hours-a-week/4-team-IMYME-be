/**
 * 시나리오 A: PvP 방 목록 조회 N+1 부하 테스트
 *
 * Before/After 비교:
 *   Before: PvpRoomRepository에 FETCH JOIN 없음 → N+1 쿼리
 *   After:  FETCH JOIN 적용 → 단일 쿼리
 *
 * 데이터 준비 (앱 실행 후):
 *   BASE_URL=http://localhost:8080
 *   curl -s -X DELETE "$BASE_URL/test/perf/reset"
 *   curl -s -X POST "$BASE_URL/test/perf/bootstrap-users?count=100" | jq '.data' > perf/data/users.json
 *   curl -s -X POST "$BASE_URL/test/perf/bootstrap-open-rooms?count=200" | jq '.data' > perf/data/open-rooms.json
 *
 * 실행:
 *   k6 run --out influxdb=http://localhost:8086/k6 monitoring/k6-scripts/pvp-scenario-a-room-list.js
 *
 * (InfluxDB 없이 터미널 출력만 원할 경우)
 *   k6 run monitoring/k6-scripts/pvp-scenario-a-room-list.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
const users = new SharedArray('users', () => JSON.parse(open('../../perf/data/users.json')));
const categoryIds = new SharedArray('categoryIds', () => {
  const data = JSON.parse(open('../../perf/data/open-rooms.json'));
  return data.categoryIdsUsed || [];
});

// ── 설정 ─────────────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ── 커스텀 메트릭 ─────────────────────────────────────────────────────────────
const roomListLatency = new Trend('room_list_latency', true);
const errorRate = new Rate('http_req_failed');

// ── 부하 단계 ─────────────────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: '2m', target: 0 },   // baseline (워밍업)
    { duration: '1m', target: 20 },  // ramp up
    { duration: '2m', target: 20 },  // hold
    { duration: '1m', target: 50 },  // ramp up
    { duration: '2m', target: 50 },  // hold
    { duration: '1m', target: 100 }, // ramp up
    { duration: '3m', target: 100 }, // hold (측정 구간)
    { duration: '2m', target: 0 },   // recovery
  ],
  thresholds: {
    room_list_latency: ['p(95)<500'],  // p95 < 500ms
    http_req_failed: ['rate<0.01'],    // 에러율 < 1%
  },
};

// ── VU 로직 ──────────────────────────────────────────────────────────────────
export default function () {
  const user = users[__VU % users.length];
  const headers = {
    Authorization: `Bearer ${user.accessToken}`,
    'Content-Type': 'application/json',
  };

  // 30% 확률로 categoryId 필터 적용
  let url = `${BASE_URL}/pvp/rooms?size=20`;
  if (Math.random() < 0.3 && categoryIds.length > 0) {
    const categoryId = categoryIds[Math.floor(Math.random() * categoryIds.length)];
    url += `&categoryId=${categoryId}`;
  }

  const res = http.get(url, { headers });

  roomListLatency.add(res.timings.duration);
  errorRate.add(res.status !== 200);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has data': (r) => {
      try {
        return JSON.parse(r.body).success === true;
      } catch {
        return false;
      }
    },
  });

  sleep(2);
}