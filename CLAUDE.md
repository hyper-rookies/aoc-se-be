# 백엔드 개발 가이드 (aoc-se-be)
# 파일 경로: aoc-se-be/CLAUDE.md

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
│   │   │   ├── Member.kt                   ✅ Role, MemberStatus enum 포함 / 🔧 Day 3 수정 (MemberStatus 6종 확장, deletedAt 추가)
│   │   │   └── MemberRepository.kt         ✅
│   │   ├── application/
│   │   │   └── MemberService.kt            ✅ loginOrRegister() / 🔧 Day 3 수정 (상태 검증, 동시 로그인 처리)
│   │   ├── presentation/
│   │   │   ├── MemberController.kt         (미구현)
│   │   │   └── dto/
│   │   └── infra/
│   │       └── CognitoClient.kt            ✅ JWKS 기반 검증
│   ├── auth/
│   │   ├── AuthController.kt               ✅ POST /auth/callback / 🆕 Day 3 추가 (POST/DELETE /shadow-login)
│   │   ├── JwtProvider.kt                  ✅ HS256, jti 포함
│   │   ├── JwtAuthenticationFilter.kt      ✅ Bearer 추출 → 검증 → 블랙리스트 / 🔧 Day 3 수정 (동시 로그인 차단)
│   │   ├── ActorContext.kt                 ✅ ThreadLocal, @Component("actorContext") / 🔧 Day 3 수정 (shadowId 추가)
│   │   ├── CognitoJwtException.kt          ✅
│   │   └── ShadowJwtProvider.kt            🆕 Day 3 신규
│   ├── history/
│   │   ├── History.kt                      (골격만) / 🔧 Day 3 수정 (shadow_id 필드 추가) / Day 4 완성
│   │   ├── HistoryRepository.kt            🆕 Day 3 신규 (쉐도우 감사 로그용)
│   │   ├── HistoryEntityListener.kt        (stub — Day 4)
│   │   └── HistoryEventHandler.kt          (미구현 — Day 4)
│   ├── notification/
│   │   ├── NotificationSetting.kt          ✅
│   │   └── NotificationSettingRepository.kt ✅
│   ├── common/
│   │   ├── BaseEntity.kt                   ✅ ULID, snapshot
│   │   ├── ApiResponse.kt                  ✅ ok/error 팩토리, code 필드
│   │   ├── ErrorCode.kt                    ✅ ErrorCode enum / 🔧 Day 3 수정 (MEMBER_STATUS 에러코드 추가)
│   │   ├── BusinessException.kt            ✅ + 하위 예외 클래스 / 🔧 Day 3 수정 (MemberStatusException 추가)
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

범례: ✅ 완료 / 🔧 기존 파일 수정 / 🆕 신규 파일

---

## Day별 작업 대상 파일

### Day 3 — 쉐도우 로그인 + DB 스키마 확장

> ⚠️ 아래 파일들은 Day 2에 이미 구현된 것을 **수정**하는 작업임. 새로 만들지 말 것.

#### 🔧 수정 대상 파일

**`member/domain/Member.kt`**
```kotlin
// MemberStatus enum 확장
enum class MemberStatus {
    ACTIVE,
    DORMANT,           // 휴면 (상태값만 준비, 전환 정책은 본 프로젝트)
    SUSPENDED,         // 정지 (관리자 수동)
    SECURITY_LOCKOUT,  // 잠금 (반복 실패 자동 — 본 프로젝트 구현)
    PENDING_DELETION,  // 탈퇴 대기
    DELETED
}

// deletedAt 필드 추가
var deletedAt: LocalDateTime? = null
```

**`member/application/MemberService.kt`**
```kotlin
// loginOrRegister()에 두 가지 처리 추가:
// 1. status 검증 — ACTIVE가 아니면 MemberStatusException(ErrorCode.MEMBER_DORMANT 등) 던지기
// 2. 동시 로그인 차단 — 기존 session:{userId}의 jti를 blacklist:{jti}에 등록 후 새 jti로 교체
```

**`auth/ActorContext.kt`**
```kotlin
// set() 시그니처에 shadowId 파라미터 추가
fun set(actorId: String, operatorId: String?, shadowId: String?, isShadow: Boolean)

// ActorInfo data class에도 shadowId: String? 추가
```

