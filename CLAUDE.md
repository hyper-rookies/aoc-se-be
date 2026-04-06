# 백엔드 개발 가이드 (aoc-se-be)

## 개발자 배경

- Java 경험 있음, **Kotlin은 처음**
- PostgreSQL 사용 경험 없음 (로컬은 Docker로 세팅)
- Terraform, Fargate 경험 없음 → CLAUDE_infra 참고

---

## 기술 스택

- **언어**: Kotlin
- **프레임워크**: Spring Boot 3.2.5
- **ORM**: Spring Data JPA + Hibernate
- **DB**: PostgreSQL 15
- **세션**: Redis 7
- **인증**: AWS Cognito (OAuth 2.0), JJWT 0.12.x, Nimbus JOSE JWT 9.37.3
- **빌드**: Gradle (Kotlin DSL)
- **배포**: AWS ECS Fargate (eclipse-temurin:17-jre-jammy / Ubuntu 22.04)
- **이메일**: AWS SES (업무용 이메일 인증 코드 발송)
- **API 문서**: SpringDoc OpenAPI 2.5.0 (Swagger UI)

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
├── src/main/kotlin/com/aoc/
│   ├── AocSeApplication.kt
│   ├── member/
│   │   ├── domain/
│   │   │   ├── Member.kt                   ✅ Role, MemberStatus enum 포함
│   │   │   └── MemberRepository.kt         ✅
│   │   ├── application/
│   │   │   └── MemberService.kt            ✅ loginOrRegister()
│   │   ├── presentation/
│   │   │   ├── MemberController.kt         (미구현)
│   │   │   └── dto/
│   │   └── infra/
│   │       └── CognitoClient.kt            ✅ JWKS 기반 검증
│   ├── auth/
│   │   ├── AuthController.kt               ✅ POST /auth/callback
│   │   ├── JwtProvider.kt                  ✅ HS256, jti 포함
│   │   ├── JwtAuthenticationFilter.kt      ✅ Bearer 추출 → 검증 → 블랙리스트
│   │   ├── ActorContext.kt                 ✅ ThreadLocal, @Component("actorContext")
│   │   ├── CognitoJwtException.kt          ✅
│   │   └── ShadowJwtProvider.kt            TODO (Day 3)
│   ├── history/
│   │   ├── History.kt                      (골격만)
│   │   ├── HistoryEntityListener.kt        (stub — Day 4)
│   │   └── HistoryEventHandler.kt          (미구현 — Day 4)
│   ├── notification/
│   │   ├── NotificationSetting.kt          ✅
│   │   └── NotificationSettingRepository.kt ✅
│   ├── common/
│   │   ├── BaseEntity.kt                   ✅ ULID, snapshot
│   │   ├── ApiResponse.kt                  ✅ ok/error 팩토리, code 필드
│   │   ├── ErrorCode.kt                    ✅ ErrorCode enum
│   │   ├── BusinessException.kt            ✅ + 하위 예외 클래스
│   │   ├── GlobalExceptionHandler.kt       ✅ MethodArgumentNotValidException 포함
│   │   └── SpringApplicationContext.kt     (비어있음 — Day 4에 채울 예정)
│   └── config/
│       ├── SecurityConfig.kt               ✅ STATELESS, CSRF off, JwtFilter 등록
│       ├── CorsConfig.kt                   ✅ 로컬 5173 + CloudFront
│       ├── JpaConfig.kt                    ✅ @EnableJpaAuditing
│       ├── RedisConfig.kt                  ✅ StringRedisSerializer
│       └── SwaggerConfig.kt                ✅ bearerAuth 스킴 등록
├── src/main/resources/
│   ├── application.yml                     공통 설정 (profiles.active=local)
│   ├── application-local.yml               로컬 개발용 (git 제외)
│   ├── application-prod.yml                배포용 환경변수 참조 (git 제외)
│   └── application-prod.yml.example        환경변수 키 목록 문서화 (git 포함)
├── build.gradle.kts
├── Dockerfile
├── CLAUDE.md
├── CONVENTION.md
└── README.md
```

---

## 코딩 컨벤션

→ `CONVENTION.md` 참고

---

## 환경변수 관리

### 로컬 개발
`application-local.yml`에 하드코딩 (git 제외).  
서버 실행 시 `spring.profiles.active=local`이 자동 적용됨.

### 배포 환경
ECS 태스크 정의에서 AWS Secrets Manager / Parameter Store → 환경변수로 주입.  
Spring은 `application-prod.yml`의 `${ENV_VAR}` 형태로 읽음.

```
민감 정보 → AWS Secrets Manager
  aoc-se-secret-shadow-jwt-key   # Shadow JWT 서명 키
  aoc-se-secret-db-password      # DB 비밀번호

