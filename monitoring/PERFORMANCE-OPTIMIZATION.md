# 🚀 성능 개선을 위한 지표 가이드

성능 개선할 때 **무엇을 측정하고, 어떻게 개선할지** 정리한 문서입니다.

---

## 📋 목차
1. [성능 개선 프로세스](#성능-개선-프로세스)
2. [병목 지점별 지표](#병목-지점별-지표)
3. [상황별 성능 개선 전략](#상황별-성능-개선-전략)
4. [실전 예시](#실전-예시)

---

## 성능 개선 프로세스

### 🎯 **3단계 접근법**

```
1️⃣ 측정 (Measure)
   → 현재 성능 파악
   → 병목 지점 식별

2️⃣ 분석 (Analyze)
   → 원인 진단
   → 개선 방향 결정

3️⃣ 개선 & 검증 (Improve & Verify)
   → 코드 수정
   → 개선 효과 측정
```

---

## 병목 지점별 지표

### 🐌 1. 응답 속도가 느릴 때

#### **측정할 지표**

| 지표 | Prometheus 메트릭 | 목표 | 위험 수준 |
|---|---|---|---|
| **평균 응답 시간** | `rate(http_server_requests_seconds_sum[1m]) / rate(http_server_requests_seconds_count[1m])` | < 100ms | > 500ms |
| **P95 응답 시간** | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))` | < 500ms | > 1s |
| **P99 응답 시간** | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))` | < 1s | > 3s |
| **최대 응답 시간** | `http_server_requests_seconds_max` | < 2s | > 5s |

#### **Grafana 쿼리**
```promql
# 엔드포인트별 평균 응답 시간
rate(http_server_requests_seconds_sum[5m])
/
rate(http_server_requests_seconds_count[5m])
by (uri, method)
```

#### **개선 전략**
1. **느린 엔드포인트 찾기** → 위 쿼리로 정렬
2. **원인 파악** → DB 쿼리? 외부 API? 연산?
3. **개선 방법**
   - DB 쿼리 최적화 (인덱스 추가)
   - N+1 쿼리 해결 (fetch join)
   - 캐싱 도입 (Redis)
   - 비동기 처리 (CompletableFuture)

---

### 💾 2. 데이터베이스 병목

#### **측정할 지표**

| 지표 | Prometheus 메트릭 | 목표 | 위험 수준 |
|---|---|---|---|
| **활성 커넥션** | `hikaricp_connections_active` | < 7 (70%) | = 10 (100%) |
| **대기 중인 요청** | `hikaricp_connections_pending` | = 0 | > 0 |
| **커넥션 획득 시간** | `rate(hikaricp_connections_acquire_seconds_sum[1m]) / rate(hikaricp_connections_acquire_seconds_count[1m])` | < 10ms | > 100ms |
| **커넥션 타임아웃** | `increase(hikaricp_connections_timeout_total[5m])` | = 0 | > 0 |
| **Repository 실행 시간** | `rate(spring_data_repository_invocations_seconds_sum[1m]) / rate(spring_data_repository_invocations_seconds_count[1m])` | < 50ms | > 200ms |

#### **Grafana 쿼리**
```promql
# 커넥션 사용률
(hikaricp_connections_active / hikaricp_connections_max) * 100

# Repository별 평균 실행 시간
rate(spring_data_repository_invocations_seconds_sum[5m])
/
rate(spring_data_repository_invocations_seconds_count[5m])
by (repository, method)
```

#### **개선 전략**

##### **Case 1: 활성 커넥션 = 10개 (포화)**
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 10 → 20 증가
      minimum-idle: 10
```

##### **Case 2: 느린 쿼리**
```sql
-- PostgreSQL에서 느린 쿼리 찾기
SELECT
  calls,
  total_time / calls as avg_time_ms,
  query
FROM pg_stat_statements
ORDER BY total_time DESC
LIMIT 10;

-- 해결: 인덱스 추가
CREATE INDEX idx_card_user_id ON cards(user_id);
CREATE INDEX idx_attempt_card_id ON card_attempts(card_id);
```

##### **Case 3: N+1 쿼리**
```java
// 잘못된 코드 (N+1 발생)
List<Card> cards = cardRepository.findAll();
for (Card card : cards) {
    card.getKeyword().getName();  // N번의 추가 쿼리!
}

// 개선된 코드 (fetch join)
@Query("SELECT c FROM Card c JOIN FETCH c.keyword")
List<Card> findAllWithKeyword();
```

---

### 🧠 3. 메모리 문제 (힙 메모리 부족)

#### **측정할 지표**

| 지표 | Prometheus 메트릭 | 목표 | 위험 수준 |
|---|---|---|---|
| **힙 사용률** | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` | < 70% | > 85% |
| **GC 횟수 (Minor)** | `rate(jvm_gc_pause_seconds_count{action="end of minor GC"}[1m])` | < 5/초 | > 10/초 |
| **GC 시간** | `rate(jvm_gc_pause_seconds_sum[1m])` | < 100ms | > 500ms |
| **GC 후 힙 사용량** | `jvm_memory_usage_after_gc{area="heap"}` | < 0.7 | > 0.85 |
| **Old Gen 승격률** | `rate(jvm_gc_memory_promoted_bytes_total[1m])` | 낮을수록 좋음 | 높음 |

#### **Grafana 쿼리**
```promql
# 힙 메모리 사용률
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# GC 시간 비율 (%)
(rate(jvm_gc_pause_seconds_sum[1m]) / 60) * 100

# 메모리 할당률 (MB/s)
rate(jvm_gc_memory_allocated_bytes_total[1m]) / 1024 / 1024
```

#### **개선 전략**

##### **Case 1: 힙 크기 부족**
```bash
# JVM 옵션 수정 (application.properties or 실행 옵션)
-Xms512m     # 초기 힙: 512MB
-Xmx2g       # 최대 힙: 2GB
```

##### **Case 2: 메모리 누수**
```bash
# Heap Dump 생성
jmap -dump:live,format=b,file=heap.bin <PID>

# VisualVM or Eclipse MAT로 분석
# 찾는 것: 크기가 큰 객체, 계속 증가하는 컬렉션
```

**주요 원인:**
- 무한히 증가하는 리스트/맵
- 클로즈되지 않은 리소스 (Stream, Connection)
- 캐시 크기 제한 없음

##### **Case 3: 불필요한 객체 생성**
```java
// 잘못된 코드
for (int i = 0; i < 1000000; i++) {
    String str = new String("test");  // 매번 새 객체!
}

// 개선된 코드
String str = "test";  // 상수 풀 재사용
for (int i = 0; i < 1000000; i++) {
    // str 재사용
}
```

---

### ⚡ 4. CPU 병목

#### **측정할 지표**

| 지표 | Prometheus 메트릭 | 목표 | 위험 수준 |
|---|---|---|---|
| **CPU 사용률** | `process_cpu_usage` | < 70% | > 90% |
| **시스템 CPU 사용률** | `system_cpu_usage` | < 70% | > 90% |
| **실행 가능 스레드** | `jvm_threads_states_threads{state="runnable"}` | 적을수록 좋음 | 많음 |

#### **Grafana 쿼리**
```promql
# CPU 사용률
process_cpu_usage * 100

# 코어당 사용률
system_cpu_usage / system_cpu_count
```

#### **개선 전략**

##### **Case 1: 무거운 연산**
```java
// 잘못된 코드 (CPU 집약적)
for (User user : users) {
    String hash = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());  // 매우 느림!
}

// 개선된 코드 (비동기 처리)
@Async
CompletableFuture<String> hashPassword(String password) {
    return CompletableFuture.completedFuture(
        BCrypt.hashpw(password, BCrypt.gensalt())
    );
}
```

##### **Case 2: 불필요한 정규식**
```java
// 잘못된 코드
for (String str : largeList) {
    if (str.matches(".*test.*")) {  // 매번 정규식 컴파일!
        // ...
    }
}

// 개선된 코드
Pattern pattern = Pattern.compile(".*test.*");  // 한 번만 컴파일
for (String str : largeList) {
    if (pattern.matcher(str).matches()) {
        // ...
    }
}
```

---

### 🔄 5. 동시성 문제 (스레드 경합)

#### **측정할 지표**

| 지표 | Prometheus 메트릭 | 목표 | 위험 수준 |
|---|---|---|---|
| **활성 스레드** | `jvm_threads_live_threads` | < 200 | > 500 |
| **대기 중인 스레드** | `jvm_threads_states_threads{state="waiting"}` | 적을수록 좋음 | 많음 |
| **블록된 스레드** | `jvm_threads_states_threads{state="blocked"}` | = 0 | > 10 |
| **Executor 대기 작업** | `executor_queued_tasks` | < 100 | > 1000 |

#### **Grafana 쿼리**
```promql
# 스레드 상태별 분포
sum by (state) (jvm_threads_states_threads)

# 블록된 스레드 비율
(jvm_threads_states_threads{state="blocked"} / jvm_threads_live_threads) * 100
```

#### **개선 전략**

##### **Case 1: 스레드 풀 크기 조정**
```yaml
# application.yml
spring:
  task:
    execution:
      pool:
        core-size: 10      # 기본 스레드 수
        max-size: 50       # 최대 스레드 수
        queue-capacity: 100  # 큐 크기
```

##### **Case 2: Lock 경합 해결**
```java
// 잘못된 코드 (Lock 경합)
private final Map<String, String> cache = new HashMap<>();

public synchronized String get(String key) {  // 모든 스레드가 대기!
    return cache.get(key);
}

// 개선된 코드 (동시성 컬렉션)
private final Map<String, String> cache = new ConcurrentHashMap<>();

public String get(String key) {  // Lock 없음!
    return cache.get(key);
}
```

---

## 상황별 성능 개선 전략

### 📊 **시나리오 1: API 응답이 느림 (P95 > 1초)**

#### **1단계: 측정**
```promql
# 느린 엔드포인트 찾기
topk(10,
  rate(http_server_requests_seconds_sum[5m])
  /
  rate(http_server_requests_seconds_count[5m])
)
```

#### **2단계: 원인 파악**
```promql
# DB 커넥션 포화?
hikaricp_connections_active == hikaricp_connections_max

# Repository 느림?
rate(spring_data_repository_invocations_seconds_sum[5m])
/
rate(spring_data_repository_invocations_seconds_count[5m])
```

#### **3단계: 개선**
- **DB 쿼리 최적화**: 인덱스 추가, fetch join
- **캐싱**: Redis 도입
- **비동기 처리**: @Async, CompletableFuture

#### **4단계: 검증**
```bash
# 개선 전 테스트
k6 run --vus 50 --duration 1m scenario.js > before.txt

# 코드 수정

# 개선 후 테스트
k6 run --vus 50 --duration 1m scenario.js > after.txt

# 비교
diff before.txt after.txt
```

---

### 📊 **시나리오 2: 메모리 사용량 계속 증가**

#### **1단계: 측정**
```promql
# 힙 사용률 추이
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# GC 후에도 감소 안 됨?
jvm_memory_usage_after_gc{area="heap"}
```

#### **2단계: Heap Dump 분석**
```bash
# 1. Heap Dump 생성
jmap -dump:live,format=b,file=heap.bin <PID>

# 2. Eclipse MAT로 분석
# → Leak Suspects Report 확인
# → Dominator Tree에서 큰 객체 찾기
```

#### **3단계: 개선**
```java
// 주요 원인 1: 무한 증가하는 컬렉션
private List<String> logs = new ArrayList<>();  // ❌

// 해결: 크기 제한
private final Queue<String> logs = new ArrayDeque<>(1000);  // ✅
if (logs.size() >= 1000) {
    logs.poll();
}

// 주요 원인 2: 캐시 크기 제한 없음
private Map<String, String> cache = new HashMap<>();  // ❌

// 해결: LRU 캐시
@Cacheable(value = "myCache", key = "#key")  // ✅
```

---

### 📊 **시나리오 3: DB 커넥션 부족**

#### **1단계: 측정**
```promql
# 커넥션 포화?
hikaricp_connections_active >= 10

# 대기 시간?
rate(hikaricp_connections_acquire_seconds_sum[1m])
/
rate(hikaricp_connections_acquire_seconds_count[1m])
```

#### **2단계: 원인 파악**
```sql
-- 느린 쿼리 찾기
SELECT
  query,
  calls,
  mean_time,
  max_time
FROM pg_stat_statements
WHERE mean_time > 100  -- 100ms 이상
ORDER BY mean_time DESC
LIMIT 10;
```

#### **3단계: 개선**

**옵션 1: 커넥션 풀 증가**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 10 → 20
```

**옵션 2: 쿼리 최적화**
```sql
-- EXPLAIN ANALYZE로 분석
EXPLAIN ANALYZE
SELECT * FROM cards WHERE user_id = 123;

-- 인덱스 추가
CREATE INDEX idx_cards_user_id ON cards(user_id);
```

**옵션 3: N+1 쿼리 해결**
```java
// Before: N+1 쿼리
@Query("SELECT c FROM Card c WHERE c.userId = :userId")
List<Card> findByUserId(Long userId);

// After: fetch join
@Query("SELECT c FROM Card c JOIN FETCH c.keyword WHERE c.userId = :userId")
List<Card> findByUserIdWithKeyword(Long userId);
```

---

## 실전 예시

### 🎯 **IMYME 프로젝트 실전 최적화**

#### **문제: Solo 피드백 저장 시 LazyInitializationException**

**측정:**
```promql
# 에러 로그 확인
increase(logback_events_total{level="error"}[5m])
```

**원인:**
- Virtual Thread에서 JPA 엔티티 접근
- Session이 닫혀서 Lazy Loading 실패

**해결:**
```java
// Before
CardAttempt attempt = attemptRepository.findById(attemptId)
    .orElseThrow();
attempt.getCard().getUser();  // LazyInitializationException!

// After
@Query("""
    SELECT ca FROM CardAttempt ca
    JOIN FETCH ca.card c
    JOIN FETCH c.user
    WHERE ca.id = :attemptId
    """)
Optional<CardAttempt> findByIdWithCardAndUser(@Param("attemptId") Long attemptId);
```

**검증:**
```bash
# 에러 로그가 0이 되었는지 확인
curl -s "http://localhost:9092/api/v1/query?query=rate(logback_events_total{level='error'}[5m])"
```

---

#### **문제: 카드 목록 조회 느림 (500ms → 50ms)**

**측정:**
```promql
# 엔드포인트별 응답 시간
rate(http_server_requests_seconds_sum{uri="/cards"}[5m])
/
rate(http_server_requests_seconds_count{uri="/cards"}[5m])
```

**원인:**
- N+1 쿼리 (Keyword 조회)
- 인덱스 없음

**해결:**
```java
// fetch join 추가
@Query("""
    SELECT c FROM Card c
    JOIN FETCH c.keyword
    WHERE c.userId = :userId
    """)

// 인덱스 추가
CREATE INDEX idx_cards_user_id_created ON cards(user_id, created_at DESC);
```

**검증:**
```bash
# k6로 부하 테스트
k6 run --vus 50 --duration 1m test-cards.js

# P95 확인
# Before: 500ms
# After: 50ms (10배 개선!)
```

---

## 💡 핵심 정리

### **성능 개선 우선순위**

1. **측정 먼저**: 추측하지 말고 데이터로 확인
2. **병목 파악**: 가장 느린 부분부터 개선
3. **작은 개선**: 한 번에 하나씩 변경
4. **검증**: 개선 효과 측정

### **자주 쓰는 Prometheus 쿼리**

```promql
# 1. 느린 엔드포인트 TOP 10
topk(10,
  rate(http_server_requests_seconds_sum[5m])
  /
  rate(http_server_requests_seconds_count[5m])
)

# 2. 힙 메모리 사용률
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# 3. DB 커넥션 사용률
(hikaricp_connections_active / hikaricp_connections_max) * 100

# 4. GC 시간 비율
(rate(jvm_gc_pause_seconds_sum[1m]) / 60) * 100

# 5. 에러율
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
/
rate(http_server_requests_seconds_count[5m])
```

---

궁금한 점 있으면 물어보세요! 👋