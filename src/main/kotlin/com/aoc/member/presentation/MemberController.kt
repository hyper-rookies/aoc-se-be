package com.aoc.member.presentation

import com.aoc.auth.ActorContext
import com.aoc.common.ApiResponse
import com.aoc.member.application.EmailVerificationService
import com.aoc.member.application.MemberService
import com.aoc.member.domain.MemberStatus
import com.aoc.member.domain.Role
import com.aoc.member.presentation.dto.MemberResponse
import com.aoc.member.presentation.dto.MemberSummaryResponse
import com.aoc.member.presentation.dto.UpdateMemberRequest
import com.aoc.member.presentation.dto.UpdateRoleRequest
import com.aoc.member.presentation.dto.WorkEmailConfirmRequest
import com.aoc.member.presentation.dto.WorkEmailVerifyRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/members")
class MemberController(
    private val memberService: MemberService,
    private val emailVerificationService: EmailVerificationService
) {

    @GetMapping("/me")
    fun getMe(): ResponseEntity<ApiResponse<MemberResponse>> {
        val userId = ActorContext.get()!!.actorId
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMe(userId)))
    }

    @PutMapping("/me")
    @PreAuthorize("!@actorContext.isShadow()")
    fun updateMe(@Valid @RequestBody req: UpdateMemberRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = ActorContext.get()!!.actorId
        memberService.updateMe(userId, req.name)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('MARKETER') and !@actorContext.isShadow()")
    fun deleteMe(): ResponseEntity<ApiResponse<Unit>> {
        val userId = ActorContext.get()!!.actorId
        memberService.deleteMe(userId)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('OPERATOR')")
    fun updateMemberRole(
        @PathVariable id: String,
        @Valid @RequestBody req: UpdateRoleRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val operatorId = ActorContext.get()!!.actorId
        memberService.updateMemberRole(operatorId, id, req.role)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    @PostMapping("/me/work-email/verify")
    @PreAuthorize("!@actorContext.isShadow()")
    fun sendWorkEmailVerification(@Valid @RequestBody req: WorkEmailVerifyRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = ActorContext.get()!!.actorId
        emailVerificationService.sendVerificationCode(userId, req.workEmail)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    @PostMapping("/me/work-email/confirm")
    @PreAuthorize("!@actorContext.isShadow()")
    fun confirmWorkEmail(@Valid @RequestBody req: WorkEmailConfirmRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = ActorContext.get()!!.actorId
        emailVerificationService.confirmVerificationCode(userId, req.workEmail, req.code)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    @GetMapping
    @PreAuthorize("hasRole('OPERATOR')")
    fun getMembers(
        @RequestParam role: Role? = null,
        @RequestParam status: MemberStatus? = null,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<MemberSummaryResponse>>> {
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMembers(role, status, pageable)))
    }
}
