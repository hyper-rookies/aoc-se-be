package com.aoc.auth

import com.aoc.common.ShadowActionNotAllowedException
import com.aoc.history.History
import com.aoc.history.HistoryAction
import com.aoc.history.HistoryRepository
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.Role
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.concurrent.TimeUnit

data class ShadowLoginResult(val shadowToken: String)

@Service
class ShadowService(
    private val memberRepository: MemberRepository,
    private val shadowJwtProvider: ShadowJwtProvider,
    private val historyRepository: HistoryRepository,
    private val redisTemplate: RedisTemplate<String, String>
) {

    @Transactional
    fun startShadow(operatorId: String, targetMemberId: String): ShadowLoginResult {
        if (operatorId == targetMemberId) {
            throw ShadowActionNotAllowedException()
        }

        val target = memberRepository.findById(targetMemberId)
            .orElseThrow { com.aoc.common.MemberNotFoundException() }

        if (target.role == Role.OPERATOR) {
            throw ShadowActionNotAllowedException()
        }

        // 기존 Shadow JWT 무효화
        val oldShadowJti = redisTemplate.opsForValue().get("shadow:operator:$operatorId")
        if (oldShadowJti != null) {
            redisTemplate.opsForValue().set("blacklist:$oldShadowJti", "1", Duration.ofMinutes(30))
            redisTemplate.delete("shadow:operator:$operatorId")
        }

        val shadowToken = shadowJwtProvider.generateToken(
            userId = target.id,
            role = target.role,
            operatorId = operatorId,
            targetName = target.name,
            targetWorkEmail = target.workEmail
        )
        val shadowClaims = shadowJwtProvider.validateToken(shadowToken)

        redisTemplate.opsForValue().set("shadow:operator:$operatorId", shadowClaims.jti, Duration.ofMinutes(30))
        redisTemplate.opsForSet().add("shadow:target:$targetMemberId", operatorId)
        redisTemplate.expire("shadow:target:$targetMemberId", 30, TimeUnit.MINUTES)

        historyRepository.save(
            History(
                entityType = "Member",
                entityId = targetMemberId,
                action = HistoryAction.SHADOW_LOGIN_START,
                actorId = targetMemberId,
                operatorId = operatorId,
                shadowId = shadowClaims.jti,
                isShadow = true
            )
        )

        return ShadowLoginResult(shadowToken = shadowToken)
    }

    @Transactional
    fun endShadow(shadowJti: String, operatorId: String, targetMemberId: String) {
        redisTemplate.opsForValue().set("blacklist:$shadowJti", "1", Duration.ofMinutes(30))
        redisTemplate.delete("shadow:operator:$operatorId")
        redisTemplate.opsForSet().remove("shadow:target:$targetMemberId", operatorId)
        val remaining = redisTemplate.opsForSet().size("shadow:target:$targetMemberId") ?: 0L
        if (remaining == 0L) {
            redisTemplate.delete("shadow:target:$targetMemberId")
        }

        historyRepository.save(
            History(
                entityType = "Member",
                entityId = targetMemberId,
                action = HistoryAction.SHADOW_LOGIN_END,
                actorId = targetMemberId,
                operatorId = operatorId,
                shadowId = shadowJti,
                isShadow = true
            )
        )
    }

    fun invalidateShadowByTarget(targetId: String) {
        val key = "shadow:target:$targetId"
        val operatorIds = redisTemplate.opsForSet().members(key) ?: return
        operatorIds.forEach { operatorId ->
            redisTemplate.delete("shadow:operator:$operatorId")
        }
        redisTemplate.delete(key)
    }
}
