package com.aoc.member.application

import com.aoc.auth.JwtProvider
import com.aoc.auth.ShadowService
import com.aoc.common.ErrorCode
import com.aoc.common.MemberStatusException
import com.aoc.history.History
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberStatusTest {

    private lateinit var memberRepository: MemberRepository
    private lateinit var notificationSettingRepository: NotificationSettingRepository
    private lateinit var jwtProvider: JwtProvider
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var historyRepository: HistoryRepository
    private lateinit var shadowService: ShadowService
    private lateinit var memberService: MemberService

    private val claims = CognitoClaims(sub = "google-sub-001", email = "user@example.com", name = "테스트유저")

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
        whenever(jwtProvider.generateToken(any(), any(), any())).thenReturn("mock-token")
        whenever(historyRepository.save(any<History>())).thenAnswer { it.arguments[0] }

        memberService = MemberService(
            memberRepository, notificationSettingRepository, jwtProvider, redisTemplate, historyRepository, shadowService
        )
    }

    private fun memberWithStatus(status: MemberStatus) = Member(
        email = claims.email,
        name = claims.name,
        provider = claims.provider,
        providerId = claims.sub,
        role = Role.MARKETER,
        status = status
    )

    @Test
    fun `DORMANT 계정 로그인 시 MEMBER_DORMANT 에러와 메시지를 반환한다`() {
        whenever(memberRepository.findByProviderAndProviderId(any(), any()))
            .thenReturn(memberWithStatus(MemberStatus.DORMANT))

        val ex = assertThrows<MemberStatusException> {
            memberService.loginOrRegister(claims)
        }

        assertEquals(ErrorCode.MEMBER_DORMANT, ex.errorCode)
        assertEquals("휴면 계정입니다. 고객센터에 문의해주세요.", ex.message)
    }

    @Test
    fun `SUSPENDED 계정 로그인 시 MEMBER_SUSPENDED 에러와 메시지를 반환한다`() {
        whenever(memberRepository.findByProviderAndProviderId(any(), any()))
            .thenReturn(memberWithStatus(MemberStatus.SUSPENDED))

        val ex = assertThrows<MemberStatusException> {
            memberService.loginOrRegister(claims)
        }

        assertEquals(ErrorCode.MEMBER_SUSPENDED, ex.errorCode)
        assertEquals("정지된 계정입니다. 고객센터에 문의해주세요.", ex.message)
    }

    @Test
    fun `SECURITY_LOCKOUT 계정 로그인 시 MEMBER_SECURITY_LOCKOUT 에러와 메시지를 반환한다`() {
        whenever(memberRepository.findByProviderAndProviderId(any(), any()))
            .thenReturn(memberWithStatus(MemberStatus.SECURITY_LOCKOUT))

        val ex = assertThrows<MemberStatusException> {
            memberService.loginOrRegister(claims)
        }

        assertEquals(ErrorCode.MEMBER_SECURITY_LOCKOUT, ex.errorCode)
        assertEquals("보안 잠금 상태입니다. 잠시 후 다시 시도해주세요.", ex.message)
    }

    @Test
    fun `PENDING_DELETION 계정 로그인 시 MEMBER_PENDING_DELETION 에러와 메시지를 반환한다`() {
        whenever(memberRepository.findByProviderAndProviderId(any(), any()))
            .thenReturn(memberWithStatus(MemberStatus.PENDING_DELETION))

        val ex = assertThrows<MemberStatusException> {
            memberService.loginOrRegister(claims)
        }

        assertEquals(ErrorCode.MEMBER_PENDING_DELETION, ex.errorCode)
        assertEquals("탈퇴 처리 중인 계정입니다.", ex.message)
    }

    @Test
    fun `DELETED 계정은 신규 가입으로 처리되어 ACTIVE 상태의 새 Member가 생성된다`() {
        val newMember = Member(
            email = claims.email,
            name = claims.name,
            provider = claims.provider,
            providerId = claims.sub,
            role = Role.MARKETER
        )
        whenever(memberRepository.findByProviderAndProviderId(any(), any()))
            .thenReturn(memberWithStatus(MemberStatus.DELETED))
        whenever(memberRepository.save(any<Member>())).thenReturn(newMember)
        whenever(notificationSettingRepository.save(any<NotificationSetting>())).thenAnswer { it.arguments[0] }

        val result = memberService.loginOrRegister(claims)

        assertTrue(result.isNewMember)
        assertEquals(MemberStatus.ACTIVE, newMember.status)
        verify(memberRepository).save(any<Member>())
        verify(notificationSettingRepository).save(any<NotificationSetting>())
    }
}
