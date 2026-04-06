package com.aoc.member.domain

import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, String> {
    fun findByEmail(email: String): Member?
    fun findByProviderAndProviderId(provider: String, providerId: String): Member?
}
