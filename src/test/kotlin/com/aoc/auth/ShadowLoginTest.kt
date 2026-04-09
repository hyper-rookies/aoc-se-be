package com.aoc.auth

import com.aoc.common.MemberNotFoundException
import com.aoc.common.ShadowActionNotAllowedException
import com.aoc.config.SecurityConfig
import com.aoc.member.domain.Role
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ShadowController::class)
@Import(SecurityConfig::class, ActorContext::class)
class ShadowLoginTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var shadowService: ShadowService

    @MockBean
    private lateinit var jwtProvider: JwtProvider

    @MockBean
    private lateinit var shadowJwtProvider: ShadowJwtProvider

    @MockBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private lateinit var valueOps: ValueOperations<String, String>

    @BeforeEach
    fun setUp() {
        valueOps = mock()
        whenever(redisTemplate.hasKey(any())).thenReturn(false)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get(any<String>())).thenReturn(null)
    }

    private fun mockOperatorToken(operatorId: String = "operator-id") {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = operatorId, role = Role.OPERATOR, isShadow = false, jti = "jti-$operatorId")
        )
    }

    @Test
    fun `OPERATOR가 MARKETER에게 쉐도우 로그인을 발급하면 Shadow JWT를 반환한다`() {
        mockOperatorToken()
        whenever(shadowService.startShadow(any(), any())).thenReturn(ShadowLoginResult("shadow-token-value"))

        mockMvc.perform(
            post("/shadow-login")
                .header("Authorization", "Bearer mock-operator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("targetMemberId" to "target-id")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.shadowToken").value("shadow-token-value"))
    }

    @Test
    fun `OPERATOR가 OPERATOR에게 쉐도우 발급 시 403을 반환한다`() {
        mockOperatorToken()
        whenever(shadowService.startShadow(any(), any())).thenThrow(ShadowActionNotAllowedException())

        mockMvc.perform(
            post("/shadow-login")
                .header("Authorization", "Bearer mock-operator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("targetMemberId" to "other-operator-id")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PERMISSION_002"))
    }

    @Test
    fun `운영자가 본인 계정에 쉐도우 발급 시 403을 반환한다`() {
        mockOperatorToken(operatorId = "operator-id")
        whenever(shadowService.startShadow(any(), any())).thenThrow(ShadowActionNotAllowedException())

        mockMvc.perform(
            post("/shadow-login")
                .header("Authorization", "Bearer mock-operator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("targetMemberId" to "operator-id")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PERMISSION_002"))
    }

    @Test
    fun `쉐도우 JWT로 POST shadow-login 시도 시 403을 반환한다`() {
        whenever(jwtProvider.validateToken(any())).thenThrow(RuntimeException("not an operator jwt"))
        whenever(shadowJwtProvider.validateToken(any())).thenReturn(
            ShadowClaims(
                jti = "shadow-jti",
                userId = "marketer-id",
                role = Role.MARKETER,
                operatorId = "operator-id",
                targetName = "테스트마케터",
                targetWorkEmail = null
            )
        )

        mockMvc.perform(
            post("/shadow-login")
                .header("Authorization", "Bearer mock-shadow-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("targetMemberId" to "some-id")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `OPERATOR가 아닌 토큰으로 POST shadow-login 시도 시 403을 반환한다`() {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = "marketer-id", role = Role.MARKETER, isShadow = false, jti = "jti-mk")
        )

        mockMvc.perform(
            post("/shadow-login")
                .header("Authorization", "Bearer mock-marketer-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("targetMemberId" to "some-id")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `존재하지 않는 targetMemberId는 404를 반환한다`() {
        mockOperatorToken()
        whenever(shadowService.startShadow(any(), any())).thenThrow(MemberNotFoundException())

        mockMvc.perform(
            post("/shadow-login")
                .header("Authorization", "Bearer mock-operator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("targetMemberId" to "non-existent")))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("MEMBER_001"))
    }
}
