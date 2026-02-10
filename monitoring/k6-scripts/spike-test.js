// 스파이크 테스트 - 급격한 부하 증가 시나리오
// 트래픽이 갑자기 급증했을 때 시스템이 견딜 수 있는지 테스트
// 사용법: k6 run spike-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '10s', target: 10 },   // 정상 부하
    { duration: '10s', target: 500 },  // 급격한 트래픽 증가 (스파이크)
    { duration: '30s', target: 500 },  // 스파이크 유지
    { duration: '10s', target: 10 },   // 정상 부하로 복귀
    { duration: '10s', target: 0 },    // 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'], // 스파이크 상황에서는 2초까지 허용
    http_req_failed: ['rate<0.1'],     // 에러율 10% 미만
  },
};

const BASE_URL = 'http://host.docker.internal:8080';

export default function () {
  const responses = http.batch([
    ['GET', `${BASE_URL}/actuator/health`],
    ['GET', `${BASE_URL}/categories`],
    ['GET', `${BASE_URL}/keywords`],
  ]);

  responses.forEach((res, index) => {
    check(res, {
      [`request ${index} status is 200`]: (r) => r.status === 200,
    }) || errorRate.add(1);
  });

  sleep(1);
}