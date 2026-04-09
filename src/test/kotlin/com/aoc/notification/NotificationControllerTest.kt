package com.aoc.notification

import com.aoc.auth.ActorContext
import com.aoc.auth.JwtClaims
import com.aoc.auth.JwtProvider
import com.aoc.auth.ShadowClaims
import com.aoc.auth.ShadowJwtProvider
import com.aoc.config.SecurityConfig
import com.aoc.member.domain.Role
import com.aoc.notification.dto.NotificationSettingResponse
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(NotificationController::class)
@Import(SecurityConfig::class, ActorContext::class)
class NotificationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var notificationService: NotificationService

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

    private fun mockMarketerToken(userId: String = "marketer-id") {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = userId, role = Role.MARKETER, isShadow = false, jti = "jti-mk")
        )
    }

    private fun mockOperatorToken(userId: String = "operator-id") {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = userId, role = Role.OPERATOR, isShadow = false, jti = "jti-op")
        )
    }

    private fun mockAgencyManagerToken(userId: String = "agency-id") {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = userId, role = Role.AGENCY_MANAGER, isShadow = false, jti = "jti-am")
        )
    }

    private fun mockShadowToken(userId: String = "marketer-id") {
        whenever(jwtProvider.validateToken(any())).thenThrow(RuntimeException("invalid"))
        whenever(shadowJwtProvider.validateToken(any())).thenReturn(
            ShadowClaims(
                jti = "shadow-jti",
                userId = userId,
                role = Role.MARKETER,
                operatorId = "operator-id",
                targetName = "테스트마케터",
                targetWorkEmail = null
            )
        )
    }

    // --- GET /notification-settings ---

    @Test
    fun `인증 없이 GET notification-settings 호출 시 401을 반환한다`() {
        mockMvc.perform(get("/notification-settings"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `MARKETER는 GET notification-settings에서 200과 설정 값을 반환한다`() {
        mockMarketerToken()
        whenever(notificationService.getSettings("marketer-id"))
            .thenReturn(NotificationSettingResponse(inquiryAlert = true, marketingAlert = false))

        mockMvc.perform(
            get("/notification-settings")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.inquiryAlert").value(true))
            .andExpect(jsonPath("$.data.marketingAlert").value(false))
    }

    @Test
    fun `쉐도우 세션에서 GET notification-settings는 대상 계정 설정을 반환한다`() {
        mockShadowToken(userId = "shadow-target-id")
        whenever(notificationService.getSettings("shadow-target-id"))
            .thenReturn(NotificationSettingResponse(inquiryAlert = true, marketingAlert = true))

        mockMvc.perform(
            get("/notification-settings")
                .header("Authorization", "Bearer mock-shadow-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.inquiryAlert").value(true))
    }

    @Test
    fun `OPERATOR도 GET notification-settings에서 200과 설정 값을 반환한다`() {
        mockOperatorToken()
        whenever(notificationService.getSettings("operator-id"))
            .thenReturn(NotificationSettingResponse(inquiryAlert = false, marketingAlert = false))

        mockMvc.perform(
            get("/notification-settings")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    // --- PUT /notification-settings ---

    @Test
    fun `MARKETER는 PUT notification-settings에서 200을 반환한다`() {
        mockMarketerToken()

        mockMvc.perform(
            put("/notification-settings")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("inquiryAlert" to false, "marketingAlert" to true)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `OPERATOR는 PUT notification-settings에서 403을 반환한다`() {
        mockOperatorToken()

        mockMvc.perform(
            put("/notification-settings")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("inquiryAlert" to true, "marketingAlert" to false)))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `AGENCY_MANAGER는 PUT notification-settings에서 403을 반환한다`() {
        mockAgencyManagerToken()

        mockMvc.perform(
            put("/notification-settings")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("inquiryAlert" to true, "marketingAlert" to false)))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `쉐도우 세션은 PUT notification-settings에서 403을 반환한다`() {
        mockShadowToken()

        mockMvc.perform(
            put("/notification-settings")
                .header("Authorization", "Bearer mock-shadow-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("inquiryAlert" to true, "marketingAlert" to false)))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }
}
