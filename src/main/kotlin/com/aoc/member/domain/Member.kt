package com.aoc.member.domain

import com.aoc.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

enum class Role { MARKETER, AGENCY_MANAGER, OPERATOR }
enum class MemberStatus { ACTIVE, DELETED }

@Entity
@Table(name = "member")
class Member(
    var email: String,
    var name: String,
    var provider: String,
    var providerId: String,
    var workEmail: String? = null,
    @Enumerated(EnumType.STRING)
    var role: Role = Role.MARKETER,
    @Enumerated(EnumType.STRING)
    var status: MemberStatus = MemberStatus.ACTIVE
) : BaseEntity()
