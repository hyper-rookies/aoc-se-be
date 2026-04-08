package com.aoc.auth

import com.aoc.member.domain.Role
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShadowJwtProviderTest {

    private val secret = "test-shadow-secret-key-that-is-at-least-32-characters-long!!"
    private val provider = ShadowJwtProvider(secret)

    @Test
    fun `정상 발급 후 파싱하면 동일한 클레임을 반환한다`() {
        val token = provider.generateToken(
            userId = "target-user-id",
            role = Role.MARKETER,
            operatorId = "operator-id",
            targetName = "테스트유저",
            targetWorkEmail = "test@work.com"
        )

        val claims = provider.validateToken(token)

        assertEquals("target-user-id", claims.userId)
        assertEquals(Role.MARKETER, claims.role)
        assertEquals("operator-id", claims.operatorId)
        assertEquals("테스트유저", claims.targetName)
        assertEquals("test@work.com", claims.targetWorkEmail)
        assertTrue(claims.isShadow)
        assertNotNull(claims.jti)
    }

    @Test
    fun `targetWorkEmail이 null이어도 정상 파싱된다`() {
        val token = provider.generateToken(
            userId = "target-user-id",
            role = Role.AGENCY_MANAGER,
            operatorId = "operator-id",
            targetName = "대행사관리자",
            targetWorkEmail = null
        )

        val claims = provider.validateToken(token)

        assertEquals(Role.AGENCY_MANAGER, claims.role)
        assertEquals(null, claims.targetWorkEmail)
    }

    @Test
    fun `만료된 토큰은 예외를 던진다`() {
        val expiredProvider = ShadowJwtProvider(secret, -100L)
        val token = expiredProvider.generateToken("user-id", Role.MARKETER, "op-id", "테스트", null)

        assertThrows<Exception> {
            provider.validateToken(token)
        }
    }
}
