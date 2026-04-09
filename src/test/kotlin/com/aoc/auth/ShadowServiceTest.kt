package com.aoc.auth

import com.aoc.history.History
import com.aoc.history.HistoryRepository
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.Role
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeUnit

class ShadowServiceTest {

    private lateinit var memberRepository: MemberRepository
    private lateinit var shadowJwtProvider: ShadowJwtProvider
    private lateinit var historyRepository: HistoryRepository
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var setOps: SetOperations<String, String>
    private lateinit var shadowService: ShadowService

    @BeforeEach
    fun setUp() {
        memberRepository = mock()
        shadowJwtProvider = mock()
        historyRepository = mock()
        redisTemplate = mock()
        valueOps = mock()
        setOps = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(redisTemplate.opsForSet()).thenReturn(setOps)
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }

        shadowService = ShadowService(memberRepository, shadowJwtProvider, historyRepository, redisTemplate)
    }

    @Test
    fun `새 쉐도우 발급 시 기존 shadow JWT는 블랙리스트에 등록되고 Redis 키가 삭제된다`() {
        val target = Member(
            email = "marketer@example.com",
            name = "마케터",
            provider = "GOOGLE",
            providerId = "google-sub",
            role = Role.MARKETER
        )
        whenever(memberRepository.findById("target-id")).thenReturn(Optional.of(target))
        whenever(valueOps.get("shadow:operator:operator-id")).thenReturn("old-shadow-jti")
        whenever(shadowJwtProvider.generateToken(any(), any(), any(), any(), anyOrNull())).thenReturn("new-shadow-token")
        whenever(shadowJwtProvider.validateToken("new-shadow-token")).thenReturn(
            ShadowClaims(
                jti = "new-shadow-jti",
                userId = target.id,
                role = Role.MARKETER,
                operatorId = "operator-id",
                targetName = "마케터",
                targetWorkEmail = null
            )
        )

        shadowService.startShadow("operator-id", "target-id")

        verify(valueOps).set(eq("blacklist:old-shadow-jti"), eq("1"), eq(Duration.ofMinutes(30)))
        verify(redisTemplate).delete(eq("shadow:operator:operator-id"))
        verify(setOps).add(eq("shadow:target:target-id"), eq("operator-id"))
        verify(redisTemplate).expire(eq("shadow:target:target-id"), eq(30L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `startShadow 시 shadow-target 키에 operatorId가 추가되고 TTL이 30분으로 설정된다`() {
        val target = Member(
            email = "marketer@example.com",
            name = "마케터",
            provider = "GOOGLE",
            providerId = "google-sub",
            role = Role.MARKETER
        )
        whenever(memberRepository.findById("target-id")).thenReturn(Optional.of(target))
        whenever(valueOps.get("shadow:operator:operator-id")).thenReturn(null)
        whenever(shadowJwtProvider.generateToken(any(), any(), any(), any(), anyOrNull())).thenReturn("shadow-token")
        whenever(shadowJwtProvider.validateToken("shadow-token")).thenReturn(
            ShadowClaims(
                jti = "new-jti",
                userId = target.id,
                role = Role.MARKETER,
                operatorId = "operator-id",
                targetName = "마케터",
                targetWorkEmail = null
            )
        )

        shadowService.startShadow("operator-id", "target-id")

        verify(setOps).add(eq("shadow:target:target-id"), eq("operator-id"))
        verify(redisTemplate).expire(eq("shadow:target:target-id"), eq(30L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `쉐도우 로그아웃 시 shadow JWT 블랙리스트 등록 및 operator Redis 키가 삭제된다`() {
        whenever(setOps.size("shadow:target:marketer-id")).thenReturn(0L)

        shadowService.endShadow(
            shadowJti = "shadow-jti-123",
            operatorId = "operator-id",
            targetMemberId = "marketer-id"
        )

        verify(valueOps).set(eq("blacklist:shadow-jti-123"), eq("1"), eq(Duration.ofMinutes(30)))
        verify(redisTemplate).delete(eq("shadow:operator:operator-id"))
        verify(setOps).remove(eq("shadow:target:marketer-id"), eq("operator-id"))
        verify(redisTemplate).delete(eq("shadow:target:marketer-id"))
    }

    @Test
    fun `endShadow 후 Set에 다른 operatorId가 남아 있으면 shadow-target 키를 삭제하지 않는다`() {
        whenever(setOps.size("shadow:target:marketer-id")).thenReturn(1L)

        shadowService.endShadow(
            shadowJti = "shadow-jti-123",
            operatorId = "operator-id",
            targetMemberId = "marketer-id"
        )

        verify(redisTemplate, never()).delete(eq("shadow:target:marketer-id"))
    }

    @Test
    fun `invalidateShadowByTarget 시 관련 shadow-operator 키가 모두 삭제된다`() {
        whenever(setOps.members("shadow:target:marketer-id"))
            .thenReturn(setOf("operator-id-1", "operator-id-2"))

        shadowService.invalidateShadowByTarget("marketer-id")

        verify(redisTemplate).delete(eq("shadow:operator:operator-id-1"))
        verify(redisTemplate).delete(eq("shadow:operator:operator-id-2"))
        verify(redisTemplate).delete(eq("shadow:target:marketer-id"))
    }

    @Test
    fun `invalidateShadowByTarget 시 shadow-target 키가 없으면 아무것도 삭제하지 않는다`() {
        whenever(setOps.members("shadow:target:marketer-id")).thenReturn(null)

        shadowService.invalidateShadowByTarget("marketer-id")

        verify(redisTemplate, never()).delete(any<String>())
    }
}
