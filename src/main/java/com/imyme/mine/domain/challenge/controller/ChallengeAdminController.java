package com.imyme.mine.domain.challenge.controller;

import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRankingRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeResultRepository;
import com.imyme.mine.domain.challenge.scheduler.ChallengeScheduler;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 챌린지 파이프라인 수동 트리거 API (dev/release 전용)
 *
 * <p>밤 10시 고정 스케줄러를 수동으로 단계별 실행할 수 있어 테스트 편의성 제공.
 * {@code /setup}은 하루에 몇 번이든 호출 가능 — 기존 데이터 초기화 후 SCHEDULED로 재설정.
 *
 * <pre>
 * POST /admin/challenge/setup    — 오늘 날짜 챌린지 생성 (기존 있으면 초기화 후 재사용)
 * POST /admin/challenge/open     — SCHEDULED → OPEN
 * POST /admin/challenge/close    — OPEN → CLOSED
 * POST /admin/challenge/analyze  — CLOSED → ANALYZING + STT MQ 발행
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/admin/challenge")
@Profile({"dev", "release"})
@RequiredArgsConstructor
public class ChallengeAdminController {

    private final ChallengeScheduler challengeScheduler;
    private final ChallengeRepository challengeRepository;
    private final NotificationBroadcastService notificationBroadcastService;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final ChallengeRankingRepository challengeRankingRepository;
    private final ChallengeResultRepository challengeResultRepository;
    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final Random RANDOM = new Random();

