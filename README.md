> 말하면서 내 것으로 — AI 음성 기반 CS 학습 플랫폼
> 

---

## 프로젝트 소개

MINE은 사용자가 CS 개념을 **음성으로 직접 설명**하고, AI로부터 즉각적인 피드백을 받는 학습 플랫폼이다.

"설명할 수 있어야 진짜 내 것"이라는 철학을 기반으로, 혼자 학습하는 레벨업 모드부터 실시간 1:1 PvP 대결, 일일 챌린지까지 지원한다.

---

## 기술 스택

| 분류 | 기술 |
| --- | --- |
| Language | Java 21 LTS |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Cache | Redis 7.2 (Lettuce + Connection Pool) |
| Message Queue | RabbitMQ (AmazonMQ) |
| ORM | Spring Data JPA + QueryDSL |
| Migration | Flyway |
| Auth | Kakao OAuth 2.0 + JWT (JJWT) |
| 실시간 | WebSocket + STOMP |
| 알림 | SSE + FCM |
| 파일 | AWS S3 (Presigned URL) |
| 배치 | Spring @Scheduled + ShedLock |
| 테스트 | JUnit5 + Mockito + JaCoCo |
| 인프라 | AWS EC2, RDS, ElastiCache, AmazonMQ |

---

## 아키텍처

```
Client (iOS/Android)
    │
    ├── REST API
    ├── WebSocket (wss://)
    └── SSE
         │
    ┌────▼──────────┐
    │  Main Server  │  Spring Boot (EC2 t3.medium)
    └────┬──────────┘
         │
    ┌────┼─────────────────────────┐
    ▼    ▼                         ▼
PostgreSQL  Redis            RabbitMQ
 (RDS)   (ElastiCache)      (AmazonMQ)
                                   │
                           ┌───────▼───────┐
                           │   AI Server   │
                           │ FastAPI       │
                           │ Whisper/Gemini│
                           └───────────────┘
```

---

## 핵심 기능 및 플로우

### 레벨업 모드 (학습 카드)

```
POST /cards                    → 카드 생성
POST /learning/presigned-url   → S3 업로드 URL 발급
POST /cards/{id}/attempts      → 시도 생성 (PENDING)
S3 직접 업로드
PUT  .../upload-complete        → PENDING → UPLOADED
RabbitMQ: stt.request 발행
AI 서버: STT + Gemini 피드백
SSE 스트리밍으로 결과 전달
```

### PvP 모드

```
POST /pvp/rooms                → 방 생성
WS   PVP_JOIN                  → 실시간 구독
POST /pvp/rooms/{id}/join      → 게스트 입장
WS   PVP_STATUS_CHANGE(MATCHED)→ 양쪽 알림
녹음 → S3 → 제출 완료
RabbitMQ: pvp.stt.request × 2
양쪽 STT 완료 → pvp.feedback.request
WS   PVP_STATUS_CHANGE(FINISHED)→ 결과
```

> 설계 원칙: **"명령은 REST, 알림은 WebSocket"**
> 

### 챌린지 모드 배치 스케줄

```
00:00 챌린지 생성 (키워드 랜덤 선택)
22:00 챌린지 시작 (SCHEDULED → OPEN)
22:10 챌린지 종료 (OPEN → CLOSED)
22:12 AI 분석 시작 (최대 80명 동시)
23:00 결과 발표 (CLOSED → COMPLETED)
02:00 미사용 FCM 토큰 정리
04:00 Soft Delete 물리 삭제
```

---

## 인증/인가

**OAuth 2.0 + JWT + Next.js BFF 패턴**

```
Browser → Next.js BFF → Spring Backend → Kakao OAuth
```

| 토큰 | 형식 | 만료 | 저장 위치 |
| --- | --- | --- | --- |
| Access Token | JWT | 30분 | 클라이언트 메모리 (Zustand) |
| Refresh Token | UUID | 14일 | Next.js HttpOnly Cookie |
- RT는 JS에 노출되지 않음 (XSS 방어)
- RT Rotation으로 탈취 토큰 재사용 방지
- `devices` 테이블로 기기별 세션 관리 / 원격 로그아웃 지원

---

## API 현황 (MVP2 기준)

| 도메인 | 엔드포인트 수 |
| --- | --- |
| 인증/회원 | 11개 |
| 마스터 데이터 | 3개 |
| 학습 카드 | 13개 |
| PvP | 11개 |
| 챌린지 | 6개 |
| 알림/통계 | 4개 |
| **합계** | **48개 (32개 구현 완료)** |

---

## 주요 설계 결정

### 비동기 처리 전략

| 처리 방식 | 대상 |
| --- | --- |
| 동기 | 카드 생성, PvP 매칭, 인증 |
| @Async (Virtual Threads) | MVP: 내부 비동기 |
| RabbitMQ (MVP3~) | AI 분석(STT+LLM), 푸시 알림 |

### 상태 전이 (학습 시도)

```
PENDING → UPLOADED → PROCESSING → COMPLETED
                              └─→ FAILED
```

### 반정규화

쿼리 성능을 위해 `users` 테이블에 통계 데이터 동기화

(`level`, `win_count`, `total_card_count`, `consecutive_days` 등)

### N+1 제거

QueryDSL Fetch Join + 커버링 인덱스 적용

---

## 확장 전략

| 항목 | MVP | Growth (1K명) | Scale (10K명+) |
| --- | --- | --- | --- |
| DB | 단일 인스턴스 | Read Replica | 샤딩 |
| Cache | Caffeine (로컬) | Redis | Redis Cluster |
| 서버 | EC2 1대 | 2대 + ELB | ECS + ASG |
| MQ | RabbitMQ | RabbitMQ | Kafka |
| 아키텍처 | 모놀리식 | 모놀리식 | MSA 검토 |

---

## 관련 문서

- [아키텍처 개요](https://www.notion.so/BE-330b5ab3a8c28126b42ad8a5a85e713f?pvs=21)
- [API 명세서](https://www.notion.so/FE-BE-API-2e3b5ab3a8c280ff892ad354aae2f3ea?pvs=21)
- [테이블 정의서](https://www.notion.so/BE-2dcb5ab3a8c280e1bdb4f41352311690?pvs=21)
- [인증/인가 설계](https://www.notion.so/4-IMYME-MINE-2d1b5ab3a8c2819d9700e640ab4a2400?pvs=21)
- [GitHub BE Repo](https://github.com/100-hours-a-week/4-team-IMYME-be)
