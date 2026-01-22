# MINE Project Guide (CLAUDE.md)

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 1. 프로젝트 개요 (v1 MVP)
- **서비스명**: MINE (마인)
- **비전**: "말하면서 내 것으로" 만드는 AI 기반 음성 학습 플랫폼
- **핵심 기능**: 음성 학습 카드(Level-up), 실시간 PvP 대결, 일일 챌린지, AI 피드백, 알림, 통계
- **기술 스택**: Java 21 / Spring Boot 3.4.x / PostgreSQL / Redis / AWS S3 / Spring Security(OAuth2)

## 2. 빌드 & 실행 명령어
**이 프로젝트는 엄격한 품질 도구(SpotBugs, PMD, Checkstyle)가 적용되어 있습니다.**

```bash
./gradlew clean build         # PR 전 필수: 품질 도구까지 포함해 전체 빌드
./gradlew bootRun             # Run application
./gradlew test                # Run all tests
./gradlew spotbugsMain        # SpotBugs 정적 분석
./gradlew pmdMain             # PMD 코드 품질 검사
./gradlew checkstyleMain      # Checkstyle 코드 스타일 검사
./gradlew test --tests "com.imyme.mine.SomeTestClass"              # Single test class
./gradlew test --tests "com.imyme.mine.SomeTestClass.methodName"   # Single test method
```

## 3. Architecture

Domain-driven package structure under `com.imyme.mine`:

* **domain/** - 비즈니스 도메인별 패키지

  * `auth/` - OAuth2 인증 (Kakao)
  * `user/` - 사용자, 기기 관리
  * `category/`, `keyword/` - 마스터 데이터
  * `card/`, `attempt/` - 학습 카드 및 시도
  * `storage/` - S3 Presigned URL 관리
  * `device/` - FCM 토큰 관리
  * 각 도메인: `controller/`, `service/`, `dto/`, Entity 클래스
* **global/** - 공통 모듈 (config, common, error, util)

## 4. API Design Principles

### REST 설계 원칙

* URL은 명사(복수형), 계층 구조로 관계 표현: `/cards/{card_id}/attempts`
* `/me` 패턴 사용: `/users/me`, `/pvp/my-rooms`
* HTTP Method: GET(조회), POST(생성), PATCH(부분수정), PUT(상태전환/멱등성), DELETE(삭제)

### 상태 관리 패턴

학습 시도(Attempt) 상태 전이:

```
PENDING → UPLOADED → PROCESSING → COMPLETED/FAILED
```

* PENDING: S3 업로드 대기 (10분 타임아웃)
* UPLOADED: AI 분석 대기
* PROCESSING: STT + AI 채점 중
* COMPLETED/FAILED: 완료/실패

### 업로드 패턴 (Presigned URL)

1. `POST /learning/presigned-url` → S3 업로드 URL 발급
2. 클라이언트 → S3 직접 업로드
3. `PUT .../upload-complete` → 상태 전환 (PENDING → UPLOADED)

### 페이지네이션

* **Cursor 기반** (기본): `/cards`, `/notifications`, `/pvp/rooms` 등
* **Offset 기반**: `/challenges/{id}/rankings` (순위 점프 필요)
* **No Pagination**: `/categories`, `/keywords` (소량 데이터)

### 응답 형식

```json
// 성공: Wrapper 없이 직접 반환
{ "id": 1, "title": "...", "created_at": "..." }

// 에러
{ "error": "ERROR_CODE", "message": "...", "timestamp": "...", "path": "..." }
```

### 공통 HTTP Status

* 200: 성공 (GET, PATCH)
* 201: 생성 성공 (POST)
* 204: 성공, 본문 없음 (DELETE)
* 400: 잘못된 요청
* 401: 인증 실패
* 403: 권한 없음
* 404: 리소스 없음
* 409: 중복/충돌
* 422: 비즈니스 로직 검증 실패

## 5. Code Style

* DTO는 Java record 사용
* 4-space 들여쓰기 (Java/XML/YAML)
* Lombok 사용 (@Data, @Builder 등)
* Soft Delete: `deleted_at` 타임스탬프 사용

## 6. Development & Educational Guidelines (중요!)

이 프로젝트는 **사용자가 코드를 완전히 이해(Deep Understanding)하고, 왜 이렇게 작성했는지 설명할 수 있는 수준**을 최우선 목표로 합니다.
AI는 단순 코더가 아니라 **개발 파트너이자 튜터**로서 행동해야 합니다.

### 6.1 학습용 주석 원칙 (Educational Comments)

* **원칙: 모든 코드에 상세 주석을 포함**합니다.
* "변수 선언"처럼 피상적인 주석이 아니라, 아래 관점을 포함합니다.

  * **왜 이 문법/패턴을 선택했는지(의도)**
  * **Spring 내부에서 어떻게 동작하는지(동작 원리)**
  * **대안(다른 구현 방법) 대비 장단점이 무엇인지(선택 근거)**
* 주석은 가독성을 해치지 않도록 다음을 허용합니다.

  * 반복되는 패턴은 **라인 주석 대신 블록 주석으로 묶어서** 설명 가능
  * 단, "학습에 핵심인 부분(흐름, 상태전이, 트랜잭션, 보안, I/O 경계)"은 가능한 한 상세히 설명

### 6.2 코드 작성 방식 (Chunking Strategy)

* **한 번에 전체 파일을 던지지 말고**, 코드를 **약 10~15줄 단위**로 끊어서 제공합니다.
* 각 청크(Chunk)마다:

  * 해당 청크가 담당하는 역할(책임)
  * 실행 흐름에서의 위치
  * 왜 이렇게 작성했는지(설계 이유)
    를 짧게 요약합니다.
* 사용자가 원하면 "다음 청크로 계속" 방식으로 이어가되, 기본은 청크 단위로 제공합니다.

### 6.3 작성 예시

```java
// @Service: 스프링이 이 클래스를 "비즈니스 로직 담당 빈"으로 등록하게 합니다.
// 컨트롤러/다른 서비스에서 의존성 주입(Injection)으로 재사용 가능해집니다.
@Service
// @RequiredArgsConstructor: final 필드 기반 생성자를 Lombok이 생성합니다.
// 생성자가 1개이면 스프링이 자동으로 주입하므로, @Autowired를 생략할 수 있습니다.
@RequiredArgsConstructor
public class UserService {

    // final: 주입 이후 변경되지 않는 불변 참조를 보장하여 안정성을 높입니다.
    // UserRepository: DB 접근(조회/저장)을 담당하는 계층입니다.
    private final UserRepository userRepository;

    // (여기서 10~15줄 내로 끊고, 다음 로직은 다음 청크에서 진행)
}
```
