/**
 * 시나리오 C: PvP 타이머 포화 테스트 (Thread.sleep 병목 관찰)
 *
 * 설계: 제출 없이 타이머만 관찰
 *   bootstrap-matched-rooms 호출 시 scheduleThinkingTransition이 예약됨
 *   k6는 roomId + token으로 상태 polling만 수행
 *
 * 상태 전환 타이밍 (실제 코드 기준):
 *   0s   → MATCHED
 *   ~3s  → THINKING
 *   ~33s → RECORDING
 *   ~113s → CANCELED (제출 없으므로 — PvpAsyncService:257)
 *
 * Before/After 비교:
 *   Before: Thread.sleep 기반 → Virtual Thread 포화
 *   After:  ScheduledExecutorService 기반 → Thread 재사용
 *
 * 데이터 준비 (앱 실행 후, users.json이 있어야 함):
 *   BASE_URL=http://localhost:8080
 *   curl -s -X POST "$BASE_URL/test/perf/bootstrap-matched-rooms?count=50" | jq '.data' > perf/data/matched-rooms.json
 *
 * 실행:
 *   k6 run --out influxdb=http://localhost:8086/k6 monitoring/k6-scripts/pvp-scenario-c-timer.js
 *
 * Actuator 병행 관찰 (별도 터미널):
 *   while true; do clear; curl -s http://localhost:8080/actuator/metrics/jvm.threads.live; sleep 2; done
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
const matchedRooms = new SharedArray('matchedRooms', () => {
  const data = JSON.parse(open('../../perf/data/matched-rooms.json'));
  return data.rooms || data; // {created, rooms:[...]} 또는 배열 직접
});

const users = new SharedArray('users', () => JSON.parse(open('../../perf/data/users.json')));

// ── 설정 ─────────────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const POLL_INTERVAL_SEC = 5;
const MAX_POLL_SEC = 130; // 113초 + 여유 17초

// userId → accessToken 매핑
const tokenByUserId = {};
for (const u of users) {
  tokenByUserId[u.userId] = u.accessToken;
}

// ── 커스텀 메트릭 ─────────────────────────────────────────────────────────────
const timerTransitionSuccess = new Counter('timer_transition_canceled');  // CANCELED 도달 수
const timerTransitionFail = new Counter('timer_transition_fail');        // 시간 초과 또는 잘못된 상태
const pollLatency = new Trend('poll_latency', true);
const errorRate = new Rate('http_req_failed');

// ── 부하 설정: 방 수만큼 VU ──────────────────────────────────────────────────
export const options = {
  vus: matchedRooms.length,   // 방 개수 = VU 수 (1:1 매핑)
  duration: `${MAX_POLL_SEC}s`,
  thresholds: {
    // CANCELED 도달률 80% 이상 (타이머가 정상 동작하면 거의 100%)
    timer_transition_canceled: ['count>0'],
    poll_latency: ['p(95)<1000'],
  },
};

// ── VU 로직 ──────────────────────────────────────────────────────────────────
export default function () {
  const roomIndex = (__VU - 1) % matchedRooms.length;
  const seed = matchedRooms[roomIndex];
  const { roomId, hostUserId } = seed;

  const token = tokenByUserId[hostUserId];
  if (!token) {
    console.error(`[perf] token not found for userId=${hostUserId}`);
    timerTransitionFail.add(1);
    return;
  }

  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  let elapsed = 0;
  let finalStatus = null;

  // MAX_POLL_SEC 동안 5초 간격으로 polling
  while (elapsed < MAX_POLL_SEC) {
    const res = http.get(`${BASE_URL}/pvp/rooms/${roomId}`, { headers });

    pollLatency.add(res.timings.duration);
    errorRate.add(res.status !== 200);

    if (res.status === 200) {
      try {
        const body = JSON.parse(res.body);
        const status = body.data?.status;
        finalStatus = status;

        check(res, { 'status is 200': () => true });

        if (status === 'CANCELED') {
          timerTransitionSuccess.add(1);
          console.log(`[perf] roomId=${roomId} → CANCELED (elapsed=${elapsed}s) ✓`);
          return;
        }

        // PROCESSING은 예상치 못한 상태 (제출이 발생했다는 의미)
        if (status === 'PROCESSING' || status === 'FINISHED') {
          timerTransitionFail.add(1);
          console.warn(`[perf] roomId=${roomId} → 예상치 못한 상태=${status} (elapsed=${elapsed}s)`);
          return;
        }
      } catch (e) {
        console.error(`[perf] roomId=${roomId} 응답 파싱 실패: ${e}`);
      }
    }

    sleep(POLL_INTERVAL_SEC);
    elapsed += POLL_INTERVAL_SEC;
  }

  // 시간 초과
  timerTransitionFail.add(1);
  console.warn(`[perf] roomId=${roomId} → 시간 초과 (finalStatus=${finalStatus})`);
}