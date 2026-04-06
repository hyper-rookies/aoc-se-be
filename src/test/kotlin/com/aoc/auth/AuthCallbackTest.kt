package com.aoc.auth

import com.aoc.config.SecurityConfig
import com.aoc.member.application.LoginResult
import com.aoc.member.application.MemberService
import com.aoc.member.infra.CognitoClaims
import com.aoc.member.infra.CognitoClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
class AuthCallbackTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var cognitoClient: CognitoClient

    @MockBean
    private lateinit var memberService: MemberService

    // SecurityConfig 의존성
    @MockBean
    private lateinit var jwtProvider: JwtProvider

    @MockBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Test
    fun `유효하지 않은 Cognito 토큰은 401을 반환한다`() {
        whenever(cognitoClient.validateToken(any())).thenThrow(CognitoJwtException("유효하지 않은 토큰"))

        mockMvc.perform(
            post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("cognitoToken" to "invalid-token")))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_001"))
    }

    @Test
    fun `유효한 Cognito 토큰은 200과 JWT를 반환한다`() {
        val fakeClaims = CognitoClaims(sub = "sub-123", email = "user@example.com", name = "테스트")
        val loginResult = LoginResult(
            accessToken = "access-token-value",
            refreshToken = "refresh-token-value",
            isNewMember = false
        )

        whenever(cognitoClient.validateToken(any())).thenReturn(fakeClaims)
        whenever(memberService.loginOrRegister(any())).thenReturn(loginResult)

        mockMvc.perform(
            post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("cognitoToken" to "valid-token")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
            .andExpect(jsonPath("$.data.isNewMember").value(false))
    }
}
