package com.aoc.history

import com.aoc.auth.JwtClaims
import com.aoc.auth.JwtProvider
import com.aoc.auth.ShadowJwtProvider
import com.aoc.config.SecurityConfig
import com.aoc.member.domain.Role
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.jpa.domain.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Sort
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.mockito.kotlin.mock
import java.time.LocalDateTime

@WebMvcTest(HistoryController::class)
@Import(SecurityConfig::class)
class HistoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var historyRepository: HistoryRepository

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

    private fun mockOperatorToken() {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = "operator-id", role = Role.OPERATOR, isShadow = false, jti = "jti-op")
        )
    }

    private fun mockMarketerToken() {
        whenever(jwtProvider.validateToken(any())).thenReturn(
            JwtClaims(userId = "marketer-id", role = Role.MARKETER, isShadow = false, jti = "jti-mk")
        )
    }

    @Test
    fun `인증 없이 GET histories 호출 시 403을 반환한다`() {
        mockMvc.perform(get("/histories"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `MARKETER 권한으로 GET histories 호출 시 403을 반환한다`() {
        mockMarketerToken()

        mockMvc.perform(
            get("/histories")
                .header("Authorization", "Bearer mock-marketer-token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `OPERATOR는 GET histories에서 200과 빈 Page를 반환한다`() {
        mockOperatorToken()
        whenever(historyRepository.findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        mockMvc.perform(
            get("/histories")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray)
    }

    @Test
    fun `from-to 날짜 필터를 적용하면 200을 반환하고 repository가 호출된다`() {
        mockOperatorToken()
        whenever(historyRepository.findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        mockMvc.perform(
            get("/histories")
                .param("from", "2026-04-01")
                .param("to", "2026-04-09")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(historyRepository).findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>())
    }

    @Test
    fun `action 필터를 적용하면 200을 반환하고 repository가 호출된다`() {
        mockOperatorToken()
        whenever(historyRepository.findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        mockMvc.perform(
            get("/histories")
                .param("action", "LOGIN")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(historyRepository).findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>())
    }

    @Test
    fun `targetMemberId 필터를 적용하면 200을 반환하고 repository가 호출된다`() {
        mockOperatorToken()
        whenever(historyRepository.findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        mockMvc.perform(
            get("/histories")
                .param("targetMemberId", "member-abc-123")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(historyRepository).findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>())
    }

    @Test
    fun `필터 미입력 시 전체 조회로 200을 반환한다`() {
        mockOperatorToken()
        val history = History(
            entityType = "Member",
            entityId = "member-id",
            action = HistoryAction.LOGIN,
            actorId = "actor-id",
            createdAt = LocalDateTime.of(2026, 4, 9, 12, 0, 0)
        )
        whenever(historyRepository.findAll(any<Specification<History>>(), any<org.springframework.data.domain.Pageable>()))
            .thenReturn(PageImpl(listOf(history)))

        mockMvc.perform(
            get("/histories")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].action").value("LOGIN"))
    }

    @Test
    fun `GET histories export는 CSV Content-Type과 올바른 파일명을 반환한다`() {
        mockOperatorToken()
        val history = History(
            entityType = "Member",
            entityId = "member-id-001",
            action = HistoryAction.UPDATE,
            actorId = "actor-id-001",
            createdAt = LocalDateTime.of(2026, 4, 9, 10, 0, 0)
        )
        whenever(historyRepository.findAll(any<Specification<History>>(), any<Sort>()))
            .thenReturn(listOf(history))

        mockMvc.perform(
            get("/histories/export")
                .param("from", "2026-04-01")
                .param("to", "2026-04-09")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"history_2026-04-01_2026-04-09.csv\""))
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
    }

    @Test
    fun `MARKETER 권한으로 GET histories-export 호출 시 403을 반환한다`() {
        mockMarketerToken()

        mockMvc.perform(
            get("/histories/export")
                .header("Authorization", "Bearer mock-marketer-token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PERMISSION_001"))
    }

    @Test
    fun `GET histories export는 헤더와 데이터 행을 포함한 CSV를 반환한다`() {
        mockOperatorToken()
        val history = History(
            entityType = "Member",
            entityId = "member-id-001",
            action = HistoryAction.UPDATE,
            actorId = "actor-id-001",
            operatorId = "operator-id-001",
            isShadow = false,
            createdAt = LocalDateTime.of(2026, 4, 9, 10, 0, 0)
        )
        whenever(historyRepository.findAll(any<Specification<History>>(), any<Sort>()))
            .thenReturn(listOf(history))

        val result = mockMvc.perform(
            get("/histories/export")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
            .andReturn()

        val csv = result.response.contentAsString
        val lines = csv.trim().lines()
        assert(lines[0] == "id,entityType,entityId,action,actorId,operatorId,isShadow,createdAt")
        assert(lines[1].contains("Member"))
        assert(lines[1].contains("UPDATE"))
        assert(lines[1].contains("actor-id-001"))
    }

    @Test
    fun `from이 to보다 늦으면 400 INVALID_DATE_RANGE를 반환한다`() {
        mockOperatorToken()

        mockMvc.perform(
            get("/histories/export")
                .param("from", "2026-04-10")
                .param("to", "2026-04-01")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("HISTORY_001"))
    }

    @Test
    fun `범위가 91일이면 400 HISTORY_EXPORT_RANGE_EXCEEDED를 반환한다`() {
        mockOperatorToken()

        mockMvc.perform(
            get("/histories/export")
                .param("from", "2026-01-01")
                .param("to", "2026-04-02")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("HISTORY_002"))
    }

    @Test
    fun `범위가 정확히 90일이면 정상 처리된다`() {
        mockOperatorToken()
        whenever(historyRepository.findAll(any<Specification<History>>(), any<Sort>()))
            .thenReturn(emptyList())

        mockMvc.perform(
            get("/histories/export")
                .param("from", "2026-01-01")
                .param("to", "2026-04-01")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `from과 to가 같은 날이면 정상 처리된다`() {
        mockOperatorToken()
        whenever(historyRepository.findAll(any<Specification<History>>(), any<Sort>()))
            .thenReturn(emptyList())

        mockMvc.perform(
            get("/histories/export")
                .param("from", "2026-04-01")
                .param("to", "2026-04-01")
                .header("Authorization", "Bearer mock-operator-token")
        )
            .andExpect(status().isOk)
    }
}
