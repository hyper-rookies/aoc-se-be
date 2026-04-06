package com.aoc.auth

import com.aoc.common.ApiResponse
import com.aoc.member.application.LoginResult
import com.aoc.member.application.MemberService
import com.aoc.member.infra.CognitoClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CallbackRequest(val cognitoToken: String)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val cognitoClient: CognitoClient,
    private val memberService: MemberService
) {

    @PostMapping("/callback")
    fun callback(@RequestBody request: CallbackRequest): ApiResponse<LoginResult> {
        val claims = cognitoClient.validateToken(request.cognitoToken)
        val result = memberService.loginOrRegister(claims)
        return ApiResponse(success = true, data = result)
    }
}
