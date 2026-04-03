# 백엔드 개발 가이드 (aoc-se-be)

## 개발자 배경

- Java 경험 있음, **Kotlin은 처음**
- PostgreSQL 사용 경험 없음 (로컬 미세팅)
- 로컬 DB는 Docker로 실행 (README 참고)

---

## 기술 스택

- **언어**: Kotlin
- **프레임워크**: Spring Boot 3.x
- **ORM**: Spring Data JPA + Hibernate
- **DB**: PostgreSQL 15
- **인증**: AWS Cognito (OAuth 2.0)
- **빌드**: Gradle (Kotlin DSL)

---

## Kotlin 작성 원칙

Java 경험자 기준으로 아래 규칙을 따름.

```kotlin
// 1. data class로 DTO 작성 (Java record와 유사)
data class MemberResponse(
    val id: Long,
    val name: String,
    val email: String
)

// 2. val/var 구분
val name: String = "고정값"   // Java final
var count: Int = 0            // Java 일반 변수

// 3. null 처리 명시
val email: String?            // null 가능
val name: String              // null 불가 (기본)

// 4. when = Java switch
when (role) {
    Role.MARKETER -> "마케터 메뉴"
    Role.OPERATOR -> "운영자 메뉴"
    else -> throw IllegalArgumentException()
}
```

모르는 Kotlin 문법은 Claude에게 "Java로 치면 어떤 코드야?" 라고 물어볼 것.

---

## 레포지토리 구조

```
aoc-se-be/
├── src/main/kotlin/
│   └── com/aoc/
│       ├── member/
│       │   ├── domain/
│       │   │   ├── Member.kt               # 엔티티
│       │   │   └── MemberRepository.kt
│       │   ├── application/
│       │   │   └── MemberService.kt        # 비즈니스 로직
│       │   ├── presentation/
│       │   │   ├── MemberController.kt
│       │   │   └── dto/
│       │   └── infra/
│       │       └── CognitoClient.kt
│       ├── auth/
│       │   ├── JwtProvider.kt
│       │   ├── ShadowJwtProvider.kt
│       │   └── ActorContext.kt             # ThreadLocal 요청 컨텍스트
│       ├── history/
│       │   ├── HistoryEntityListener.kt    # EntityListener
│       │   ├── HistoryEventHandler.kt      # @TransactionalEventListener
│       │   └── History.kt                 # 히스토리 엔티티
│       ├── notification/
│       │   └── NotificationSetting.kt
│       ├── common/
│       │   ├── BaseEntity.kt               # 모든 엔티티 상속
│       │   └── SpringApplicationContext.kt # 이벤트 발행 헬퍼
│       └── config/
│           ├── SecurityConfig.kt
│           └── JpaConfig.kt
├── src/main/resources/
│   └── application.yml
├── build.gradle.kts
├── CLAUDE.md                               # 이 파일
└── README.md
```

---

## BaseEntity 구조

모든 엔티티는 반드시 `BaseEntity`를 상속해야 히스토리가 자동 기록됨.

```kotlin
@MappedSuperclass
@EntityListeners(HistoryEntityListener::class)
abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    val createdAt: LocalDateTime = LocalDateTime.now()
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @Transient  // DB 컬럼 아님 — 변경 전 값 보관용
    var snapshot: String? = null
        private set

    @PostLoad
    fun takeSnapshot() {
        snapshot = jacksonObjectMapper().writeValueAsString(this)
    }
}
```

---

## 히스토리 자동 기록 구조

비즈니스 코드에 히스토리 저장 코드를 작성하지 않아도 자동으로 기록됨.

```
save() 호출
    → EntityListener (@PrePersist / @PreUpdate / @PreRemove)
    → 이벤트 발행 (ApplicationEventPublisher)
    → 원본 트랜잭션 커밋
    → @TransactionalEventListener(AFTER_COMMIT)
    → 별도 트랜잭션(REQUIRES_NEW)으로 history 테이블 저장
```

핵심 원칙:
- 히스토리 저장 실패가 원본 트랜잭션에 영향 없어야 함
- `HistoryRepository.save()`를 서비스에서 직접 호출하지 않음

---

## Shadow JWT

쉐도우 로그인은 Cognito 토큰이 아닌 서버 자체 발급 JWT 사용.

