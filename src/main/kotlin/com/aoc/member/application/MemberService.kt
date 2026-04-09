package com.aoc.member.application

import com.aoc.auth.JwtProvider
import com.aoc.common.AocAccessDeniedException
import com.aoc.common.ErrorCode
import com.aoc.common.MemberNotFoundException
import com.aoc.common.MemberStatusException
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import com.aoc.member.domain.MemberStatus
import com.aoc.member.domain.Role
import com.aoc.member.infra.CognitoClaims
import com.aoc.member.presentation.dto.MemberResponse
import com.aoc.member.presentation.dto.MemberSummaryResponse
import com.aoc.notification.NotificationSetting
import com.aoc.notification.NotificationSettingRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
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

        // 동시 로그인 차단: 기존 session jti 블랙리스트 등록
        val oldJti = redisTemplate.opsForValue().get("session:${member.id}")
        if (oldJti != null) {
            redisTemplate.opsForValue().set("blacklist:$oldJti", "1", Duration.ofHours(1))
        }

        val accessToken = jwtProvider.generateToken(member.id, member.role, jti)
        val refreshToken = UUID.randomUUID().toString()

        redisTemplate.opsForValue().set("refresh:${member.id}", refreshToken, Duration.ofHours(4))
        redisTemplate.opsForValue().set("session:${member.id}", jti, Duration.ofHours(1))

        return LoginResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            isNewMember = isNewMember
        )
    }

    @Transactional(readOnly = true)
    fun getMe(userId: String): MemberResponse {
        val member = memberRepository.findById(userId).orElseThrow { MemberNotFoundException() }
        return MemberResponse.from(member)
    }

    @Transactional
    fun updateMe(userId: String, name: String) {
        val member = memberRepository.findById(userId).orElseThrow { MemberNotFoundException() }
        member.name = name
    }

    @Transactional
    fun deleteMe(userId: String) {
        val member = memberRepository.findById(userId).orElseThrow { MemberNotFoundException() }
        member.status = MemberStatus.PENDING_DELETION
        member.deletedAt = LocalDateTime.now()
    }

    @Transactional
    fun updateMemberRole(operatorId: String, targetId: String, role: Role) {
        if (operatorId == targetId) throw AocAccessDeniedException()

        val target = memberRepository.findById(targetId).orElseThrow { MemberNotFoundException() }
        target.role = role

        val jti = redisTemplate.opsForValue().get("session:${target.id}")
        if (jti != null) {
            redisTemplate.opsForValue().set("blacklist:$jti", "1", Duration.ofHours(1))
            redisTemplate.delete("session:${target.id}")
        }
    }

    @Transactional(readOnly = true)
    fun getMembers(role: Role?, status: MemberStatus?, pageable: Pageable): Page<MemberSummaryResponse> {
        val spec = Specification<Member> { root, _, cb ->
            val predicates = buildList {
                role?.let { add(cb.equal(root.get<Role>("role"), it)) }
                status?.let { add(cb.equal(root.get<MemberStatus>("status"), it)) }
            }
            cb.and(*predicates.toTypedArray())
        }
        return memberRepository.findAll(spec, pageable).map { MemberSummaryResponse.from(it) }
    }

    private fun findOrCreateMember(claims: CognitoClaims): Pair<Member, Boolean> {
        val existing = memberRepository.findByProviderAndProviderId(claims.provider, claims.sub)
        if (existing != null && existing.status != MemberStatus.DELETED) {
            validateMemberStatus(existing.status)
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

    private fun validateMemberStatus(status: MemberStatus) {
        when (status) {
            MemberStatus.ACTIVE -> return
            MemberStatus.DORMANT -> throw MemberStatusException(ErrorCode.MEMBER_DORMANT)
            MemberStatus.SUSPENDED -> throw MemberStatusException(ErrorCode.MEMBER_SUSPENDED)
            MemberStatus.SECURITY_LOCKOUT -> throw MemberStatusException(ErrorCode.MEMBER_SECURITY_LOCKOUT)
            MemberStatus.PENDING_DELETION -> throw MemberStatusException(ErrorCode.MEMBER_PENDING_DELETION)
            MemberStatus.DELETED -> return
        }
    }
}
