package com.aoc.history

import com.aoc.common.SpringApplicationContext
import com.aoc.member.domain.Member
import com.aoc.member.domain.MemberRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
@Import(SpringApplicationContext::class, HistoryEventHandler::class)
class HistoryEntityListenerTest {

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var historyRepository: HistoryRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Member 저장 시 INSERT 히스토리가 자동 기록된다`() {
        val member = memberRepository.save(
            Member(email = "insert@example.com", name = "신규유저", provider = "google", providerId = "google-sub-001")
        )

        val histories = historyRepository.findAll()
            .filter { it.entityType == "Member" && it.entityId == member.id }

        assertEquals(1, histories.size)
        val h = histories[0]
        assertEquals(HistoryAction.PERSIST, h.action)
        assertNull(h.beforeValue)
        assertNotNull(h.afterValue)
        assertEquals("system", h.actorId)  // ActorContext 미설정 시 system
        assertFalse(h.isShadow)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Member 수정 시 UPDATE 히스토리에 before_value와 after_value가 모두 기록된다`() {
        val tx = TransactionTemplate(transactionManager)

        // INSERT
        val savedId = tx.execute {
            memberRepository.save(
                Member(email = "update@example.com", name = "수정전", provider = "google", providerId = "google-sub-002")
            ).id
        }!!

        // UPDATE: 별도 트랜잭션에서 로드(→ @PostLoad/snapshot) + 수정 + 커밋(→ @PreUpdate)
        tx.execute {
            val member = memberRepository.findById(savedId).get()
            member.name = "수정후"
            memberRepository.save(member)
        }

        val histories = historyRepository.findAll()
            .filter { it.entityType == "Member" && it.entityId == savedId }
            .sortedBy { it.createdAt }

        assertEquals(2, histories.size)
        assertEquals(HistoryAction.PERSIST, histories[0].action)

        val updateHistory = histories[1]
        assertEquals(HistoryAction.UPDATE, updateHistory.action)
        assertNotNull(updateHistory.beforeValue)
        assertNotNull(updateHistory.afterValue)
        assertTrue(updateHistory.beforeValue!!.contains("수정전"))
        assertTrue(updateHistory.afterValue!!.contains("수정후"))
    }
}
