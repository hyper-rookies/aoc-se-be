package com.aoc.member.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface MemberRepository : JpaRepository<Member, String>, JpaSpecificationExecutor<Member> {
    fun findByEmail(email: String): Member?
    fun findByProviderAndProviderId(provider: String, providerId: String): Member?
    fun existsByWorkEmailAndIdNot(workEmail: String, id: String): Boolean
}
