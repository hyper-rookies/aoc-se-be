# 백엔드 개발 가이드 (aoc-se-be)

## 개발자 배경

- Java 경험 있음, **Kotlin은 처음**
- PostgreSQL 사용 경험 없음 (로컬은 Docker로 세팅)
- Terraform, Fargate 경험 없음 → CLAUDE_infra 참고

---

## 기술 스택

- **언어**: Kotlin
- **프레임워크**: Spring Boot 3.x
- **ORM**: Spring Data JPA + Hibernate
- **DB**: PostgreSQL 15
- **세션**: Redis 7
- **인증**: AWS Cognito (OAuth 2.0)
- **빌드**: Gradle (Kotlin DSL)
- **배포**: AWS ECS Fargate (eclipse-temurin:17-jre-jammy / Ubuntu 22.04)

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
├── .github/
│   └── workflows/
│       └── be-ci.yml               # CI/CD 파이프라인
├── src/main/kotlin/
│   └── com/aoc/
│       ├── member/
│       │   ├── domain/
│       │   │   ├── Member.kt
│       │   │   └── MemberRepository.kt
│       │   ├── application/
│       │   │   └── MemberService.kt
│       │   ├── presentation/
│       │   │   ├── MemberController.kt
│       │   │   └── dto/
│       │   └── infra/
│       │       └── CognitoClient.kt
│       ├── auth/
│       │   ├── JwtProvider.kt
│       │   ├── ShadowJwtProvider.kt
│       │   └── ActorContext.kt
│       ├── history/
│       │   ├── HistoryEntityListener.kt
│       │   ├── HistoryEventHandler.kt
│       │   └── History.kt
│       ├── notification/
│       │   └── NotificationSetting.kt
│       ├── common/
│       │   ├── BaseEntity.kt
│       │   └── SpringApplicationContext.kt
│       └── config/
│           ├── SecurityConfig.kt
│           └── JpaConfig.kt
├── src/main/resources/
│   ├── application.yml              # 로컬 개발용 (git 포함)
│   ├── application-prod.yml         # 배포용 (git 제외)
│   └── application-prod.yml.example # 형식 공유용 (git 포함)
├── build.gradle.kts
├── Dockerfile
├── CLAUDE.md
└── README.md
```

---

## 환경변수 관리

### 로컬 개발
```yaml
# application.yml (git 포함)
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
  data:
    redis:
      host: localhost
      port: 6379
```

### 배포 환경
```
민감 정보 → AWS Secrets Manager
  aoc-se-secret-shadow-jwt-key   # Shadow JWT 서명 키
  aoc-se-secret-db-password      # DB 비밀번호

비민감 설정 → AWS Parameter Store
  /aoc/se/db/url                 # DB 접속 URL
  /aoc/se/cognito/domain         # Cognito 도메인
  /aoc/se/cognito/client-id      # Cognito 클라이언트 ID
```

```yaml
# application-prod.yml.example (형식 공유용)
spring:
  datasource:
    url: ${DB_URL}          # Parameter Store
    password: ${DB_PASSWORD} # Secrets Manager
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379
```

### .gitignore 추가 필수
```
application-prod.yml
```

---

## CI/CD (be-ci.yml)

```yaml
# PR → 테스트 + 빌드 확인
# main 머지 → ECR push → ECS 재배포

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:           # PR 시 실행
  build-and-push: # main 머지 시 실행
    # 1. bootJar 빌드
    # 2. Docker 이미지 빌드 (eclipse-temurin:17-jre-jammy)
    # 3. ECR push (aoc-se-ecr-chat)
    # 4. ECS 서비스 재배포 (aoc-se-ecs-chat-service)
```

GitHub Repository Secrets 등록 필요:
```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
ECR_REGISTRY
```

---

## Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY build/libs/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

빌드:
```powershell
.\gradlew.bat bootJar
docker build -t aoc-se-ecr-chat .
```

jar 파일명 고정 (`build.gradle.kts`):
```kotlin
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
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

    @Transient
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
data class ShadowClaims(
    val userId: String,
    val role: Role,
    val operatorId: String,
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

## DB 테이블 설계

```sql
-- 회원
CREATE TABLE member (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    provider    VARCHAR(50)  NOT NULL,  -- GOOGLE, META, APPLE
    provider_id VARCHAR(255) NOT NULL,  -- 소셜 고유 식별자
    work_email  VARCHAR(255),
    role        VARCHAR(50)  NOT NULL DEFAULT 'MARKETER'
                    CHECK (role IN ('MARKETER', 'AGENCY_MANAGER', 'OPERATOR')),
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'DELETED')),
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
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    BIGINT       NOT NULL,
    action       VARCHAR(20)  NOT NULL,  -- CREATE, UPDATE, DELETE
    before_value JSONB,
    after_value  JSONB,
    actor_id     BIGINT       NOT NULL,
    operator_id  BIGINT,
    is_shadow    BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_history_actor   ON history(actor_id);
CREATE INDEX idx_history_entity  ON history(entity_type, entity_id);
CREATE INDEX idx_history_created ON history(created_at);
```

PostgreSQL은 MySQL과 다르게 대소문자를 구분함.
테이블명, 컬럼명은 snake_case 소문자로 통일할 것.
before/after 값은 `JSONB` 타입 사용 (인덱싱/쿼리 가능).

`role`, `status`는 PostgreSQL native enum 대신 **VARCHAR + CHECK 제약** 사용.
native enum은 값 추가 시 `ALTER TYPE` 마이그레이션이 필요하고 운영 중 락이 발생할 수 있음.
VARCHAR + CHECK는 CHECK 제약만 수정하면 돼서 확장이 쉬움.

Kotlin 코드에서는 enum으로 타입 안전하게 관리:
```kotlin
enum class Role { MARKETER, AGENCY_MANAGER, OPERATOR }
enum class MemberStatus { ACTIVE, DELETED }

@Entity
class Member : BaseEntity() {
    @Enumerated(EnumType.STRING)  // DB에 문자열로 저장 (ORDINAL 사용 금지 — 순서 변경 시 데이터 오염)
    var role: Role = Role.MARKETER

    @Enumerated(EnumType.STRING)
    var status: MemberStatus = MemberStatus.ACTIVE
}
```

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
@PreAuthorize("hasRole('OPERATOR')")
@PreAuthorize("hasRole('MARKETER')")
@PreAuthorize("hasRole('MARKETER') and !@actorContext.isShadow()")
```

---

## 로컬 실행

```powershell
.\gradlew.bat bootRun
```

---

## 주의사항

- Kotlin JPA Entity는 `open class` 또는 allOpen 플러그인 필요
- `data class`를 JPA Entity로 쓰면 equals/hashCode 문제 발생 → 일반 `class` 사용
- `build.gradle.kts`에 `kotlin("plugin.jpa")`와 `kotlin("plugin.spring")` 플러그인 추가 필요
- `application-prod.yml`은 절대 git에 올리지 않을 것
- 연관 레포: `aoc-se-fe` (프론트엔드), `aoc-se-infra` (인프라)
