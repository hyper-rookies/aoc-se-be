package com.aoc.member.application

import com.aoc.common.BusinessException
import com.aoc.common.ErrorCode
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.Role
import com.aoc.member.infra.EmailSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class EmailVerificationServiceTest {

    private lateinit var memberRepository: MemberRepository
    private lateinit var emailSender: EmailSender
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var emailVerificationService: EmailVerificationService

    @BeforeEach
    fun setUp() {
        memberRepository = mock()
        emailSender = mock()
        redisTemplate = mock()
        valueOps = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(redisTemplate.hasKey(any())).thenReturn(false)

        emailVerificationService = EmailVerificationService(memberRepository, emailSender, redisTemplate)
    }

    // ── sendVerificationCode ─────────────────────────────────────────────────

    @Test
    fun `정상 발송 시 Redis에 인증 코드가 저장되고 이메일이 발송된다`() {
        whenever(memberRepository.existsByWorkEmailAndIdNot(any(), any())).thenReturn(false)

        emailVerificationService.sendVerificationCode("user-id", "work@company.com")

        verify(valueOps).set(eq("email-verify:user-id"), any(), eq(5L), eq(TimeUnit.MINUTES))
        verify(emailSender).send(eq("work@company.com"), any())
    }

    @Test
    fun `이미 다른 회원에게 등록된 workEmail이면 EMAIL_ALREADY_USED 예외를 던진다`() {
        whenever(memberRepository.existsByWorkEmailAndIdNot("work@company.com", "user-id")).thenReturn(true)

        val ex = assertThrows<BusinessException> {
            emailVerificationService.sendVerificationCode("user-id", "work@company.com")
        }

        assertEquals(ErrorCode.EMAIL_ALREADY_USED, ex.errorCode)
    }

    @Test
    fun `재발송 시 코드만 갱신되고 attempts 키는 건드리지 않는다`() {
        whenever(memberRepository.existsByWorkEmailAndIdNot(any(), any())).thenReturn(false)

        emailVerificationService.sendVerificationCode("user-id", "work@company.com")
        emailVerificationService.sendVerificationCode("user-id", "work@company.com")

        verify(valueOps, times(2)).set(eq("email-verify:user-id"), any(), eq(5L), eq(TimeUnit.MINUTES))
        verify(valueOps, never()).set(eq("email-verify:user-id:attempts"), any(), any<Long>(), any())
    }

    @Test
    fun `잠금 상태에서 재발송 시도 시 EMAIL_VERIFY_LOCKED 예외를 던진다`() {
        whenever(redisTemplate.hasKey("email-verify:user-id:locked")).thenReturn(true)

        val ex = assertThrows<BusinessException> {
            emailVerificationService.sendVerificationCode("user-id", "work@company.com")
        }

        assertEquals(ErrorCode.EMAIL_VERIFY_LOCKED, ex.errorCode)
        verify(emailSender, never()).send(any(), any())
    }

    // ── confirmVerificationCode ──────────────────────────────────────────────

    @Test
    fun `정상 코드 입력 시 workEmail이 업데이트되고 Redis 키가 삭제된다`() {
        val member = Member(
            email = "m@test.com", name = "유저", provider = "GOOGLE", providerId = "g-1", role = Role.MARKETER
        )
        whenever(valueOps.get("email-verify:${member.id}")).thenReturn("123456")
        whenever(valueOps.increment("email-verify:${member.id}:attempts")).thenReturn(1L)
        whenever(memberRepository.findById(member.id)).thenReturn(Optional.of(member))

        emailVerificationService.confirmVerificationCode(member.id, "work@company.com", "123456")

        assertEquals("work@company.com", member.workEmail)
        verify(redisTemplate).delete(eq("email-verify:${member.id}"))
        verify(redisTemplate).delete(eq("email-verify:${member.id}:attempts"))
    }

    @Test
    fun `코드 불일치 시 EMAIL_VERIFY_INVALID 예외를 던지고 시도 횟수가 증가한다`() {
        whenever(valueOps.get("email-verify:user-id")).thenReturn("123456")
        whenever(valueOps.increment("email-verify:user-id:attempts")).thenReturn(1L)

        val ex = assertThrows<BusinessException> {
            emailVerificationService.confirmVerificationCode("user-id", "work@company.com", "000000")
        }

        assertEquals(ErrorCode.EMAIL_VERIFY_INVALID, ex.errorCode)
        verify(valueOps).increment("email-verify:user-id:attempts")
    }

    @Test
    fun `시도 횟수가 5회 초과되면 locked 키가 생성되고 EMAIL_VERIFY_LIMIT_EXCEEDED 예외를 던진다`() {
        whenever(valueOps.get("email-verify:user-id")).thenReturn("123456")
        whenever(valueOps.increment("email-verify:user-id:attempts")).thenReturn(6L)

        val ex = assertThrows<BusinessException> {
            emailVerificationService.confirmVerificationCode("user-id", "work@company.com", "123456")
        }

        assertEquals(ErrorCode.EMAIL_VERIFY_LIMIT_EXCEEDED, ex.errorCode)
        verify(valueOps).set(eq("email-verify:user-id:locked"), eq("locked"), eq(30L), eq(TimeUnit.MINUTES))
        verify(redisTemplate).delete(eq("email-verify:user-id"))
        verify(redisTemplate).delete(eq("email-verify:user-id:attempts"))
    }

    @Test
    fun `잠금 상태에서 confirm 시도 시 EMAIL_VERIFY_LOCKED 예외를 던진다`() {
        whenever(redisTemplate.hasKey("email-verify:user-id:locked")).thenReturn(true)

        val ex = assertThrows<BusinessException> {
            emailVerificationService.confirmVerificationCode("user-id", "work@company.com", "123456")
        }

        assertEquals(ErrorCode.EMAIL_VERIFY_LOCKED, ex.errorCode)
    }

    @Test
    fun `TTL 만료로 코드가 없으면 EMAIL_VERIFY_EXPIRED 예외를 던진다`() {
        whenever(valueOps.get("email-verify:user-id")).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            emailVerificationService.confirmVerificationCode("user-id", "work@company.com", "123456")
        }

        assertEquals(ErrorCode.EMAIL_VERIFY_EXPIRED, ex.errorCode)
    }

    @Test
    fun `성공 후 재시도 시 Redis 키가 삭제되어 EMAIL_VERIFY_EXPIRED 예외를 던진다`() {
        val member = Member(
            email = "m@test.com", name = "유저", provider = "GOOGLE", providerId = "g-1", role = Role.MARKETER
        )
        whenever(valueOps.get("email-verify:${member.id}"))
            .thenReturn("123456")
            .thenReturn(null)
        whenever(valueOps.increment("email-verify:${member.id}:attempts")).thenReturn(1L)
        whenever(memberRepository.findById(member.id)).thenReturn(Optional.of(member))

        emailVerificationService.confirmVerificationCode(member.id, "work@company.com", "123456")

        val ex = assertThrows<BusinessException> {
            emailVerificationService.confirmVerificationCode(member.id, "work@company.com", "123456")
        }
        assertEquals(ErrorCode.EMAIL_VERIFY_EXPIRED, ex.errorCode)
    }
}
