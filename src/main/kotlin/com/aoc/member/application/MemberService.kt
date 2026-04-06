package com.aoc.member.application

import com.aoc.auth.JwtProvider
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.Role
import com.aoc.member.infra.CognitoClaims
import com.aoc.notification.NotificationSetting
import com.aoc.notification.NotificationSettingRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

data class LoginResult(
    val accessToken: String,
    val refreshToken: String,
    val isNewMember: Boolean
)

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val jwtProvider: JwtProvider,
    private val redisTemplate: RedisTemplate<String, String>
) {

    @Transactional
    fun loginOrRegister(cognitoClaims: CognitoClaims): LoginResult {
        val (member, isNewMember) = findOrCreateMember(cognitoClaims)

        val jti = UUID.randomUUID().toString()
        val accessToken = jwtProvider.generateToken(member.id, member.role, jti)
        val refreshToken = UUID.randomUUID().toString()

        redisTemplate.opsForValue().set(
            "refresh:${member.id}",
            refreshToken,
            Duration.ofHours(4)
        )

        return LoginResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            isNewMember = isNewMember
        )
    }

    private fun findOrCreateMember(claims: CognitoClaims): Pair<Member, Boolean> {
        val existing = memberRepository.findByProviderAndProviderId(claims.provider, claims.sub)
        if (existing != null) {
            return Pair(existing, false)
        }

        val newMember = memberRepository.save(
            Member(
                email = claims.email,
                name = claims.name,
                provider = claims.provider,
                providerId = claims.sub,
                role = Role.MARKETER
            )
        )

        notificationSettingRepository.save(NotificationSetting(memberId = newMember.id))

        return Pair(newMember, true)
    }
}