비민감 설정 → AWS Parameter Store
  /aoc/se/db/url                 # DB 접속 URL
  /aoc/se/cognito/domain         # Cognito 도메인
  /aoc/se/cognito/client-id      # Cognito 클라이언트 ID
```

---

## CI/CD (be-ci.yml) — Day 5~6 구성 예정

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
    val id: String = UlidCreator.getUlid().toString()  // ULID 자동 생성

    val createdAt: LocalDateTime = LocalDateTime.now()
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @Transient  // DB 컬럼 아님 — 변경 전 값 보관용
    var snapshot: String? = null
        protected set  // allOpen 플러그인과의 호환성으로 protected

    @PostLoad
    fun takeSnapshot() {
        snapshot = jacksonObjectMapper().writeValueAsString(this)
    }
}
```

> ULID는 서버에서 생성하므로 `@GeneratedValue` 불필요.

---

## 히스토리 자동 기록 구조 (TODO — Day 4)

비즈니스 코드에 히스토리 저장 코드를 작성하지 않아도 자동으로 기록됨.

```
save() / update() 호출
    → EntityListener (@PrePersist / @PreUpdate / @PreRemove)
    → 이벤트 발행 (ApplicationEventPublisher)
    → 원본 트랜잭션 커밋
    → @TransactionalEventListener(AFTER_COMMIT)
    → 별도 트랜잭션(REQUIRES_NEW)으로 history 테이블 저장
```

UPDATE 시 before/after 모두 기록:
```
@PreUpdate
  → entity.snapshot (변경 전, @PostLoad 시점에 저장)  → before_value
  → JsonUtils.toJson(entity) (변경 후)                → after_value
```

SpringApplicationContext.kt는 EntityListener에서 Spring Bean(HistoryRepository)을
꺼내기 위해 Day 4에 구현 예정.

---

## 로그인 흐름 및 서버 JWT

```
1. 클라이언트 → Cognito          소셜 로그인 (OAuth)
2. Cognito → 클라이언트          Cognito JWT 발급 (role 없음)
3. 클라이언트 → /auth/callback   Cognito JWT 전달
4. 서버                          Cognito JWT 검증 (JWKS 기반 RS256)
                                 DB에서 멤버 조회 (없으면 신규 생성 + MARKETER 역할 부여)
                                 role 포함한 서버 자체 JWT 발급 (HS256)
                                 Refresh Token → Redis 저장 (4시간 TTL)
5. 서버 → 클라이언트             서버 JWT + Refresh Token 반환
6. 클라이언트                    이후 모든 API 호출에 서버 JWT 사용
```

서버 JWT 클레임 구조:
```kotlin
data class JwtClaims(
    val userId: String,
    val role: Role,
    val isShadow: Boolean,
    val jti: String          // 블랙리스트 키로 사용
)
```

Redis 키 구조:
```
refresh:{userId}        → Refresh Token (TTL 4시간)
blacklist:{jti}         → 블랙리스트 등록된 Access Token
```

---

## Shadow JWT (TODO — Day 3)

```kotlin
// ShadowJwtProvider.kt 구현 예정
data class ShadowClaims(
    val userId: String,           // 대상 마케터 ID
    val role: Role,               // 대상 마케터 역할
    val operatorId: String,       // 발급한 운영자 ID
    val isShadow: Boolean = true,
    val targetName: String,       // 대상 마케터 이름
    val targetWorkEmail: String?  // 업무용 이메일 (없으면 null)
)
```

- 유효기간: 30분
- 서명 키: AWS Secrets Manager (`aoc-se-secret-shadow-jwt-key`)
- 만료 시 재발급 없음

---

## ActorContext (ThreadLocal)

요청 컨텍스트에서 행위자 정보를 전달하는 구조.
Filter에서 세팅 → EntityListener에서 꺼내 사용.

