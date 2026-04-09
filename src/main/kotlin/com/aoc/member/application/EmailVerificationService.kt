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
import kotlin.random.Random

@Service
class EmailVerificationService(
    private val memberRepository: MemberRepository,
    private val emailSender: EmailSender,
    private val redisTemplate: RedisTemplate<String, String>
) {

    companion object {
        private const val CODE_TTL_MINUTES = 5L
        private const val MAX_ATTEMPTS = 5
    }

    fun sendVerificationCode(userId: String, workEmail: String) {
        if (memberRepository.existsByWorkEmailAndIdNot(workEmail, userId)) {
            throw BusinessException(ErrorCode.EMAIL_ALREADY_USED)
        }

        val code = String.format("%06d", Random.nextInt(1_000_000))
        val ttl = Duration.ofMinutes(CODE_TTL_MINUTES)

        redisTemplate.opsForValue().set("email-verify:$userId", code, ttl)
        redisTemplate.opsForValue().set("email-verify:$userId:attempts", "0", ttl)

        emailSender.send(workEmail, code)
    }

    @Transactional
    fun confirmVerificationCode(userId: String, workEmail: String, code: String) {
        val storedCode = redisTemplate.opsForValue().get("email-verify:$userId")
            ?: throw BusinessException(ErrorCode.EMAIL_VERIFY_EXPIRED)

        val attempts = redisTemplate.opsForValue().get("email-verify:$userId:attempts")?.toIntOrNull() ?: 0
        if (attempts >= MAX_ATTEMPTS) {
            throw BusinessException(ErrorCode.EMAIL_VERIFY_LIMIT_EXCEEDED)
        }

        redisTemplate.opsForValue().increment("email-verify:$userId:attempts")

        if (storedCode != code) {
            throw BusinessException(ErrorCode.EMAIL_VERIFY_INVALID)
        }

        val member = memberRepository.findById(userId).orElseThrow { MemberNotFoundException() }
        member.workEmail = workEmail

        redisTemplate.delete("email-verify:$userId")
        redisTemplate.delete("email-verify:$userId:attempts")
    }
}