    /** 부하테스트용 싱글톤 패턴 STT 텍스트 100개 (4단계 품질 × 25개) */
    private static final String[] SINGLETON_STT_TEXTS = {
        // ── Level 1 우수 (0-24) ──────────────────────────────────────────────
        "싱글톤 패턴은 특정 클래스의 인스턴스가 오직 하나만 생성되도록 보장하는 생성 디자인 패턴입니다 생성자를 private으로 선언하고 정적 메서드를 통해서만 인스턴스에 접근하게 합니다 멀티스레드 환경에서는 synchronized나 double-checked locking 또는 정적 이너 클래스 홀더 방식으로 스레드 안전성을 보장합니다",
        "멀티스레드 환경에서 안전한 싱글톤 구현 방법은 세 가지입니다 첫째 클래스 로딩 시점에 정적 필드로 초기화하는 이른 초기화 둘째 synchronized를 사용한 지연 초기화 셋째 정적 이너 클래스를 활용하는 홀더 패턴입니다 각 방법은 성능과 안전성 사이의 트레이드오프가 있습니다",
        "싱글톤 패턴의 단점으로는 전역 상태로 인한 코드 결합도 증가와 단위 테스트의 어려움이 있습니다 클래스 로더가 여러 개인 환경에서는 인스턴스가 중복 생성될 수 있으며 이를 해결하려면 의존성 주입 프레임워크를 사용하거나 enum 기반 싱글톤을 고려해야 합니다",
        "자바에서 enum을 이용한 싱글톤 구현은 직렬화와 역직렬화 시에도 인스턴스 유일성을 보장하고 리플렉션 API를 통한 새 인스턴스 생성도 막아줍니다 이펙티브 자바에서도 권장하는 방식으로 열거 타입 선언만으로 스레드 안전성까지 자동으로 확보됩니다",
        "싱글톤 패턴은 데이터베이스 연결 풀 로그 설정 애플리케이션 설정값 캐시 등 공유 리소스 관리에 유용합니다 스프링 프레임워크에서는 빈이 기본적으로 싱글톤 스코프로 관리되므로 개발자가 직접 구현할 필요 없이 IoC 컨테이너가 처리해줍니다",
        "double-checked locking은 인스턴스가 null인지 먼저 확인한 후에만 synchronized 블록에 진입하고 블록 내에서 다시 한번 null 확인을 수행합니다 자바 5 이후에는 volatile 키워드를 필드에 선언해야 메모리 가시성이 보장되어 올바르게 동작합니다",
        "싱글톤과 정적 클래스의 차이는 싱글톤이 인터페이스 구현과 상속이 가능하여 다형성을 지원하지만 정적 클래스는 불가능하다는 점입니다 싱글톤은 지연 초기화가 가능하고 생성 인자를 받는 로직을 포함할 수 있어 더 유연합니다",
        "클래스 로더 문제로 인한 싱글톤 파괴를 방지하려면 같은 JVM 내에서 여러 클래스 로더가 각각 클래스를 로딩하면 별도 인스턴스가 생성될 수 있음을 알아야 합니다 이를 방지하려면 클래스 로더를 명시적으로 지정하거나 레지스트리 패턴을 함께 사용합니다",
        "초기화 온 디맨드 홀더 패턴은 외부 클래스 로딩 시 이너 정적 클래스는 로딩되지 않다가 getInstance 메서드가 처음 호출될 때 이너 클래스가 로딩되면서 정적 필드 초기화가 이루어집니다 지연 초기화와 스레드 안전성을 synchronized 없이 동시에 확보합니다",
        "싱글톤 패턴의 테스트 용이성 문제 해결 방법으로는 인터페이스 도입과 의존성 주입 그리고 리플렉션을 활용한 인스턴스 교체가 있습니다 근본적인 해결책은 스프링의 IoC 컨테이너처럼 수명 주기 관리를 프레임워크에 위임하는 것입니다",
        "직렬화 가능한 싱글톤 클래스에는 readResolve 메서드를 구현해야 합니다 역직렬화 과정에서 새로운 인스턴스가 생성되는 것을 막고 기존 싱글톤 인스턴스를 반환해야 합니다 이 메서드가 없으면 역직렬화 이후 두 개의 인스턴스가 존재하게 됩니다",
        "싱글톤 패턴 적용 시 해당 인스턴스가 정말로 시스템 전역에서 단 하나여야 하는지를 신중히 고려해야 합니다 단순히 객체 생성 비용을 줄이기 위해 싱글톤을 남용하면 코드 결합도가 높아지고 테스트가 어려워집니다 상황에 따라 플라이웨이트 패턴이나 의존성 주입이 더 적합할 수 있습니다",
        "분산 시스템에서 싱글톤 패턴은 서버가 여러 대인 경우 각 서버마다 별도의 인스턴스가 생성되므로 진정한 의미의 싱글톤이 보장되지 않습니다 분산 환경에서 단일 인스턴스가 필요하다면 중앙화된 상태 저장소나 분산 락을 활용해야 합니다",
        "싱글톤 패턴은 GoF의 23가지 디자인 패턴 중 생성 패턴에 속합니다 팩토리 메서드나 추상 팩토리 패턴과 함께 객체 생성 메커니즘을 추상화한다는 점에서 자주 언급되지만 안티패턴으로 분류되는 경우도 많아 신중하게 사용해야 합니다",
        "정적 이너 클래스를 활용한 싱글톤 구현의 장점은 클래스 로딩 메커니즘을 이용해 스레드 안전성을 별도의 동기화 없이도 보장한다는 점입니다 이너 클래스는 처음 접근할 때만 JVM이 로딩하고 인스턴스를 생성하므로 지연 초기화 효과도 얻습니다",
        "싱글톤의 핵심 구현 요소는 세 가지입니다 private 생성자로 외부 직접 인스턴스 생성 차단 static 변수로 유일한 인스턴스 보관 그리고 public static 메서드로 해당 인스턴스에 접근하는 통로를 제공하는 것입니다",
        "안드로이드에서 싱글톤 패턴은 데이터베이스 헬퍼 레트로핏 인스턴스 공유 프리퍼런스 매니저 등을 관리할 때 자주 쓰입니다 컨텍스트 누수를 방지하려면 액티비티 컨텍스트 대신 애플리케이션 컨텍스트를 사용해야 합니다",
        "싱글톤 패턴의 lazy initialization이란 인스턴스가 실제로 필요한 시점까지 생성을 늦추는 기법입니다 메모리와 시작 시간을 절약할 수 있으나 스레드 안전성 문제가 발생할 수 있습니다 반면 eager initialization은 클래스 로딩 시점에 즉시 생성하므로 스레드 안전합니다",
        "싱글톤을 구현할 때 리플렉션 공격을 방어하려면 생성자에서 이미 인스턴스가 존재하는 경우 예외를 던지도록 해야 합니다 그렇지 않으면 setAccessible을 통해 새 인스턴스를 강제로 만들 수 있습니다 enum 방식은 이런 공격에 자연스럽게 면역입니다",
        "싱글톤과 모노스테이트 패턴의 차이는 싱글톤은 인스턴스가 하나만 존재하지만 모노스테이트는 여러 인스턴스가 존재하되 모든 상태를 정적 필드를 통해 공유한다는 점입니다 외부에서 보기에는 싱글톤처럼 동작합니다",
        "스프링에서는 빈의 스코프로 싱글톤 여부를 제어합니다 기본 스코프인 싱글톤 외에도 프로토타입 리퀘스트 세션 등이 있습니다 싱글톤 스코프 빈은 컨테이너당 하나의 인스턴스를 공유하므로 상태를 갖는 빈은 싱글톤으로 사용하지 않는 것이 좋습니다",
        "volatile 키워드는 변수의 값을 항상 메인 메모리에서 읽고 쓰도록 강제합니다 double-checked locking에서 volatile 없이는 컴파일러 최적화나 CPU 재정렬로 인해 완전히 초기화되지 않은 객체의 참조가 다른 스레드에 노출될 수 있습니다",
        "오브젝트 풀링과 싱글톤의 관계를 보면 싱글톤이 단 하나의 인스턴스만 허용하는 반면 풀링은 제한된 수의 인스턴스를 재사용합니다 데이터베이스 커넥션 풀은 여러 커넥션을 관리하므로 풀 자체를 싱글톤으로 관리하는 형태가 일반적입니다",
        "싱글톤 패턴의 장점은 전역 상태 관리의 일관성 보장 객체 생성 비용 절감 공유 리소스에 대한 단일 접근점 제공 그리고 전역 변수의 단점을 제거하면서도 전역 접근의 편리함을 유지하는 것입니다",
        "프로덕션 코드에서 싱글톤을 직접 구현하기보다는 스프링의 IoC 컨테이너나 구글 주스 같은 의존성 주입 프레임워크에 수명 주기 관리를 위임하는 것이 권장됩니다 이렇게 하면 싱글톤의 장점을 누리면서도 테스트 용이성과 모듈성을 유지할 수 있습니다",
        // ── Level 2 양호 (25-49) ─────────────────────────────────────────────
        "싱글톤 패턴은 클래스의 인스턴스를 하나만 만들어서 전역적으로 사용하는 디자인 패턴입니다 생성자를 private으로 만들고 정적 메서드를 통해서만 인스턴스를 가져올 수 있게 합니다 스레드 환경에서는 synchronized를 사용해서 동시에 여러 인스턴스가 생성되지 않도록 해야 합니다",
        "싱글톤 구현에는 이른 초기화와 늦은 초기화가 있습니다 이른 초기화는 클래스가 로딩될 때 바로 인스턴스를 만들고 늦은 초기화는 처음 필요할 때 만듭니다 멀티스레드 환경에서 안전하게 쓰려면 synchronized를 쓰거나 volatile과 함께 double-checked locking을 사용합니다",
        "싱글톤 패턴의 장점은 메모리를 절약하고 객체 생성 비용이 비싼 경우에 효과적이라는 점입니다 데이터베이스 연결이나 스레드 풀 같은 경우에 많이 쓰입니다 단점은 전역 상태를 사용하기 때문에 테스트하기 어렵고 의존성이 숨겨진다는 점입니다",
        "자바에서 enum으로 싱글톤을 만들면 직렬화 문제도 해결되고 리플렉션으로 인한 문제도 없어서 가장 안전한 방법으로 알려져 있습니다 단순하게 enum 타입 하나만 선언하면 JVM이 알아서 하나의 인스턴스만 보장해줍니다",
        "싱글톤과 정적 클래스의 차이는 싱글톤은 인터페이스를 구현하거나 상속이 가능하지만 정적 클래스는 불가능하다는 점입니다 싱글톤은 지연 초기화가 가능하고 객체로서의 장점을 갖지만 정적 클래스는 그렇지 않습니다",
        "스프링 프레임워크에서는 기본적으로 빈이 싱글톤으로 관리됩니다 같은 타입의 빈을 여러 번 요청해도 같은 인스턴스를 반환합니다 이를 통해 싱글톤의 장점을 누리면서도 직접 구현하지 않아도 됩니다",
        "싱글톤 패턴 구현 시 가장 흔한 실수는 멀티스레드 환경에서 synchronized를 빠뜨리는 것입니다 두 스레드가 동시에 null을 확인하고 각각 인스턴스를 생성하는 경합 조건이 발생할 수 있습니다 이를 방지하기 위해 동기화 처리가 필요합니다",
        "싱글톤의 일반적인 코드 구조는 private static 변수로 인스턴스를 저장하고 private 생성자를 통해 외부 생성을 막으며 public static getInstance 메서드로 인스턴스를 반환하는 형태입니다",
        "싱글톤을 안티패턴이라고 부르는 이유는 전역 상태가 코드의 예측 가능성을 낮추고 단위 테스트를 어렵게 만들기 때문입니다 하지만 적절한 사용 사례에서는 여전히 유용한 패턴입니다",
        "클래스 홀더를 이용한 싱글톤은 정적 이너 클래스가 처음 사용될 때만 로딩되는 JVM 특성을 이용합니다 synchronized 없이도 스레드 안전성과 지연 초기화를 동시에 얻을 수 있어서 자바에서 권장되는 방법 중 하나입니다",
        "싱글톤에서 직렬화를 지원하려면 readResolve 메서드를 반드시 구현해야 합니다 그렇지 않으면 역직렬화할 때 새로운 인스턴스가 만들어져서 싱글톤이 깨질 수 있습니다",
        "리플렉션을 통해 싱글톤의 private 생성자를 강제로 호출하면 새로운 인스턴스를 만들 수 있습니다 이를 막으려면 생성자 내부에서 이미 인스턴스가 있으면 예외를 던지도록 해야 합니다",
        "분산 환경에서는 JVM이 여러 개이므로 각 JVM마다 싱글톤 인스턴스가 하나씩 존재합니다 진정한 분산 싱글톤이 되려면 공유 상태 저장소나 분산 락이 필요합니다",
        "싱글톤의 주요 목적은 하나의 인스턴스로 공유 자원을 효율적으로 관리하는 것입니다 로그 파일 설정 파일 캐시 같은 것들을 싱글톤으로 관리하면 여러 곳에서 일관된 상태를 유지할 수 있습니다",
        "static 블록을 이용한 초기화도 스레드 안전한 싱글톤 구현 방법입니다 static 초기화 블록은 클래스가 로딩될 때 한 번만 실행되고 JVM이 스레드 안전성을 보장합니다",
        "싱글톤의 테스트 격리 문제를 해결하기 위해 싱글톤을 추상화하거나 의존성 주입을 사용하는 방법이 있습니다 테스트마다 상태를 초기화해야 하고 목 객체로 교체하기도 어렵다는 것이 단점입니다",
        "double-checked locking은 인스턴스가 null인지 먼저 확인하고 null이면 synchronized 블록에 진입한 뒤 다시 한 번 확인합니다 이미 인스턴스가 존재할 때 불필요한 동기화를 피할 수 있습니다",
        "싱글톤 패턴은 생성 패턴 중 하나로 팩토리 메서드 추상 팩토리 빌더 프로토타입과 함께 객체 생성과 관련된 패턴으로 분류됩니다",
        "싱글톤의 인스턴스는 애플리케이션이 종료될 때까지 존재합니다 이 때문에 상태가 많은 경우 메모리 누수가 발생할 수 있습니다",
        "코틀린에서는 object 키워드를 사용하면 자동으로 싱글톤이 됩니다 자바보다 훨씬 간결하게 싱글톤을 구현할 수 있습니다",
        "싱글톤을 사용할 때는 여러 스레드에서 동시에 상태를 변경하면 경쟁 조건이 발생하므로 불변 객체로 만들거나 스레드 로컬을 사용하는 방법을 고려해야 합니다",
        "멀티톤 패턴은 특정 키에 대해 하나의 인스턴스를 보장하는 싱글톤의 변형으로 지역별로 하나의 설정 인스턴스를 유지해야 할 때 사용할 수 있습니다",
        "싱글톤 패턴의 가장 중요한 것은 생성자를 private으로 선언하는 것입니다 이를 통해 외부에서 new 키워드로 새 인스턴스를 만드는 것을 막을 수 있습니다",
        "GoF 디자인 패턴에서 싱글톤은 가장 단순하면서도 자주 오용되는 패턴 중 하나입니다 올바른 사용 사례를 이해하고 남용하지 않는 것이 중요합니다",
        "싱글톤 패턴을 테스트할 때는 각 테스트가 독립적으로 실행되어야 하므로 인스턴스를 초기화하는 방법이 필요합니다 리플렉션을 사용하거나 테스트용 리셋 메서드를 제공하는 방법이 있습니다",
        // ── Level 3 보통 (50-74) ─────────────────────────────────────────────
        "싱글톤 패턴은 하나의 인스턴스만 만들어서 쓰는 패턴입니다 생성자를 private으로 막고 정적 메서드로 인스턴스를 가져옵니다",
        "싱글톤 패턴은 객체를 한 번만 만들어서 재사용하는 방법입니다 메모리를 절약할 수 있고 설정 정보 같은 것을 관리할 때 씁니다",
        "private 생성자와 정적 메서드를 사용해서 하나의 인스턴스를 보장하는 것이 싱글톤 패턴입니다 전역적으로 접근 가능합니다",
        "싱글톤은 인스턴스를 하나만 생성하도록 보장하는 패턴인데 자바에서는 private 생성자랑 static 변수 static 메서드로 구현합니다",
        "싱글톤 패턴의 가장 큰 특징은 해당 클래스의 인스턴스가 하나만 있다는 것입니다 어디서나 같은 인스턴스에 접근할 수 있어서 공유 데이터를 관리하기 좋습니다",
        "스레드가 여러 개일 때 싱글톤을 쓰면 동시에 여러 인스턴스가 생성될 수 있어서 synchronized를 사용해야 합니다",
        "싱글톤 패턴은 디자인 패턴 중 생성 패턴에 속하며 클래스의 인스턴스 생성을 제어합니다",
        "데이터베이스 연결이나 로그 관리 같은 경우에 싱글톤 패턴을 많이 씁니다 하나의 인스턴스를 공유하기 때문에 효율적입니다",
        "싱글톤은 getInstance라는 메서드를 통해 인스턴스를 반환합니다 처음 호출되면 인스턴스를 생성하고 이후에는 같은 인스턴스를 반환합니다",
        "싱글톤 패턴의 단점은 테스트가 어렵다는 점인데 전역 상태를 공유하기 때문에 테스트 간 영향을 줄 수 있습니다",
        "자바에서 enum으로 싱글톤을 구현하면 안전하다고 알고 있습니다 직렬화나 리플렉션 문제를 자동으로 처리해준다고 합니다",
        "싱글톤은 전역 변수와 비슷하지만 객체지향적인 방법으로 전역 상태를 관리합니다",
        "static 키워드를 사용해서 클래스 레벨의 인스턴스를 관리하는 것이 싱글톤 패턴의 핵심입니다",
        "스프링에서는 따로 싱글톤을 구현하지 않아도 기본적으로 빈이 싱글톤으로 관리되기 때문입니다",
        "싱글톤 패턴을 구현할 때는 스레드 안전성을 고려해야 하고 동기화 처리가 필요할 수 있습니다",
        "인스턴스를 하나만 유지함으로써 메모리를 효율적으로 사용할 수 있는 것이 싱글톤의 장점입니다",
        "싱글톤을 잘못 사용하면 코드의 결합도가 높아지고 유지보수가 어려워질 수 있습니다",
        "기본적인 싱글톤 구현은 private static 필드에 인스턴스를 저장하고 getter 메서드를 통해 접근합니다",
        "멀티스레드 환경에서 싱글톤은 synchronized 블록으로 스레드 안전성을 확보해야 합니다",
        "싱글톤 패턴은 하나의 인스턴스를 공유하므로 상태를 가지는 객체를 싱글톤으로 만들 때는 주의해야 합니다",
        "싱글톤의 인스턴스는 애플리케이션 생명 주기 동안 유지됩니다",
        "GoF 패턴 중에서 싱글톤은 구현이 단순해서 자주 사용되지만 잘못 쓰면 문제가 됩니다",
        "로거나 설정 매니저 같은 경우 싱글톤으로 구현하면 편리하게 전역에서 사용할 수 있습니다",
        "싱글톤 구현의 핵심은 외부에서 인스턴스를 임의로 만들 수 없게 하는 것입니다",
        "싱글톤 패턴은 객체 생성을 한 번만 하게 제한하는 방법입니다",
        // ── Level 4 미흡 (75-99) ─────────────────────────────────────────────
        "싱글톤은 하나만 만드는 거요 그냥 하나의 인스턴스를 만들어서 계속 쓰는 건데요",
        "싱글톤 패턴이요 음 클래스를 하나만 만든다는 건가요 아니면 객체를 하나만 만드는 건가요 잘 모르겠는데 뭔가 static을 쓰는 것 같습니다",
        "싱글톤 패턴은 그냥 전역 변수 같은 거 아닌가요 어디서나 접근할 수 있고 하나만 있는 것 같습니다",
        "싱글톤 패턴이요 음 아마도 인스턴스를 여러 개 만들 수 없는 패턴인 것 같은데요 왜 쓰는지는 잘 모르겠습니다",
        "싱글톤은 객체를 하나만 만드는 건데 자바에서는 static을 쓰면 되는 것 같습니다 정확한 구현 방법은 잘 모르겠어요",
        "싱글톤 패턴이요 들어본 것 같은데 정확히는 모르겠습니다 디자인 패턴 중 하나인 것은 알고 있습니다",
        "싱글톤 패턴은 같은 클래스의 인스턴스를 계속 새로 만들지 않고 처음에 만든 것을 재사용하는 방식입니다 근데 왜 하는지는 잘 이해가 안 됩니다",
        "아마도 싱글톤은 클래스당 하나의 객체만 허용하는 것 같습니다 구현 방법은 잘 모르겠고 어디서 쓰는지도 잘 모르겠습니다",
        "싱글톤이 뭔가요 처음 들어봤는데요 혹시 단순히 하나만 있는 패턴을 말하는 건가요",
        "싱글톤 패턴은 어떤 클래스에서 오직 하나의 인스턴스만 사용하는 것입니다 생성자가 public인지 private인지 헷갈립니다",
        "싱글톤이요 static 변수로 뭔가를 하는 것 같은데요 구체적으로는 잘 기억이 안 납니다",
        "싱글톤은 패턴이름이 하나라는 뜻이니까 뭔가 하나만 쓴다는 의미인 것 같습니다 정확한 내용은 공부를 더 해야 할 것 같습니다",
        "싱글톤 패턴이요 객체를 재활용하는 것 같은데 그냥 new로 만들면 안 되는 건가요 잘 이해가 안 됩니다",
        "싱글톤은 전역으로 사용하는 객체 같은 건데요 어떻게 구현하는지는 정확히 모르겠습니다",
        "뭔가 하나의 인스턴스만 있어야 하는 상황에서 쓰는 것 같습니다 Thread safe인지는 모르겠는데요",
        "싱글톤이요 클래스 하나에 객체 하나요 아니면 객체를 하나만 만들 수 있게 제한하는 건가요",
        "싱글톤 패턴은 인스턴스를 여러 번 생성하지 않아서 메모리를 아낀다는 것 같은데요 더 이상은 잘 모르겠습니다",
        "싱글톤이요 음 스레드 어쩌구 하는 것 같은데 잘 모르겠습니다 아마 동기화랑 관련이 있는 것 같기도 합니다",
        "싱글톤은 팩토리 패턴이랑 비슷한 것 같은데요 객체를 만드는 방법 중 하나인 것은 맞는 것 같습니다",
        "싱글톤 패턴이요 잘 몰라서 죄송한데요 그냥 하나의 클래스에서 하나의 인스턴스만 만드는 거 아닌가요",
        "싱글톤은 뭔가 제한이 있는 패턴인 것 같습니다 어떤 제한인지는 정확히 모르겠지만요",
        "싱글톤 패턴이요 공부할 때 봤는데 기억이 잘 안 납니다 아마 전역 변수랑 비슷하게 쓰는 것 같습니다",
        "싱글톤은 객체를 단 하나만 만들게 강제하는 패턴입니다 그 이상은 모르겠습니다",
        "싱글톤 패턴이요 이름만 들어봤고 실제로 구현해본 적이 없어서 잘 모르겠습니다",
        "싱글톤이요 그냥 객체 하나 있으면 되는 것 아닌가요 왜 패턴이 필요한지 모르겠습니다",
    };

