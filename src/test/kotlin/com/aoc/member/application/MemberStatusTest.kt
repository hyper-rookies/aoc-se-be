package com.aoc.member.application

import com.aoc.auth.CognitoJwtException
import com.aoc.auth.JwtProvider
import com.aoc.auth.ShadowJwtProvider
import com.aoc.common.ErrorCode
import com.aoc.common.MemberStatusException
import com.aoc.config.SecurityConfig
import com.aoc.member.infra.CognitoClaims
import com.aoc.member.infra.CognitoClient
import com.aoc.auth.AuthController
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
class MemberStatusTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var cognitoClient: CognitoClient

    @MockBean
    private lateinit var memberService: MemberService

    @MockBean
    private lateinit var jwtProvider: JwtProvider

    @MockBean
    private lateinit var shadowJwtProvider: ShadowJwtProvider

    @MockBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Test
    fun `DORMANT 계정 로그인은 403과 MEMBER_STATUS_001을 반환한다`() {
        whenever(cognitoClient.validateToken(any())).thenReturn(
            CognitoClaims(sub = "sub-123", email = "dormant@example.com", name = "휴면유저")
        )
        whenever(memberService.loginOrRegister(any())).thenThrow(MemberStatusException(ErrorCode.MEMBER_DORMANT))

        mockMvc.perform(
            post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("cognitoToken" to "token")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("MEMBER_STATUS_001"))
    }

    @Test
    fun `SUSPENDED 계정 로그인은 403과 MEMBER_STATUS_002를 반환한다`() {
        whenever(cognitoClient.validateToken(any())).thenReturn(
            CognitoClaims(sub = "sub-456", email = "suspended@example.com", name = "정지유저")
        )
        whenever(memberService.loginOrRegister(any())).thenThrow(MemberStatusException(ErrorCode.MEMBER_SUSPENDED))

        mockMvc.perform(
            post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("cognitoToken" to "token")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("MEMBER_STATUS_002"))
    }
}
