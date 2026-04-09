package com.aoc.history

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class HistorySpecificationTest {

    @Autowired
    private lateinit var historyRepository: HistoryRepository

    @BeforeEach
    fun setUp() {
        historyRepository.deleteAll()
        historyRepository.saveAll(
            listOf(
                History(
                    entityType = "Member", entityId = "member-001",
                    action = HistoryAction.UPDATE, actorId = "actor-A",
                    createdAt = LocalDateTime.of(2026, 4, 1, 10, 0, 0)
                ),
                History(
                    entityType = "Member", entityId = "member-002",
                    action = HistoryAction.PERSIST, actorId = "actor-B",
                    createdAt = LocalDateTime.of(2026, 4, 5, 12, 0, 0)
                ),
                History(
                    entityType = "Member", entityId = "member-001",
                    action = HistoryAction.DELETE, actorId = "actor-A",
                    createdAt = LocalDateTime.of(2026, 4, 9, 9, 0, 0)
                ),
                History(
                    entityType = "Member", entityId = "member-003",
                    action = HistoryAction.LOGIN, actorId = "actor-C",
                    createdAt = LocalDateTime.of(2026, 4, 9, 15, 0, 0)
                ),
            )
        )
    }

    @Test
    fun `필터 없이 조회하면 전체 히스토리를 반환한다`() {
        val spec = HistoryController.buildSpec(null, null, null, null, null)
        val result = historyRepository.findAll(spec)
        assertEquals(4, result.size)
    }

    @Test
    fun `action 필터로 조회하면 해당 action만 반환한다`() {
        val spec = HistoryController.buildSpec(null, null, HistoryAction.UPDATE, null, null)
        val result = historyRepository.findAll(spec)
        assertEquals(1, result.size)
        assertEquals(HistoryAction.UPDATE, result[0].action)
    }

    @Test
    fun `actorId 필터로 조회하면 해당 actor의 히스토리만 반환한다`() {
        val spec = HistoryController.buildSpec(null, null, null, null, "actor-A")
        val result = historyRepository.findAll(spec)
        assertEquals(2, result.size)
        assertTrue(result.all { it.actorId == "actor-A" })
    }

    @Test
    fun `targetMemberId 필터로 조회하면 해당 entityId만 반환한다`() {
        val spec = HistoryController.buildSpec(null, null, null, "member-001", null)
        val result = historyRepository.findAll(spec)
        assertEquals(2, result.size)
        assertTrue(result.all { it.entityId == "member-001" })
    }

    @Test
    fun `from 날짜 필터로 조회하면 해당 날짜 이후 히스토리만 반환한다`() {
        val spec = HistoryController.buildSpec(LocalDate.of(2026, 4, 5), null, null, null, null)
        val result = historyRepository.findAll(spec)
        assertEquals(3, result.size)
        assertTrue(result.all { it.createdAt >= LocalDateTime.of(2026, 4, 5, 0, 0, 0) })
    }

    @Test
    fun `to 날짜 필터로 조회하면 해당 날짜까지의 히스토리만 반환한다`() {
        val spec = HistoryController.buildSpec(null, LocalDate.of(2026, 4, 5), null, null, null)
        val result = historyRepository.findAll(spec)
        assertEquals(2, result.size)
        assertTrue(result.all { it.createdAt < LocalDateTime.of(2026, 4, 6, 0, 0, 0) })
    }

    @Test
    fun `from과 to를 함께 지정하면 해당 기간의 히스토리만 반환한다`() {
        val spec = HistoryController.buildSpec(
            LocalDate.of(2026, 4, 2),
            LocalDate.of(2026, 4, 8),
            null, null, null
        )
        val result = historyRepository.findAll(spec)
        assertEquals(1, result.size)
        assertEquals("member-002", result[0].entityId)
    }

    @Test
    fun `여러 필터를 조합하면 AND 조건으로 적용된다`() {
        val spec = HistoryController.buildSpec(null, null, HistoryAction.DELETE, "member-001", "actor-A")
        val result = historyRepository.findAll(spec)
        assertEquals(1, result.size)
        assertEquals(HistoryAction.DELETE, result[0].action)
        assertEquals("member-001", result[0].entityId)
        assertEquals("actor-A", result[0].actorId)
    }

    @Test
    fun `일치하는 결과가 없으면 빈 리스트를 반환한다`() {
        val spec = HistoryController.buildSpec(null, null, HistoryAction.SHADOW_LOGIN_START, null, null)
        val result = historyRepository.findAll(spec)
        assertTrue(result.isEmpty())
    }
}
