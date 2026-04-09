package com.aoc.member.presentation.dto

import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberStatus
import com.aoc.member.domain.Role
import java.time.LocalDateTime

data class MemberResponse(
    val name: String,
    val workEmail: String?,
    val role: Role,
    val status: MemberStatus,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(member: Member) = MemberResponse(
            name = member.name,
            workEmail = member.workEmail,
            role = member.role,
            status = member.status,
            createdAt = member.createdAt
        )
    }
}
