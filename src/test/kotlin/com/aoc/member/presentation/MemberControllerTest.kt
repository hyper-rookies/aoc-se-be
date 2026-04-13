package com.aoc.member.presentation

import com.aoc.auth.ActorContext
import com.aoc.auth.JwtClaims
import com.aoc.auth.JwtProvider
import com.aoc.auth.ShadowClaims
import com.aoc.auth.ShadowJwtProvider
import com.aoc.common.AocAccessDeniedException
import com.aoc.config.SecurityConfig
import com.aoc.member.application.EmailVerificationService
import com.aoc.member.application.MemberService
import com.aoc.member.domain.MemberStatus
import com.aoc.member.domain.Role
import com.aoc.member.presentation.dto.MemberResponse
import com.aoc.member.presentation.dto.MemberSummaryResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(MemberController::class)
@Import(SecurityConfig::class, ActorContext::class)
class MemberControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var memberService: MemberService

    @MockBean
    private lateinit var emailVerificationService: EmailVerificationService

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

    private fun mockAgencyManagerToken(userId: String = "agency-id") {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = userId, role = Role.AGENCY_MANAGER, isShadow = false, jti = "jti-am")
        )
    }

    private fun mockOperatorToken(userId: String = "operator-id") {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = userId, role = Role.OPERATOR, isShadow = false, jti = "jti-op")
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

    private fun memberResponse(name: String = "테스트유저", role: Role = Role.MARKETER) = MemberResponse(
        email = "test@example.com",
        provider = "google",
        name = name,
        workEmail = null,
        role = role,
        status = MemberStatus.ACTIVE,
        createdAt = LocalDateTime.now()
    )

    // ── GET /members/me ──────────────────────────────────────────────────────

    @Test
    fun `정상 토큰으로 GET members-me 시 200과 회원 정보를 반환한다`() {
        mockMarketerToken()
        whenever(memberService.getMe("marketer-id")).thenReturn(memberResponse(name = "홍길동"))

        mockMvc.perform(
            get("/members/me")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("홍길동"))
    }

    @Test
    fun `쉐도우 세션에서 GET members-me는 대상 계정 정보를 반환한다`() {
        mockShadowToken(userId = "shadow-target-id")
        whenever(memberService.getMe("shadow-target-id")).thenReturn(memberResponse(name = "대상마케터"))

        mockMvc.perform(
            get("/members/me")
                .header("Authorization", "Bearer mock-shadow-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("대상마케터"))
    }

    @Test
    fun `인증 없이 GET members-me 시 401을 반환한다`() {
        mockMvc.perform(get("/members/me"))
            .andExpect(status().isUnauthorized)
    }

    // ── PUT /members/me ──────────────────────────────────────────────────────

    @Test
    fun `MARKETER가 PUT members-me로 이름을 수정하면 200을 반환한다`() {
        mockMarketerToken()

        mockMvc.perform(
            put("/members/me")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "수정된이름")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(memberService).updateMe("marketer-id", "수정된이름")
    }

    @Test
    fun `쉐도우 JWT로 PUT members-me 시도 시 403을 반환한다`() {
        mockShadowToken()

        mockMvc.perform(
            put("/members/me")
                .header("Authorization", "Bearer mock-shadow-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "변경이름")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `PUT members-me에서 name이 blank이면 400을 반환한다`() {
        mockMarketerToken()

        mockMvc.perform(
            put("/members/me")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "")))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT members-me에서 name이 51자이면 400을 반환한다`() {
        mockMarketerToken()
        val longName = "가".repeat(51)

        mockMvc.perform(
            put("/members/me")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to longName)))
        )
            .andExpect(status().isBadRequest)
    }

    // ── DELETE /members/me ───────────────────────────────────────────────────

    @Test
    fun `MARKETER가 DELETE members-me 시 200을 반환하고 deleteMe가 호출된다`() {
        mockMarketerToken()

        mockMvc.perform(
            delete("/members/me")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(memberService).deleteMe("marketer-id")
    }

    @Test
    fun `쉐도우 JWT로 DELETE members-me 시도 시 403을 반환한다`() {
        mockShadowToken()

        mockMvc.perform(
            delete("/members/me")
                .header("Authorization", "Bearer mock-shadow-token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `AGENCY_MANAGER가 DELETE members-me 시도 시 403을 반환한다`() {
        mockAgencyManagerToken()

        mockMvc.perform(
            delete("/members/me")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `OPERATOR가 DELETE members-me 시도 시 403을 반환한다`() {
        mockOperatorToken()

        mockMvc.perform(
            delete("/members/me")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    // ── PUT /members/{id}/role ───────────────────────────────────────────────

    @Test
    fun `OPERATOR가 PUT members-id-role 시 200을 반환한다`() {
        mockOperatorToken()

        mockMvc.perform(
            put("/members/some-target-id/role")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("role" to "AGENCY_MANAGER")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `자기 자신 역할 변경 시도 시 403을 반환한다`() {
        mockOperatorToken(userId = "operator-id")
        whenever(memberService.updateMemberRole(any(), any(), any())).thenThrow(AocAccessDeniedException())

        mockMvc.perform(
            put("/members/operator-id/role")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("role" to "MARKETER")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `쉐도우 JWT로 PUT members-id-role 시도 시 403을 반환한다`() {
        mockShadowToken()

        mockMvc.perform(
            put("/members/some-target-id/role")
                .header("Authorization", "Bearer mock-shadow-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("role" to "OPERATOR")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `MARKETER 토큰으로 PUT members-id-role 시도 시 403을 반환한다`() {
        mockMarketerToken()

        mockMvc.perform(
            put("/members/some-target-id/role")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("role" to "OPERATOR")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    // ── GET /members ─────────────────────────────────────────────────────────

    @Test
    fun `OPERATOR가 GET members 시 200과 페이지 응답을 반환한다`() {
        mockOperatorToken()
        val member = MemberSummaryResponse(
            id = "member-id-1",
            name = "마케터A",
            workEmail = null,
            role = Role.MARKETER,
            status = MemberStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        whenever(memberService.getMembers(isNull(), isNull(), any())).thenReturn(PageImpl(listOf(member)))

        mockMvc.perform(
            get("/members")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].name").value("마케터A"))
    }

    @Test
    fun `role 필터로 GET members 시 해당 역할로 서비스가 호출된다`() {
        mockOperatorToken()
        whenever(memberService.getMembers(eq(Role.MARKETER), isNull(), any())).thenReturn(PageImpl(emptyList()))

        mockMvc.perform(
            get("/members")
                .param("role", "MARKETER")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isOk)

        verify(memberService).getMembers(eq(Role.MARKETER), isNull(), any())
    }

    @Test
    fun `status 필터로 GET members 시 해당 상태로 서비스가 호출된다`() {
        mockOperatorToken()
        whenever(memberService.getMembers(isNull(), eq(MemberStatus.ACTIVE), any())).thenReturn(PageImpl(emptyList()))

        mockMvc.perform(
            get("/members")
                .param("status", "ACTIVE")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isOk)

        verify(memberService).getMembers(isNull(), eq(MemberStatus.ACTIVE), any())
    }

    @Test
    fun `MARKETER 토큰으로 GET members 시 403을 반환한다`() {
        mockMarketerToken()

        mockMvc.perform(
            get("/members")
                .header("Authorization", "Bearer mock-token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }
}