```kotlin
// Shadow JWT 클레임 구조
data class ShadowClaims(
    val userId: String,         // 대상 마케터 ID
    val role: Role,             // 대상 마케터 역할
    val operatorId: String,     // 발급한 운영자 ID
    val isShadow: Boolean = true
)
```

- 유효기간: 30분
- 서명 키: AWS Secrets Manager (`aoc-se-secret-shadow-jwt-key`)
- 만료 시 재발급 없음, 세션 자동 종료

---

## ActorContext (ThreadLocal)

요청 컨텍스트에서 행위자 정보를 전달하는 구조.
Filter에서 세팅 → EntityListener에서 꺼내 사용.

```kotlin
object ActorContext {
    private val holder = ThreadLocal<ActorInfo>()

    fun set(actorId: String, operatorId: String?, isShadow: Boolean) {
        holder.set(ActorInfo(actorId, operatorId, isShadow))
    }

    fun get(): ActorInfo? = holder.get()

    fun clear() = holder.remove()  // 반드시 요청 종료 시 호출
}
```

---

## PostgreSQL 로컬 세팅 (Windows)

PowerShell:
```powershell
docker run -d `
  --name aoc-postgres `
  -e POSTGRES_DB=aoc `
  -e POSTGRES_USER=aoc `
  -e POSTGRES_PASSWORD=aoc1234 `
  -p 5432:5432 `
  postgres:15
```

`application.yml` 설정:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aoc
    username: aoc
    password: aoc1234
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
```

PostgreSQL은 MySQL과 다르게 스키마 대소문자를 구분함.
테이블명, 컬럼명은 snake_case 소문자로 통일할 것.

---

## DB 테이블 설계

```sql
-- 회원
CREATE TABLE member (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    provider    VARCHAR(50)  NOT NULL,  -- GOOGLE, META, APPLE 등
    provider_id VARCHAR(255) NOT NULL,  -- 소셜 고유 식별자
    work_email  VARCHAR(255),
    role        VARCHAR(50)  NOT NULL DEFAULT 'MARKETER',
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 알림 설정
CREATE TABLE notification_setting (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT    NOT NULL UNIQUE REFERENCES member(id),
    inquiry_alert   BOOLEAN   NOT NULL DEFAULT true,
    marketing_alert BOOLEAN   NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 변경 이력
CREATE TABLE history (
    id           BIGSERIAL PRIMARY KEY,
    entity_type  VARCHAR(100) NOT NULL,   -- 'Member', 'NotificationSetting'
    entity_id    BIGINT       NOT NULL,
    action       VARCHAR(20)  NOT NULL,   -- CREATE, UPDATE, DELETE
    before_value JSONB,                   -- 변경 전 값
    after_value  JSONB,                   -- 변경 후 값
    actor_id     BIGINT       NOT NULL,   -- 실제 행위자
    operator_id  BIGINT,                  -- 쉐도우 로그인 시 운영자 ID
    is_shadow    BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_history_actor   ON history(actor_id);
CREATE INDEX idx_history_entity  ON history(entity_type, entity_id);
CREATE INDEX idx_history_created ON history(created_at);
```

PostgreSQL의 `JSONB` 타입을 before/after 값 저장에 활용할 것.
`JSONB`는 JSON을 인덱싱/쿼리할 수 있어 일반 `TEXT`보다 유리함.

---

## API 응답 형식

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)
```

---

## 권한 어노테이션

```kotlin
// 운영자만 접근
@PreAuthorize("hasRole('OPERATOR')")

// 마케터만 접근
@PreAuthorize("hasRole('MARKETER')")

// 쉐도우 세션 제외
@PreAuthorize("hasRole('MARKETER') and !@actorContext.isShadow()")
```

---

## 백엔드 실행 (Windows)

```powershell
.\gradlew.bat bootRun
```

---

## 주의사항

- Kotlin에서 JPA Entity는 `open class` 또는 allOpen 플러그인 필요
- `data class`를 JPA Entity로 쓰면 equals/hashCode 문제 발생 → 일반 `class` 사용
- `build.gradle.kts`에 `kotlin("plugin.jpa")`와 `kotlin("plugin.spring")` 플러그인 추가 필요
- 연관 레포: `aoc-se-fe` (프론트엔드), `aoc-se-infra` (인프라)
