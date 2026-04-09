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
│   │   │   ├── MemberService.kt            ✅ loginOrRegister() / 🔧 Day 3 수정 (상태 검증, 동시 로그인 처리) / 🔧 Day 5 수정 (로그인 히스토리, 상태 변경)
│   │   │   └── EmailVerificationService.kt ✅ Day 5 신규 (이메일 인증 코드 발송/확인, 30분 잠금)
│   │   ├── presentation/
│   │   │   ├── MemberController.kt         ✅ Day 5 신규 (GET/PUT/DELETE /members/me, PUT /members/{id}/role, GET /members)
│   │   │   └── dto/
│   │   └── infra/
│   │       ├── CognitoClient.kt            ✅ JWKS 기반 검증
│   │       ├── EmailSender.kt              ✅ Day 5 신규 (인터페이스)
│   │       ├── LogEmailSender.kt           ✅ Day 5 신규 (로컬용 콘솔 출력)
│   │       └── SesEmailSender.kt           ✅ Day 5 신규 (prod용 AWS SES 발송)
│   ├── auth/
│   │   ├── AuthController.kt               ✅ POST /auth/callback
│   │   ├── JwtProvider.kt                  ✅ HS256, jti 포함
│   │   ├── JwtAuthenticationFilter.kt      ✅ Bearer 추출 → 검증 → 블랙리스트 → 동시로그인 차단 / Shadow JWT 분기 처리
│   │   ├── ActorContext.kt                 ✅ ThreadLocal, shadowId 포함
│   │   ├── CognitoJwtException.kt          ✅
│   │   ├── ShadowJwtProvider.kt            ✅ Day 3 완료
│   │   ├── ShadowService.kt                ✅ Day 3 완료 / 🔧 Day 5 수정 (shadow:target 관리, invalidateShadowByTarget)
│   │   └── ShadowController.kt             ✅ Day 3 완료 (POST/DELETE /shadow-login)
│   ├── history/
│   │   ├── History.kt                      ✅ Day 3 완료 (shadowId 포함) / Day 4에 EntityListener 연동
│   │   ├── HistoryAction.kt                ✅ Day 5 신규 (PERSIST/UPDATE/DELETE/LOGIN/SHADOW_LOGIN_START/SHADOW_LOGIN_END)
│   │   ├── HistoryRepository.kt            ✅ Day 3 완료 / 🔧 Day 5 수정 (JpaSpecificationExecutor 추가)
│   │   ├── HistoryController.kt            ✅ Day 5 신규 (GET /histories, GET /histories/export)
│   │   ├── HistoryResponse.kt              ✅ Day 5 신규
│   │   ├── HistoryEntityListener.kt        ✅ Day 4 완료
│   │   └── HistoryEventHandler.kt          ✅ Day 4 완료
│   ├── notification/
│   │   ├── NotificationSetting.kt          ✅
│   │   ├── NotificationSettingRepository.kt ✅
│   │   ├── NotificationController.kt       ✅ Day 5 신규 (GET/PUT /notification-settings)
│   │   ├── NotificationService.kt          ✅ Day 5 신규
│   │   └── dto/                            ✅ Day 5 신규 (NotificationSettingResponse, UpdateNotificationRequest)
│   ├── common/
│   │   ├── BaseEntity.kt                   ✅ ULID, snapshot
│   │   ├── ApiResponse.kt                  ✅ ok/error 팩토리, code 필드
│   │   ├── ErrorCode.kt                    ✅ ErrorCode enum / 🔧 Day 3 수정 (MEMBER_STATUS 에러코드 추가) / 🔧 Day 5 수정 (EMAIL_005, HISTORY_001/002 추가)
│   │   ├── BusinessException.kt            ✅ + 하위 예외 클래스 / 🔧 Day 3 수정 (MemberStatusException 추가)
│   │   ├── GlobalExceptionHandler.kt       ✅ MethodArgumentNotValidException 포함 / 🔧 Day 5 수정 (AccessDeniedException 핸들러 추가)
│   │   └── SpringApplicationContext.kt     ✅ Day 4 완료
│   └── config/
│       ├── SecurityConfig.kt               ✅ STATELESS, CSRF off, JwtFilter 등록
│       ├── CorsConfig.kt                   ✅ 로컬 5173 + CloudFront
│       ├── JpaConfig.kt                    ✅ @EnableJpaAuditing
│       ├── RedisConfig.kt                  ✅ StringRedisSerializer
│       └── SwaggerConfig.kt                ✅ bearerAuth 스킴 등록
├── src/main/resources/
│   ├── application.yml                     공통 설정 (profiles.active=local)
│   ├── application-local.yml               로컬 개발용 (git 제외)
│   ├── application-prod.yml                배포용 환경변수 참조 (git 포함 — ${ENV_VAR} 참조만)
│   ├── application-prod.yml.example        환경변수 키 목록 문서화 (git 포함)
│   └── db/migration/
│       ├── V1__init_schema.sql             ✅ Day 5 신규 (최종 스키마 — Day 2 CREATE + Day 3 ALTER 통합)
│       └── V2__history_action_no_constraint.sql  ✅ Day 5 신규 (history_action_check 제약 제거)
├── build.gradle.kts
├── Dockerfile
├── CLAUDE.md
├── CONVENTION.md
└── README.md
```

범례: ✅ 완료 / 🔧 기존 파일 수정 / 🆕 신규 파일

---

## Day별 작업 대상 파일

### Day 3 — 쉐도우 로그인 + DB 스키마 확장 ✅ 완료

#### 🗄️ DB 수정 완료 (로컬 적용됨)

```sql
ALTER TABLE member DROP CONSTRAINT member_status_check;
ALTER TABLE member ADD CONSTRAINT member_status_check
    CHECK (status IN ('ACTIVE', 'DORMANT', 'SUSPENDED', 'SECURITY_LOCKOUT', 'PENDING_DELETION', 'DELETED'));
