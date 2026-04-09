package com.aoc.member.application

import com.aoc.common.BusinessException
import com.aoc.common.ErrorCode
import com.aoc.member.domain.MemberRepository
import com.aoc.member.infra.EmailSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class EmailVerificationEdgeCaseTest {

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

    @Test
    fun `5회 실패해도 잠기지 않고 6번째에서 LOCKED 처리된다`() {
        whenever(valueOps.get("email-verify:user-id")).thenReturn("123456")

        // 5번째 시도: attempts=5 → 5 > 5 false → 코드 불일치 → INVALID
        whenever(valueOps.increment("email-verify:user-id:attempts")).thenReturn(5L)
        val ex5 = assertThrows<BusinessException> {
            emailVerificationService.confirmVerificationCode("user-id", "work@company.com", "000000")
        }
        assertEquals(ErrorCode.EMAIL_VERIFY_INVALID, ex5.errorCode)

        // 6번째 시도: attempts=6 → 6 > 5 true → LIMIT_EXCEEDED + locked 키 생성
        whenever(valueOps.increment("email-verify:user-id:attempts")).thenReturn(6L)
        val ex6 = assertThrows<BusinessException> {
            emailVerificationService.confirmVerificationCode("user-id", "work@company.com", "000000")
        }
        assertEquals(ErrorCode.EMAIL_VERIFY_LIMIT_EXCEEDED, ex6.errorCode)
        verify(valueOps).set(eq("email-verify:user-id:locked"), eq("locked"), eq(30L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `잠금 중 재발송 시도 시 EMAIL_VERIFY_LOCKED를 반환한다`() {
        whenever(redisTemplate.hasKey("email-verify:user-id:locked")).thenReturn(true)

        val ex = assertThrows<BusinessException> {
            emailVerificationService.sendVerificationCode("user-id", "work@company.com")
        }

        assertEquals(ErrorCode.EMAIL_VERIFY_LOCKED, ex.errorCode)
        verify(emailSender, never()).send(any(), any())
    }

    @Test
    fun `재발송 시 attempts 키는 건드리지 않는다`() {
        whenever(memberRepository.existsByWorkEmailAndIdNot(any(), any())).thenReturn(false)

        emailVerificationService.sendVerificationCode("user-id", "work@company.com")
        emailVerificationService.sendVerificationCode("user-id", "work@company.com")

        verify(valueOps, never()).set(eq("email-verify:user-id:attempts"), any(), any<Long>(), any())
        verify(valueOps, never()).set(eq("email-verify:user-id:attempts"), any(), any<java.time.Duration>())
    }

    @Test
    fun `잠금 해제 후 인증 코드 재발송이 가능하다`() {
        whenever(redisTemplate.hasKey("email-verify:user-id:locked"))
            .thenReturn(true)   // 첫 시도: 잠김
            .thenReturn(false)  // 두 번째 시도: 잠금 해제
        whenever(memberRepository.existsByWorkEmailAndIdNot(any(), any())).thenReturn(false)

        // 첫 시도: 잠금 → LOCKED
        val ex = assertThrows<BusinessException> {
            emailVerificationService.sendVerificationCode("user-id", "work@company.com")
        }
        assertEquals(ErrorCode.EMAIL_VERIFY_LOCKED, ex.errorCode)

        // 잠금 해제 후 재시도 → 정상 발송
        emailVerificationService.sendVerificationCode("user-id", "work@company.com")
        verify(emailSender).send(eq("work@company.com"), any())
    }
}