    /**
     * 오늘 날짜 챌린지 초기화 + SCHEDULED 상태 준비
     *
     * <ul>
     *   <li>오늘 챌린지 없음 → 새로 생성</li>
     *   <li>오늘 챌린지 있음 → ChallengeResult / ChallengeRanking / ChallengeAttempt 전부 삭제,
     *       Redis 정리 후 Challenge 상태를 SCHEDULED로 리셋</li>
     * </ul>
     * 하루에 몇 번이든 호출해서 파이프라인을 처음부터 다시 테스트 가능.
     */
    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<String> setup() {
        LocalDate today = LocalDate.now();

        Challenge existing = challengeRepository.findByChallengeDate(today).orElse(null);

        if (existing != null) {
            Long challengeId = existing.getId();

            // 1. 자식 데이터 삭제 (FK 순서 주의: result → ranking → attempt)
            challengeResultRepository.deleteByChallengeId(challengeId);
            challengeRankingRepository.deleteByChallengeId(challengeId);
            challengeAttemptRepository.deleteByChallengeId(challengeId);

            // 2. Redis 정리
            cleanupRedis(challengeId);

            // 3. Challenge 상태 초기화 (startAt/endAt도 원래 시간으로 복구)
            challengeRepository.resetToScheduled(
                    challengeId,
                    today.atTime(22, 0),
                    today.atTime(22, 9, 59)
            );

            log.info("[Admin] 챌린지 초기화 완료: id={}, date={}", challengeId, today);
            return ResponseEntity.ok("챌린지 초기화 완료 (SCHEDULED 리셋): " + existing.getKeywordText());
        }

        // 새로 생성
        List<Keyword> keywords = keywordRepository.findAllWithCategoryByIsActive(true);
        if (keywords.isEmpty()) {
            return ResponseEntity.badRequest().body("활성 키워드 없음");
        }

        Keyword keyword = keywords.get(RANDOM.nextInt(keywords.size()));
        LocalDateTime now = LocalDateTime.now();

        Challenge challenge = Challenge.builder()
                .keyword(keyword)
                .keywordText(keyword.getName())
                .challengeDate(today)
                .startAt(now)
                .endAt(now.plusMinutes(10))
                .status(ChallengeStatus.SCHEDULED)
                .build();

        challengeRepository.save(challenge);
        log.info("[Admin] 오늘 챌린지 생성: date={}, keyword={}", today, keyword.getName());
        return ResponseEntity.ok("챌린지 생성 완료 - 키워드: " + keyword.getName());
    }