ALTER TABLE member ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE history ADD COLUMN shadow_id VARCHAR(26);
```

#### ✅ 완료된 파일
- `Member.kt` — MemberStatus 6종, deletedAt
- `MemberService.kt` — 상태 검증, 동시 로그인 차단
- `ActorContext.kt` — shadowId 추가
- `JwtAuthenticationFilter.kt` — Shadow JWT 분기(ShadowJwtProvider), 동시 로그인 차단
- `SecurityConfig.kt` — ShadowJwtProvider 주입
- `ErrorCode.kt` — MEMBER_STATUS 4종
- `BusinessException.kt` — MemberStatusException
- `History.kt` — shadowId 필드
- `ShadowJwtProvider.kt` / `ShadowService.kt` / `ShadowController.kt` / `HistoryRepository.kt` 신규 생성

#### ⚠️ Day 3 특이사항 (다음 작업 시 참고)

1. **History JSONB 매핑** — `before_value`, `after_value`는 DB가 JSONB 타입이므로 엔티티 필드에 반드시 `@Column(columnDefinition = "jsonb")` 명시 필요. 없으면 schema-validation 에러 발생.

2. **Shadow JWT 서명 키 주입 방식** — 로컬은 `application-local.yml`의 `shadow-jwt.secret` 값 사용. prod는 Secrets Manager에서 주입. `application-prod.yml.example`에 아래 항목 추가 필요:
   ```yaml
   shadow-jwt:
     secret: ${SHADOW_JWT_SECRET}
   ```

3. **JwtAuthenticationFilter 분기** — 토큰의 `isShadow` 클레임으로 `ShadowJwtProvider` / `JwtProvider` 분기. Shadow JWT는 동시 로그인 차단 체크(`session:{userId}`) skip.

---

### Day 5 — 나머지 API 구현 + Flyway + 설계 보완 ✅ 완료 (4/9)

**신규 API**
- `GET /members/me`, `PUT /members/me`, `DELETE /members/me`
- `PUT /members/{id}/role`, `GET /members`
- `POST /members/me/work-email/verify`, `POST /members/me/work-email/confirm`
- `GET /notification-settings`, `PUT /notification-settings`
- `GET /histories`, `GET /histories/export` (90일 범위 제한)

**HistoryAction enum**
- DB CHECK constraint 제거, enum이 단일 진실 공급원
- `PERSIST / UPDATE / DELETE / LOGIN / SHADOW_LOGIN_START / SHADOW_LOGIN_END`

**Shadow 무효화 정책 확정**
- 트리거: 대상 계정 status 변경 OR role 변경
- 구현: `shadow:target:{targetId}` Set에서 operatorId 목록 조회 → 각 `shadow:operator` 키 삭제
- `ShadowService.invalidateShadowByTarget(targetId)` 메서드

**이메일 인증 정책**
- 재발송 시 시도 횟수 누적 (리셋 없음)
- 5회 실패 시 `email-verify:{userId}:locked` (TTL 30분)
- 잠금 중 발송/확인 시도 → `EMAIL_VERIFY_LOCKED` (HTTP 429)

**토큰 안전성**
- Redis 저장 실패 시 JWT 발급 롤백 → `SERVER_ERROR` 반환

**Flyway 도입**
- `baseline-on-migrate: true` (로컬 — 이미 테이블 존재)
- `baseline-on-migrate: false` (prod — 최초 실행, V1부터 적용)
- `ddl-auto: none` (flyway가 스키마 관리)

---

### Day 4 — 히스토리 자동 기록 ✅ 완료 (4/8)

- `SpringApplicationContext.kt` — ApplicationContextAware 브릿지 구현
- `HistoryEntityListener.kt` — @PrePersist / @PreUpdate / @PreRemove 이벤트 발행
- `HistoryEventHandler.kt` — @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)
- `HistoryEntityListenerTest.kt` — H2 JSONB 호환 처리 (schema.sql + ddl-auto: none)
- 테스트 전체 통과

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

## CI/CD (be-ci.yml) ✅ 동작 확인

- PR → 테스트 (`./gradlew test`)
- main 머지 → bootJar → Docker 빌드 → ECR push → ECS 재배포
- `role-to-assume`: `arn:aws:iam::148761639846:role/HyperLimitedAccessRole`
- `role-skip-session-tagging`: true
- `registries`: `"148761639846"`

GitHub Repository Secrets 등록 완료:
```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
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

