package com.aoc.auth

import com.aoc.common.BusinessException
import com.aoc.common.ErrorCode
import com.aoc.config.SecurityConfig
import com.aoc.history.History
import com.aoc.history.HistoryController
import com.aoc.history.HistoryRepository
import com.aoc.member.application.MemberService
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.MemberStatus
import com.aoc.member.domain.Role
import com.aoc.member.infra.CognitoClaims
import com.aoc.notification.NotificationSettingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.Optional
import kotlin.test.assertEquals

// ── 서비스 단위 테스트 ────────────────────────────────────────────────────────

class TokenServiceEdgeCaseTest {

    private lateinit var memberRepository: MemberRepository
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var historyRepository: HistoryRepository
    private lateinit var jwtProvider: JwtProvider
    private lateinit var shadowService: ShadowService
    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberRepository = mock()
        redisTemplate = mock()
        valueOps = mock()
        historyRepository = mock()
        jwtProvider = mock()
        shadowService = mock()

        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(jwtProvider.generateToken(any(), any(), any())).thenReturn("mock-token")
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }

        memberService = MemberService(
            memberRepository, mock<NotificationSettingRepository>(), jwtProvider,
            redisTemplate, historyRepository, shadowService
        )
    }

    @Test
    fun `두 번째 로그인 시 첫 번째 jti가 블랙리스트에 등록된다`() {
        val member = Member(
            email = "user@test.com", name = "유저", provider = "GOOGLE",
            providerId = "google-1", role = Role.MARKETER
        )
        val claims = CognitoClaims(sub = member.providerId, email = member.email, name = member.name)
        whenever(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(member)
        whenever(valueOps.get("session:${member.id}")).thenReturn("old-jti")

        memberService.loginOrRegister(claims)

        verify(valueOps).set(eq("blacklist:old-jti"), eq("1"), eq(Duration.ofHours(1)))
    }

    @Test
    fun `Redis 저장 실패 시 로그인 → INTERNAL_SERVER_ERROR 예외`() {
        val member = Member(
            email = "user@test.com", name = "유저", provider = "GOOGLE",
            providerId = "google-1", role = Role.MARKETER
        )
        val claims = CognitoClaims(sub = member.providerId, email = member.email, name = member.name)
        whenever(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(member)
        whenever(valueOps.get(any<String>())).thenReturn(null)
        whenever(valueOps.set(any(), any(), any<Duration>()))
            .thenThrow(RuntimeException("Redis 연결 실패"))

        val ex = assertThrows<BusinessException> { memberService.loginOrRegister(claims) }

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, ex.errorCode)
    }

    @Test
    fun `deleteMe 시 session 키 삭제 및 jti 블랙리스트 등록`() {
        val member = Member(
            email = "user@test.com", name = "유저", provider = "GOOGLE",
            providerId = "google-1", role = Role.MARKETER
        )
        whenever(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        whenever(valueOps.get("session:${member.id}")).thenReturn("current-jti")

        memberService.deleteMe(member.id)

        assertEquals(MemberStatus.PENDING_DELETION, member.status)
        verify(valueOps).set(eq("blacklist:current-jti"), eq("1"), eq(Duration.ofHours(1)))
        verify(redisTemplate).delete(eq("session:${member.id}"))
    }
}

// ── 필터 레벨 테스트 ─────────────────────────────────────────────────────────

@WebMvcTest(HistoryController::class)
@Import(SecurityConfig::class)
class TokenFilterEdgeCaseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var historyRepository: HistoryRepository

    @MockBean
    private lateinit var jwtProvider: JwtProvider

    @MockBean
    private lateinit var shadowJwtProvider: ShadowJwtProvider

    @MockBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private lateinit var valueOps: ValueOperations<String, String>

    @BeforeEach
    fun setUp() {
        valueOps = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get(any<String>())).thenReturn(null)
        whenever(redisTemplate.hasKey(any())).thenReturn(false)
    }

    @Test
    fun `블랙리스트에 등록된 토큰으로 요청 시 401을 반환한다`() {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = "user-1", role = Role.OPERATOR, isShadow = false, jti = "blacklisted-jti")
        )
        whenever(redisTemplate.hasKey("blacklist:blacklisted-jti")).thenReturn(true)

        mockMvc.perform(
            get("/histories").header("Authorization", "Bearer blacklisted-token")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `동시 로그인으로 session jti 불일치 시 401을 반환한다`() {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = "user-1", role = Role.OPERATOR, isShadow = false, jti = "old-jti")
        )
        whenever(valueOps.get("session:user-1")).thenReturn("new-jti")

        mockMvc.perform(
            get("/histories").header("Authorization", "Bearer old-token")
        )
            .andExpect(status().isUnauthorized)
    }
}
