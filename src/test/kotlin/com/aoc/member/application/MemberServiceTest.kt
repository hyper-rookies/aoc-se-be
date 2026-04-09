package com.aoc.member.application

import com.aoc.auth.JwtProvider
import com.aoc.auth.ShadowService
import com.aoc.common.AocAccessDeniedException
import com.aoc.common.BusinessException
import com.aoc.common.ErrorCode
import com.aoc.history.History
import com.aoc.history.HistoryAction
import com.aoc.history.HistoryRepository
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.MemberStatus
import com.aoc.member.domain.Role
import com.aoc.member.infra.CognitoClaims
import com.aoc.notification.NotificationSetting
import com.aoc.notification.NotificationSettingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemberServiceTest {

    private lateinit var memberRepository: MemberRepository
    private lateinit var notificationSettingRepository: NotificationSettingRepository
    private lateinit var jwtProvider: JwtProvider
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var historyRepository: HistoryRepository
    private lateinit var shadowService: ShadowService
    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberRepository = mock()
        notificationSettingRepository = mock()
        jwtProvider = mock()
        redisTemplate = mock()
        valueOps = mock()
        historyRepository = mock()
        shadowService = mock()

        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(jwtProvider.generateToken(any(), any(), any())).thenReturn("mock-access-token")

        memberService = MemberService(
            memberRepository, notificationSettingRepository, jwtProvider, redisTemplate, historyRepository, shadowService
        )
    }

    @Test
    fun `신규 회원은 Member와 NotificationSetting이 함께 생성된다`() {
        val claims = CognitoClaims(sub = "google-sub-123", email = "new@example.com", name = "신규 유저")
        val savedMember = Member(
            email = claims.email,
            name = claims.name,
            provider = claims.provider,
            providerId = claims.sub,
            role = Role.MARKETER
        )

        whenever(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(null)
        whenever(memberRepository.save(any<Member>())).thenReturn(savedMember)
        whenever(notificationSettingRepository.save(any<NotificationSetting>())).thenAnswer { it.arguments[0] }
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }

        val result = memberService.loginOrRegister(claims)

        assertTrue(result.isNewMember)
        assertEquals("mock-access-token", result.accessToken)
        assertNotNull(result.refreshToken)

        verify(memberRepository).save(any<Member>())
        verify(notificationSettingRepository).save(any<NotificationSetting>())
    }

    @Test
    fun `기존 회원은 DB 조회만 하고 새 레코드를 생성하지 않는다`() {
        val existingMember = Member(
            email = "existing@example.com",
            name = "기존 유저",
            provider = "GOOGLE",
            providerId = "google-sub-existing",
            role = Role.MARKETER
        )
        val claims = CognitoClaims(
            sub = "google-sub-existing",
            email = "existing@example.com",
            name = "기존 유저"
        )

        whenever(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(existingMember)
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }

        val result = memberService.loginOrRegister(claims)

        assertFalse(result.isNewMember)
        assertEquals("mock-access-token", result.accessToken)
        assertNotNull(result.refreshToken)

        verify(memberRepository, never()).save(any<Member>())
        verify(notificationSettingRepository, never()).save(any<NotificationSetting>())
    }

    @Test
    fun `로그인 시 LOGIN 히스토리가 기록된다`() {
        val member = Member(
            email = "login@example.com",
            name = "로그인 유저",
            provider = "GOOGLE",
            providerId = "google-sub-login",
            role = Role.MARKETER
        )
        val claims = CognitoClaims(sub = "google-sub-login", email = "login@example.com", name = "로그인 유저")

        whenever(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(member)
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }

        memberService.loginOrRegister(claims)

        val captor = argumentCaptor<History>()
        verify(historyRepository).save(captor.capture())

        val saved = captor.firstValue
        assertEquals(HistoryAction.LOGIN, saved.action)
        assertEquals("Member", saved.entityType)
        assertEquals(member.id, saved.entityId)
        assertEquals(member.id, saved.actorId)
        assertFalse(saved.isShadow)
        assertTrue(saved.afterValue!!.contains("GOOGLE"))
        assertTrue(saved.afterValue!!.contains("loginAt"))
    }

    @Test
    fun `deleteMe는 회원 상태를 PENDING_DELETION으로 전환하고 삭제 시각을 기록한다`() {
        val member = Member(
            email = "marketer@test.com",
            name = "마케터",
            provider = "GOOGLE",
            providerId = "google-sub",
            role = Role.MARKETER
        )
        whenever(memberRepository.findById(member.id)).thenReturn(Optional.of(member))

        memberService.deleteMe(member.id)

        assertEquals(MemberStatus.PENDING_DELETION, member.status)
        assertNotNull(member.deletedAt)
    }

    @Test
    fun `updateMemberRole은 역할 변경 후 대상 회원의 session Redis 키를 삭제한다`() {
        val target = Member(
            email = "target@test.com",
            name = "대상마케터",
            provider = "GOOGLE",
            providerId = "google-target",
            role = Role.MARKETER
        )
        whenever(memberRepository.findById(target.id)).thenReturn(Optional.of(target))
        whenever(valueOps.get("session:${target.id}")).thenReturn("existing-jti")

        memberService.updateMemberRole("operator-id", target.id, Role.AGENCY_MANAGER)

        assertEquals(Role.AGENCY_MANAGER, target.role)
        verify(redisTemplate).delete(eq("session:${target.id}"))
        verify(shadowService).invalidateShadowByTarget(eq(target.id))
    }

    @Test
    fun `updateMemberRole 시 operatorId와 targetId가 동일하면 AocAccessDeniedException을 던진다`() {
        assertThrows<AocAccessDeniedException> {
            memberService.updateMemberRole("same-id", "same-id", Role.AGENCY_MANAGER)
        }
    }

    @Test
    fun `updateMemberStatus는 상태 변경 후 invalidateShadowByTarget을 호출한다`() {
        val target = Member(
            email = "target@test.com",
            name = "대상마케터",
            provider = "GOOGLE",
            providerId = "google-target",
            role = Role.MARKETER
        )
        whenever(memberRepository.findById(target.id)).thenReturn(Optional.of(target))
        whenever(memberRepository.save(any<Member>())).thenReturn(target)

        memberService.updateMemberStatus("operator-id", target.id, MemberStatus.SUSPENDED)

        assertEquals(MemberStatus.SUSPENDED, target.status)
        verify(shadowService).invalidateShadowByTarget(eq(target.id))
    }

    @Test
    fun `updateMemberStatus 시 operatorId와 targetId가 동일하면 AocAccessDeniedException을 던진다`() {
        assertThrows<AocAccessDeniedException> {
            memberService.updateMemberStatus("same-id", "same-id", MemberStatus.SUSPENDED)
        }
    }

    @Test
    fun `Redis 저장 실패 시 BusinessException INTERNAL_SERVER_ERROR를 던진다`() {
        val claims = CognitoClaims(sub = "google-sub-redis", email = "redis@example.com", name = "레디스유저")
        val member = Member(
            email = claims.email,
            name = claims.name,
            provider = claims.provider,
            providerId = claims.sub,
            role = Role.MARKETER
        )
        whenever(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(member)
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }
        whenever(valueOps.set(any(), any(), any<java.time.Duration>()))
            .thenThrow(RuntimeException("Redis 연결 실패"))

        val ex = assertThrows<BusinessException> {
            memberService.loginOrRegister(claims)
        }

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, ex.errorCode)
        verify(jwtProvider).generateToken(any(), any(), any())
    }
}
