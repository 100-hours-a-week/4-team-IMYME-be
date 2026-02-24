# Redis 인프라 세팅 가이드

## 📋 개요

Mine 프로젝트에 Redis를 도입하여 PvP 대결의 실시간 상태 동기화를 구현합니다.

## 🚀 빠른 시작

### 1. Docker로 Redis 실행

```bash
# Redis 컨테이너 시작
docker-compose up -d redis

# 로그 확인
docker-compose logs -f redis

# Redis CLI 접속
docker exec -it mine-redis redis-cli

# 상태 확인
docker exec -it mine-redis redis-cli ping
# 응답: PONG
```

### 2. 애플리케이션 실행

```bash
# Spring Boot 실행
./gradlew bootRun

# 또는
./gradlew build && java -jar build/libs/mine-0.0.1-SNAPSHOT.jar
```

## 🏗️ 아키텍처

### Redis 사용 목적

1. **Pub/Sub (발행/구독)** - PvP 방 상태 실시간 알림
2. **Cache** (향후 확장) - 세션, 랭킹 등 캐싱

### 채널 구조

```
pvp:room:{roomId}  # 방별 격리 채널
pvp:global         # 전역 알림 (선택적)
```

### 메시지 타입

- `STATUS_CHANGE` - 방 상태 변경 (OPEN → MATCHED → THINKING → ...)
- `GUEST_JOINED` - 게스트 입장
- `GUEST_LEFT` - 게스트 나가기
- `RECORDING_STARTED` - 녹음 시작
- `SUBMISSION_COMPLETED` - 제출 완료
- `ANALYSIS_COMPLETED` - AI 분석 완료

## 📦 프로젝트 구조

```
src/main/java/com/imyme/mine/
├── global/
│   ├── config/
│   │   └── RedisConfig.java              # Redis 설정 (RedisTemplate, Listener Container)
│   └── messaging/
│       ├── MessagePublisher.java         # 메시지 발행 인터페이스
│       └── RedisMessagePublisher.java    # Redis 발행 구현체
└── domain/pvp/
    └── messaging/
        ├── PvpChannels.java              # 채널 상수 정의
        ├── PvpMessageType.java           # 메시지 타입 Enum
        └── PvpMessage.java               # 메시지 DTO
```

## 🔧 설정 파일

### application.yml

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2
          max-wait: 3000ms
      timeout: 3000ms
```

### .env

```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

## 💻 사용 예시

### 메시지 발행

```java
@Service
@RequiredArgsConstructor
public class PvpRoomService {

    private final MessagePublisher messagePublisher;

    public void notifyStatusChange(Long roomId, PvpRoomStatus status) {
        // 방 상태 변경 메시지 생성
        PvpMessage message = PvpMessage.statusChange(
            roomId,
            status,
            "방 상태가 변경되었습니다."
        );

        // Redis Pub/Sub으로 발행
        String channel = PvpChannels.getRoomChannel(roomId);
        messagePublisher.publish(channel, message);
    }
}
```

### 메시지 구독 (향후 WebSocket 연동)

```java
@Component
@RequiredArgsConstructor
public class PvpMessageSubscriber implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // Redis에서 수신한 메시지를 WebSocket으로 전달
        PvpMessage pvpMessage = deserialize(message.getBody());
        // ... WebSocket으로 클라이언트에게 전송
    }
}
```

## 🧪 테스트

### Redis 연결 테스트

```bash
# Redis CLI에서 직접 테스트
docker exec -it mine-redis redis-cli

# Pub/Sub 테스트
SUBSCRIBE pvp:room:1

# 다른 터미널에서
PUBLISH pvp:room:1 '{"type":"STATUS_CHANGE","roomId":1,"status":"MATCHED"}'
```

### Spring Boot에서 테스트

```java
@SpringBootTest
class RedisMessagePublisherTest {

    @Autowired
    private MessagePublisher messagePublisher;

    @Test
    void testPublish() {
        PvpMessage message = PvpMessage.statusChange(1L, PvpRoomStatus.MATCHED, "테스트");
        messagePublisher.publish("pvp:room:1", message);
    }
}
```

## 🔍 트러블슈팅

### Redis 연결 실패

```bash
# Redis 컨테이너 확인
docker ps | grep redis

# Redis 재시작
docker-compose restart redis

# 로그 확인
docker-compose logs redis
```

### 포트 충돌 (6379)

```bash
# 포트 사용 확인
lsof -i :6379

# docker-compose.yml에서 포트 변경
ports:
  - "6380:6379"  # 호스트:6380 -> 컨테이너:6379
```

## 🚀 다음 단계

- [ ] WebSocket + STOMP 연동
- [ ] PvpMessageSubscriber 구현
- [ ] 클라이언트 실시간 알림 테스트
- [ ] Redis 모니터링 (Redis Commander, RedisInsight)
- [ ] 프로덕션 환경 Redis Cluster 구성

## 📚 참고 자료

- [Spring Data Redis 공식 문서](https://spring.io/projects/spring-data-redis)
- [Redis Pub/Sub 가이드](https://redis.io/docs/manual/pubsub/)
- [Lettuce 공식 문서](https://lettuce.io/)