**`auth/JwtAuthenticationFilter.kt`**
```kotlin
// 블랙리스트 체크 이후에 동시 로그인 차단 체크 추가:
// session:{userId} 에 저장된 jti와 현재 토큰 jti가 다르면 → 401 반환
// (단, Shadow JWT는 이 체크 대상에서 제외 — isShadow=true인 경우 skip)
```

**`common/ErrorCode.kt`**
```kotlin
// MEMBER_STATUS 에러코드 4종 추가
MEMBER_DORMANT(HttpStatus.FORBIDDEN, "MEMBER_STATUS_001", "휴면 계정입니다. 고객센터에 문의해주세요.")
MEMBER_SUSPENDED(HttpStatus.FORBIDDEN, "MEMBER_STATUS_002", "정지된 계정입니다. 고객센터에 문의해주세요.")
MEMBER_SECURITY_LOCKOUT(HttpStatus.FORBIDDEN, "MEMBER_STATUS_003", "보안 잠금 상태입니다. 잠시 후 다시 시도해주세요.")
MEMBER_PENDING_DELETION(HttpStatus.FORBIDDEN, "MEMBER_STATUS_004", "탈퇴 처리 중인 계정입니다.")
```

**`common/BusinessException.kt`**
```kotlin
// MemberStatusException 추가
class MemberStatusException(errorCode: ErrorCode) : BusinessException(errorCode)
```

**`history/History.kt`** (골격 → 부분 완성)
```kotlin
// shadow_id 필드 추가
var shadowId: String? = null
```

#### 🆕 신규 파일

- `auth/ShadowJwtProvider.kt` — Shadow JWT 발급/검증
- `auth/ShadowController.kt` — POST /shadow-login, DELETE /shadow-login
- `auth/ShadowService.kt` — 쉐도우 로그인 비즈니스 로직
- `history/HistoryRepository.kt` — 쉐도우 감사 로그 저장용

#### 🗄️ DB 수정 (ALTER — CREATE 아님)

로컬 Docker PostgreSQL에 직접 실행:
```sql
ALTER TABLE member DROP CONSTRAINT IF EXISTS member_status_check;
ALTER TABLE member ADD CONSTRAINT member_status_check
    CHECK (status IN ('ACTIVE', 'DORMANT', 'SUSPENDED', 'SECURITY_LOCKOUT', 'PENDING_DELETION', 'DELETED'));
ALTER TABLE member ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE history ADD COLUMN shadow_id VARCHAR(26);
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
                                 계정 상태(status) 확인 → ACTIVE가 아니면 상태별 에러 반환
                                 role 포함한 서버 자체 JWT 발급 (HS256)
                                 Refresh Token → Redis 저장 (4시간 TTL)
                                 기존 세션 토큰 무효화 (동시 로그인 차단 — Last Write Wins)
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
session:{userId}        → 현재 유효한 Access Token jti (동시 로그인 차단용)
shadow:operator:{operatorId} → 운영자의 현재 활성 Shadow JWT jti (쉐도우 전환 시 이전 것 무효화)
```

---

## 계정 상태 (MemberStatus)

```kotlin
enum class MemberStatus {
    ACTIVE,           // 정상
    DORMANT,          // 휴면 (정책 미확정 — 상태값만 준비)
    SUSPENDED,        // 정지 (관리자가 수동 처리)
    SECURITY_LOCKOUT, // 잠금 (로그인 반복 실패 시 자동)
    PENDING_DELETION, // 탈퇴 대기 (탈퇴 요청 후 30일간 유지 — 본 프로젝트에서 배치 처리)
    DELETED           // 삭제 완료
}
```

로그인 시 상태 검증 흐름:
```
ACTIVE          → 로그인 허용
DORMANT         → 로그인 차단 + "휴면 계정입니다. 고객센터에 문의해주세요." 반환
SUSPENDED       → 로그인 차단 + "정지된 계정입니다. 고객센터에 문의해주세요." 반환
SECURITY_LOCKOUT→ 로그인 차단 + "보안 잠금 상태입니다. 잠시 후 다시 시도해주세요." 반환
PENDING_DELETION→ 로그인 차단 + "탈퇴 처리 중인 계정입니다." 반환
DELETED         → 신규 가입으로 처리 (동일 이메일 + provider 재가입 허용)
```

> **과제 구현 범위**: 상태 필드 및 로그인 차단 처리까지. SECURITY_LOCKOUT 자동 전환(반복 실패 감지), PENDING_DELETION → DELETED 배치 삭제는 본 프로젝트에서 구현.