    /**
     * SCHEDULED → OPEN
     *
     * <p>테스트 편의를 위해 startAt/endAt을 호출 시점 기준으로 갱신.
     * (스케줄러 생성분은 22:09:59 고정이라 프론트 녹음 타이머가 즉시 만료됨)
     */
    @PostMapping("/open")
    @Transactional
    public ResponseEntity<String> open() {
        LocalDateTime now = LocalDateTime.now();
        challengeRepository
                .findByChallengeDateAndStatus(LocalDate.now(), ChallengeStatus.SCHEDULED)
                .ifPresentOrElse(
                        challenge -> {
                            challenge.openForTest(now, now.plusMinutes(10));

                            Long challengeId = challenge.getId();
                            String keywordText = challenge.getKeywordText();

                            TransactionSynchronizationManager.registerSynchronization(
                                    new TransactionSynchronization() {
                                        @Override
                                        public void afterCommit() {
                                            notificationBroadcastService.broadcastToAllActive(
                                                    NotificationType.CHALLENGE_OPEN,
                                                    "오늘의 챌린지가 시작됐어요!",
                                                    "\"" + keywordText + "\" 주제로 지금 도전해보세요.",
                                                    challengeId,
                                                    "CHALLENGE"
                                            );
                                            log.info("[Admin] CHALLENGE_OPEN 브로드캐스트 완료: challengeId={}", challengeId);
                                        }
                                    }
                            );
                        },
                        () -> log.warn("[Admin] OPEN 대상 챌린지 없음")
                );
        return ResponseEntity.ok("open 완료");
    }

