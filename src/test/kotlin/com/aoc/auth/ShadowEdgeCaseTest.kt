package com.aoc.auth

import com.aoc.history.History
import com.aoc.history.HistoryRepository
import com.aoc.member.application.MemberService
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.Role
import com.aoc.notification.NotificationSettingRepository
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

class ShadowEdgeCaseTest {

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
        whenever(valueOps.get(any<String>())).thenReturn(null)
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }

        shadowService = ShadowService(memberRepository, shadowJwtProvider, historyRepository, redisTemplate)
    }

    private fun makeTarget() = Member(
        email = "target@test.com", name = "대상", provider = "GOOGLE", providerId = "google-t", role = Role.MARKETER
    )

    private fun stubShadow(operatorId: String, token: String, jti: String, target: Member) {
        whenever(shadowJwtProvider.generateToken(any(), any(), eq(operatorId), any(), anyOrNull()))
            .thenReturn(token)
        whenever(shadowJwtProvider.validateToken(token)).thenReturn(
            ShadowClaims(
                jti = jti, userId = target.id, role = Role.MARKETER,
                operatorId = operatorId, targetName = target.name, targetWorkEmail = null
            )
        )
    }

    @Test
    fun `두 운영자 A B가 동일 대상에 쉐도우 시작 시 shadow-target Set에 둘 다 등록된다`() {
        val target = makeTarget()
        whenever(memberRepository.findById("target-id")).thenReturn(Optional.of(target))
        stubShadow("operator-a", "token-a", "jti-a", target)
        stubShadow("operator-b", "token-b", "jti-b", target)

        shadowService.startShadow("operator-a", "target-id")
        shadowService.startShadow("operator-b", "target-id")

        verify(setOps).add(eq("shadow:target:target-id"), eq("operator-a"))
        verify(setOps).add(eq("shadow:target:target-id"), eq("operator-b"))
    }

    @Test
    fun `대상 계정 상태 변경 시 A B 쉐도우가 모두 무효화된다`() {
        whenever(setOps.members("shadow:target:target-id"))
            .thenReturn(setOf("operator-a", "operator-b"))

        shadowService.invalidateShadowByTarget("target-id")

        verify(redisTemplate).delete(eq("shadow:operator:operator-a"))
        verify(redisTemplate).delete(eq("shadow:operator:operator-b"))
        verify(redisTemplate).delete(eq("shadow:target:target-id"))
    }

    @Test
    fun `대상 계정 role 변경 시 쉐도우가 무효화된다`() {
        val target = makeTarget()
        val shadowSvcMock: ShadowService = mock()
        val memberRepo: MemberRepository = mock()
        val valueOpsMock: ValueOperations<String, String> = mock()
        val redisMock: RedisTemplate<String, String> = mock()
        whenever(memberRepo.findById(target.id)).thenReturn(Optional.of(target))
        whenever(redisMock.opsForValue()).thenReturn(valueOpsMock)
        whenever(valueOpsMock.get(any<String>())).thenReturn(null)

        val memberService = MemberService(
            memberRepo, mock<NotificationSettingRepository>(), mock<JwtProvider>(),
            redisMock, mock<HistoryRepository>(), shadowSvcMock
        )
        memberService.updateMemberRole("operator-id", target.id, Role.AGENCY_MANAGER)

        verify(shadowSvcMock).invalidateShadowByTarget(eq(target.id))
    }

    @Test
    fun `운영자 A 쉐도우 종료 시 B가 남아있으면 shadow-target 키가 유지된다`() {
        whenever(setOps.size("shadow:target:target-id")).thenReturn(1L)

        shadowService.endShadow("jti-a", "operator-a", "target-id")

        verify(setOps).remove(eq("shadow:target:target-id"), eq("operator-a"))
        verify(redisTemplate, never()).delete(eq("shadow:target:target-id"))
    }
}