## 히스토리 자동 기록 구조 ✅ Day 4 완료

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

SpringApplicationContext.kt — ApplicationContextAware 브릿지.
EntityListener에서 Spring Bean(HistoryRepository)을 꺼내기 위해 사용.

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
refresh:{userId}             → Refresh Token (TTL 4시간)
blacklist:{jti}              → 블랙리스트 등록된 Access Token
session:{userId}             → 현재 유효한 Access Token jti (동시 로그인 차단용)
shadow:operator:{operatorId} → 운영자의 현재 활성 Shadow JWT jti (쉐도우 전환 시 이전 것 무효화)
shadow:target:{targetId}     → Set<operatorId> (TTL 30분) — 해당 대상에 접속 중인 운영자 목록
email-verify:{userId}        → 발송된 인증 코드 (TTL 5분)
email-verify:{userId}:attempts → 시도 횟수 누적 카운터
email-verify:{userId}:locked → 잠금 플래그 (TTL 30분, 5회 초과 시 설정)
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
- `application.yml`은 git 포함 (공통 설정, 민감 정보 없음)
- `application-prod.yml`은 git 포함 (`${ENV_VAR}` 참조만 있음)
- `application-local.yml`은 절대 git에 올리지 않을 것
- JAVA_HOME은 Java 17로 설정 필요 (ms-17.0.18 또는 temurin-17)
- Shadow JWT는 Cognito 미사용, 서버가 Secrets Manager 서명 키로 직접 발급/검증
- `ActorContext.clear()`는 반드시 finally 블록에서 호출
- DB 스키마 변경은 반드시 ALTER 방식으로 — Day 2 이후 CREATE TABLE 재실행 금지
- MemberStatus.DORMANT는 상태값만 준비, 전환 정책은 본 프로젝트에서 결정
- PENDING_DELETION → DELETED 배치 전환은 본 프로젝트 구현 대상 (과제 범위 밖)
- History의 `before_value`, `after_value`는 `@Column(columnDefinition = "jsonb")` 필수
- Shadow JWT 서명 키는 로컬 `application-local.yml`, prod는 Secrets Manager — `application-prod.yml.example`에 `shadow-jwt.secret: ${SHADOW_JWT_SECRET}` 추가 필요
- DB 스키마 변경은 반드시 `db/migration/` 하위 `Vn__` 파일로 관리 (직접 ALTER 금지)
- `HistoryAction`은 enum으로만 관리, DB CHECK constraint 없음 (V2로 제거 완료)
- `shadow:target:{targetId}`는 Set 타입 (`opsForSet` 사용)
- 이메일 인증 잠금: `email-verify:{userId}:locked` (TTL 30분), 5회 초과 시 설정
- OPERATOR는 본인 역할 변경 불가 (`ActorContext.userId == pathVariable id` 체크)
- CSV export: `from~to` 범위 90일 초과 시 400 반환
- SES 발신자: `ses.from-address` 설정값 사용 (로컬: `local@test.com`, prod: SES 인증된 이메일)