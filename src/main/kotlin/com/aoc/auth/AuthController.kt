package com.aoc.auth

import com.aoc.common.ApiResponse
import com.aoc.member.application.LoginResult
import com.aoc.member.application.MemberService
import com.aoc.member.infra.CognitoClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CallbackRequest(val cognitoToken: String)

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val cognitoClient: CognitoClient,
    private val memberService: MemberService
) {

    @Operation(summary = "소셜 로그인 콜백", description = "Cognito JWT를 검증하고 서버 JWT를 발급합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "로그인 성공"),
        SwaggerApiResponse(responseCode = "401", description = "유효하지 않은 Cognito 토큰")
    )
    @PostMapping("/callback")
    fun callback(@RequestBody request: CallbackRequest): ApiResponse<LoginResult> {
        val claims = cognitoClient.validateToken(request.cognitoToken)
        val result = memberService.loginOrRegister(claims)
        return ApiResponse.ok(result)
    }
}