    /** OPEN → CLOSED */
    @PostMapping("/close")
    public ResponseEntity<String> close() {
        challengeScheduler.closeChallenge();
        return ResponseEntity.ok("close 완료");
    }

    /** CLOSED → ANALYZING + STT MQ 발행 */
    @PostMapping("/analyze")
    public ResponseEntity<String> analyze() {
        challengeScheduler.startAnalyzing();
        return ResponseEntity.ok("analyze 완료");
    }

    // -------------------------------------------------------------------------
    // 부하테스트 시드
    // -------------------------------------------------------------------------

    /**
     * 더미 참여자 N명을 DB + Redis에 직접 심어 STT 파이프라인을 우회하는 부하테스트 시드
     *
     * <ul>
     *   <li>키워드를 변경하려면 {@code keywordId} 파라미터를 전달</li>
     *   <li>loadtest_1 ~ loadtest_N 더미 유저 upsert</li>
     *   <li>각 유저의 ChallengeAttempt(PROCESSING + sttText) 생성 또는 갱신</li>
     *   <li>Redis: {@code challenge:{id}:participants} Hash 일괄 설정</li>
     *   <li>Redis: {@code active_stt_count = 0}, {@code submitted_count = N}</li>
     * </ul>
     *
     * <p>시드 후 {@code /admin/challenge/close} → {@code /admin/challenge/analyze} 순으로 호출하면
     * initRanking이 즉시 트리거된다.
     */
    @PostMapping("/load-test/seed")
    @Transactional
    public ResponseEntity<String> seedLoadTest(
            @RequestParam Long challengeId,
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(required = false) Long keywordId
    ) {
        Challenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null) {
            return ResponseEntity.badRequest().body("챌린지 없음: " + challengeId);
        }