```kotlin
@Component("actorContext")
object ActorContext {
    private val holder = ThreadLocal<ActorInfo>()

    fun set(actorId: String, operatorId: String?, isShadow: Boolean)
    fun get(): ActorInfo?
    fun clear()  // 반드시 finally 블록에서 호출
    fun isShadow(): Boolean  // @PreAuthorize("!@actorContext.isShadow()") 에서 사용
}
```

---

## API 응답 형식

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null    // ErrorCode.code (에러 시에만)
) {
    companion object {
        fun <T> ok(data: T) = ApiResponse(success = true, data = data)
        fun error(errorCode: ErrorCode) = ApiResponse<Nothing>(
            success = false,
            message = errorCode.message,
            code = errorCode.code
        )
    }
}
```

---

## ErrorCode 구조

```kotlin
enum class ErrorCode(val status: HttpStatus, val code: String, val message: String) {
    // Auth: AUTH_00x
    // Member: MEMBER_00x
    // Shadow: SHADOW_00x
    // Permission: PERMISSION_00x
    // Email: EMAIL_00x
    // Server: SERVER_00x
}
```

새 에러 추가 시 `common/ErrorCode.kt`에만 추가하면 됨.

---

## 예외 처리 구조

```
BusinessException (open class)
  └── MemberNotFoundException
  └── AocAccessDeniedException    # Spring Security AccessDeniedException과 이름 충돌 방지
  └── ShadowActionNotAllowedException

CognitoJwtException               # Cognito JWT 파싱/검증 실패
```

모든 예외는 `GlobalExceptionHandler`에서 처리 → `ApiResponse.error()` 형태로 응답.

---

## 권한 어노테이션

```kotlin
// 운영자만 접근
@PreAuthorize("hasRole('OPERATOR')")

// 쉐도우 세션 제외
@PreAuthorize("!@actorContext.isShadow()")

// 마케터이면서 쉐도우 세션 제외
@PreAuthorize("hasRole('MARKETER') and !@actorContext.isShadow()")
```

### API별 권한 매핑

| API | 권한 |
|---|---|
| `PUT /members/me` | `!@actorContext.isShadow()` |
| `DELETE /members/me` | `hasRole('MARKETER') and !@actorContext.isShadow()` |
| `PUT /notification-settings` | `hasRole('MARKETER') and !@actorContext.isShadow()` |
| `PUT /members/{id}/role` | `hasRole('OPERATOR')` |
| `GET /histories` | `hasRole('OPERATOR')` |
| `POST /shadow-login` | `hasRole('OPERATOR')` |

---

## DB 테이블 설계

```sql
CREATE TABLE member (
    id          VARCHAR(26)  PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    provider    VARCHAR(50)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    work_email  VARCHAR(255),
    role        VARCHAR(50)  NOT NULL DEFAULT 'MARKETER'
                    CHECK (role IN ('MARKETER', 'AGENCY_MANAGER', 'OPERATOR')),
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'DELETED')),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_setting (
    id              VARCHAR(26)  PRIMARY KEY,
    member_id       VARCHAR(26)  NOT NULL UNIQUE REFERENCES member(id),
    inquiry_alert   BOOLEAN      NOT NULL DEFAULT true,
    marketing_alert BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE history (
    id           VARCHAR(26)  PRIMARY KEY,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(26)  NOT NULL,
    action       VARCHAR(20)  NOT NULL,
    before_value JSONB,
    after_value  JSONB,
    actor_id     VARCHAR(26)  NOT NULL,
    operator_id  VARCHAR(26),
    is_shadow    BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

---

## 로컬 실행

```powershell
# Docker 컨테이너 실행
docker start aoc-postgres aoc-redis

# 서버 실행 (PowerShell)
.\gradlew.bat bootRun

# 또는 IntelliJ에서 AocSeApplication 실행
# spring.profiles.active=local 자동 적용

# API 문서
# http://localhost:8080/swagger-ui.html
```

---

## 주의사항

- Kotlin JPA Entity는 `open class` 또는 allOpen 플러그인 사용
- `data class`를 JPA Entity로 쓰면 equals/hashCode 문제 발생 → 일반 `class` 사용
- `@Enumerated(EnumType.STRING)` 필수 (ORDINAL 사용 금지)
- `application-local.yml`, `application-prod.yml`은 절대 git에 올리지 않을 것
- Shadow JWT는 Cognito 미사용, 서버가 Secrets Manager 서명 키로 직접 발급/검증
- `ActorContext.clear()`는 반드시 finally 블록에서 호출