package com.aoc.auth

import com.aoc.common.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ShadowLoginRequest(val targetMemberId: String)

@RestController
@RequestMapping("/shadow-login")
class ShadowController(
    private val shadowService: ShadowService
) {

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    fun startShadowLogin(@RequestBody request: ShadowLoginRequest): ApiResponse<ShadowLoginResult> {
        val actor = ActorContext.get()!!
        val result = shadowService.startShadow(
            operatorId = actor.actorId,
            targetMemberId = request.targetMemberId
        )
        return ApiResponse.ok(result)
    }

    @DeleteMapping
    @PreAuthorize("@actorContext.isShadow()")
    fun endShadowLogin(): ApiResponse<Nothing?> {
        val actor = ActorContext.get()!!
        shadowService.endShadow(
            shadowJti = actor.shadowId!!,
            operatorId = actor.operatorId!!,
            targetMemberId = actor.actorId
        )
        return ApiResponse.ok(null)
    }
}