---

## Shadow JWT (Day 3)

```kotlin
// ShadowJwtProvider.kt
data class ShadowClaims(
    val jti: String,              // Shadow JWT 고유 ID (무효화 키로 사용)
    val userId: String,           // 대상 계정 ID (마케터/대행사관리자)
    val role: Role,               // 대상 계정 역할
    val operatorId: String,       // 발급한 운영자 ID
    val isShadow: Boolean = true,
    val targetName: String,       // 대상 계정 이름 (배너 표시용)
    val targetWorkEmail: String?  // 업무용 이메일 (없으면 null)
)
```

제한사항:
- 대상이 `OPERATOR`인 경우 차단 → `ShadowActionNotAllowedException`
- 운영자 본인 계정으로의 쉐도우 로그인 차단
- 운영자가 새로운 쉐도우 발급 시 이전 Shadow JWT 자동 무효화 (`shadow:operator:{operatorId}` Redis 키 활용)

유효기간: 30분, 갱신 없음  
서명 키: AWS Secrets Manager (`aoc-se-secret-shadow-jwt-key`)

---

## ActorContext (ThreadLocal)

요청 컨텍스트에서 행위자 정보를 전달하는 구조.
Filter에서 세팅 → EntityListener에서 꺼내 사용.

```kotlin
@Component("actorContext")
object ActorContext {
    private val holder = ThreadLocal<ActorInfo>()

    fun set(actorId: String, operatorId: String?, shadowId: String?, isShadow: Boolean)
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
    // Member Status: MEMBER_STATUS_00x
    //   MEMBER_DORMANT, MEMBER_SUSPENDED, MEMBER_SECURITY_LOCKOUT, MEMBER_PENDING_DELETION
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
  └── MemberStatusException        # 계정 상태 차단 (DORMANT/SUSPENDED/SECURITY_LOCKOUT/PENDING_DELETION)
  └── AocAccessDeniedException     # Spring Security AccessDeniedException과 이름 충돌 방지
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
| `DELETE /shadow-login` | 쉐도우 JWT 소지자 (`isShadow=true`) |

---

## DB 테이블 설계

### Day 2에 생성된 테이블 (현재 상태)

```sql
-- 이미 실행 완료. 수정하지 말 것.
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

CREATE TABLE notification_setting ( ... );

CREATE TABLE history (
    ...
    actor_id     VARCHAR(26)  NOT NULL,
    operator_id  VARCHAR(26),
    is_shadow    BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### Day 3에 실행할 ALTER (⚠️ 새로 CREATE 아님)

```sql
-- 1. member 상태값 확장 (CHECK constraint 교체)
ALTER TABLE member
    DROP CONSTRAINT IF EXISTS member_status_check;

ALTER TABLE member
    ADD CONSTRAINT member_status_check
    CHECK (status IN ('ACTIVE', 'DORMANT', 'SUSPENDED', 'SECURITY_LOCKOUT', 'PENDING_DELETION', 'DELETED'));

-- 2. member 탈퇴 시각 컬럼 추가
ALTER TABLE member
    ADD COLUMN deleted_at TIMESTAMP;

-- 3. history에 shadow_id 컬럼 추가 (감사추적용 Shadow JWT jti)
ALTER TABLE history
    ADD COLUMN shadow_id VARCHAR(26);
```

> **주의**: 위 ALTER문은 Day 3 시작 시 로컬 Docker PostgreSQL에 직접 실행할 것.  
> 로컬 적용 후 배포 환경은 별도 마이그레이션 스크립트로 관리 예정.

### 최종 테이블 구조 (ALTER 적용 후)

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
                    CHECK (status IN ('ACTIVE', 'DORMANT', 'SUSPENDED', 'SECURITY_LOCKOUT', 'PENDING_DELETION', 'DELETED')),
    deleted_at  TIMESTAMP,
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
    shadow_id    VARCHAR(26),               -- Shadow JWT jti (쉐도우 세션 감사추적용)
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
- DB 스키마 변경은 반드시 ALTER 방식으로 — Day 2 이후 CREATE TABLE 재실행 금지
- MemberStatus.DORMANT는 상태값만 준비, 전환 정책은 본 프로젝트에서 결정
- PENDING_DELETION → DELETED 배치 전환은 본 프로젝트 구현 대상 (과제 범위 밖)
