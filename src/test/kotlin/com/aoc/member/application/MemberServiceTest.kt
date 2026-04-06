package com.aoc.member.application

import com.aoc.auth.JwtProvider
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.Role
import com.aoc.member.infra.CognitoClaims
import com.aoc.notification.NotificationSetting
import com.aoc.notification.NotificationSettingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
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
    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberRepository = mock()
        notificationSettingRepository = mock()
        jwtProvider = mock()
        redisTemplate = mock()
        valueOps = mock()

        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(jwtProvider.generateToken(any(), any(), any())).thenReturn("mock-access-token")

        memberService = MemberService(memberRepository, notificationSettingRepository, jwtProvider, redisTemplate)
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

        val result = memberService.loginOrRegister(claims)

        assertFalse(result.isNewMember)
        assertEquals("mock-access-token", result.accessToken)
        assertNotNull(result.refreshToken)

        verify(memberRepository, never()).save(any<Member>())
        verify(notificationSettingRepository, never()).save(any<NotificationSetting>())
    }
}