        // 1. 키워드 변경 (optional)
        if (keywordId != null) {
            Keyword keyword = keywordRepository.findById(keywordId).orElse(null);
            if (keyword == null) {
                return ResponseEntity.badRequest().body("키워드 없음: " + keywordId);
            }
            challenge.changeKeyword(keyword);
            log.info("[Admin] 키워드 변경: challengeId={}, keyword={}", challengeId, keyword.getName());
        }

        // 2. 더미 유저 + ChallengeAttempt 생성
        int actualCount = Math.min(count, SINGLETON_STT_TEXTS.length);
        List<Long> userIds = new ArrayList<>(actualCount);
        List<Long> attemptIds = new ArrayList<>(actualCount);

        for (int i = 1; i <= actualCount; i++) {
            String oauthId = "e2e_load_test_user_" + i;
            final int idx = i;
            User user = userRepository.findByOauthIdAndOauthProvider(oauthId, OAuthProviderType.KAKAO)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .oauthId(oauthId)
                            .oauthProvider(OAuthProviderType.KAKAO)
                            .nickname("부하테스터" + idx)
                            .build()));
            userIds.add(user.getId());

            String sttText = SINGLETON_STT_TEXTS[i - 1];
            ChallengeAttempt attempt = challengeAttemptRepository
                    .findByChallengeIdAndUserId(challengeId, user.getId())
                    .orElseGet(() -> challengeAttemptRepository.save(ChallengeAttempt.builder()
                            .challenge(challenge)
                            .user(user)
                            .status(ChallengeAttemptStatus.PROCESSING)
                            .submittedAt(LocalDateTime.now().minusSeconds(actualCount - idx))
                            .build()));

            attempt.startProcessing();
            attempt.saveSttText(sttText);
            attemptIds.add(attempt.getId());
        }

        // 3. DB 플러시 — attempt ID 확보
        challengeAttemptRepository.flush();

        // 4. Redis participants Hash 설정
        String participantsKey = "challenge:" + challengeId + ":participants";
        for (int i = 0; i < actualCount; i++) {
            String json = "{\"userId\":" + userIds.get(i)
                    + ",\"sttText\":" + toJsonString(SINGLETON_STT_TEXTS[i]) + "}";
            stringRedisTemplate.opsForHash().put(participantsKey, String.valueOf(attemptIds.get(i)), json);
        }
        stringRedisTemplate.expire(participantsKey, Duration.ofHours(4));

        // 5. active_stt_count = 0 (STT 모두 완료 시뮬레이션)
        stringRedisTemplate.opsForValue().set(
                "challenge:" + challengeId + ":active_stt_count", "0", Duration.ofHours(4));

        // 6. submitted_count = actualCount
        stringRedisTemplate.opsForValue().set(
                "challenge:" + challengeId + ":submitted_count",
                String.valueOf(actualCount), Duration.ofHours(4));

        log.info("[Admin] 부하테스트 시드 완료: challengeId={}, count={}", challengeId, actualCount);
        return ResponseEntity.ok("시드 완료 — 참여자: " + actualCount + "명 (challengeId=" + challengeId + ")");
    }

    /** sttText를 JSON 문자열 리터럴로 변환 (큰따옴표 이스케이프) */
    private static String toJsonString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void cleanupRedis(Long challengeId) {
        // challenge:{id}:* — 현재/미래 모든 챌린지 키 일괄 삭제
        Set<String> challengeKeys = stringRedisTemplate.keys("challenge:" + challengeId + ":*");
        if (challengeKeys != null && !challengeKeys.isEmpty()) {
            stringRedisTemplate.delete(challengeKeys);
        }
        // pairs:job:{id}:* — 토너먼트 노드 키 삭제
        Set<String> pairsKeys = stringRedisTemplate.keys("pairs:job:" + challengeId + ":*");
        if (pairsKeys != null && !pairsKeys.isEmpty()) {
            stringRedisTemplate.delete(pairsKeys);
        }
        // 스케줄러 락 키 삭제 — setup 후 재테스트 시 close/open 스킵 방지
        stringRedisTemplate.delete("scheduler:challenge:open:" + LocalDate.now() + ":lock");
        stringRedisTemplate.delete("scheduler:challenge:close:" + LocalDate.now() + ":lock");
    }
}