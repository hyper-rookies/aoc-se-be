package com.aoc.member.application

import com.aoc.common.BusinessException
import com.aoc.common.ErrorCode
import com.aoc.common.MemberNotFoundException
import com.aoc.member.domain.MemberRepository
import com.aoc.member.infra.EmailSender
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
class EmailVerificationService(
    private val memberRepository: MemberRepository,
    private val emailSender: EmailSender,
    private val redisTemplate: RedisTemplate<String, String>
) {

    companion object {
        private const val CODE_TTL_MINUTES = 5L
        private const val LOCK_TTL_MINUTES = 30L
        private const val MAX_ATTEMPTS = 5L
    }

    fun sendVerificationCode(userId: String, workEmail: String) {
        if (redisTemplate.hasKey("email-verify:$userId:locked") == true) {
            throw BusinessException(ErrorCode.EMAIL_VERIFY_LOCKED)
        }

        if (memberRepository.existsByWorkEmailAndIdNot(workEmail, userId)) {
            throw BusinessException(ErrorCode.EMAIL_ALREADY_USED)
        }

        val code = String.format("%06d", Random.nextInt(1_000_000))
        redisTemplate.opsForValue().set("email-verify:$userId", code, CODE_TTL_MINUTES, TimeUnit.MINUTES)

        emailSender.send(workEmail, code)
    }

    @Transactional
    fun confirmVerificationCode(userId: String, workEmail: String, code: String) {
        if (redisTemplate.hasKey("email-verify:$userId:locked") == true) {
            throw BusinessException(ErrorCode.EMAIL_VERIFY_LOCKED)
        }

        val storedCode = redisTemplate.opsForValue().get("email-verify:$userId")
            ?: throw BusinessException(ErrorCode.EMAIL_VERIFY_EXPIRED)

        val attempts = redisTemplate.opsForValue().increment("email-verify:$userId:attempts") ?: 1L

        if (attempts > MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set("email-verify:$userId:locked", "locked", LOCK_TTL_MINUTES, TimeUnit.MINUTES)
            redisTemplate.delete("email-verify:$userId")
            redisTemplate.delete("email-verify:$userId:attempts")
            throw BusinessException(ErrorCode.EMAIL_VERIFY_LIMIT_EXCEEDED)
        }

        if (storedCode != code) {
            throw BusinessException(ErrorCode.EMAIL_VERIFY_INVALID)
        }

        val member = memberRepository.findById(userId).orElseThrow { MemberNotFoundException() }
        member.workEmail = workEmail

        redisTemplate.delete("email-verify:$userId")
        redisTemplate.delete("email-verify:$userId:attempts")
    }
}
