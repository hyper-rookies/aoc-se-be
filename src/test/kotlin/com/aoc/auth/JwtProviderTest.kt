package com.aoc.auth

import com.aoc.member.domain.Role
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class JwtProviderTest {

    private val secret = "test-secret-key-that-is-at-least-32-characters-long-for-hmac!!"
    private val jwtProvider = JwtProvider(secret, 3600L)

    @Test
    fun `토큰 생성 후 파싱하면 동일한 클레임을 반환한다`() {
        val userId = "01HZXXXTEST"
        val role = Role.MARKETER
        val jti = UUID.randomUUID().toString()

        val token = jwtProvider.generateToken(userId, role, jti)
        val claims = jwtProvider.validateToken(token)

        assertEquals(userId, claims.userId)
        assertEquals(role, claims.role)
        assertEquals(jti, claims.jti)
        assertFalse(claims.isShadow)
    }

    @Test
    fun `OPERATOR 역할 토큰도 정상 파싱된다`() {
        val jti = UUID.randomUUID().toString()
        val token = jwtProvider.generateToken("operator-id", Role.OPERATOR, jti)
        val claims = jwtProvider.validateToken(token)

        assertEquals(Role.OPERATOR, claims.role)
        assertEquals(jti, claims.jti)
        assertNotNull(claims.jti)
    }

    @Test
    fun `만료된 토큰은 예외를 던진다`() {
        val expiredProvider = JwtProvider(secret, -100L) // 100초 전 만료
        val token = expiredProvider.generateToken("user-id", Role.MARKETER, UUID.randomUUID().toString())

        assertThrows<Exception> {
            jwtProvider.validateToken(token)
        }
    }

    @Test
    fun `잘못된 형식의 토큰은 예외를 던진다`() {
        assertThrows<Exception> {
            jwtProvider.validateToken("invalid.token.value")
        }
    }

    @Test
    fun `다른 시크릿으로 서명된 토큰은 예외를 던진다`() {
        val otherProvider = JwtProvider("completely-different-secret-key-must-be-32-chars-or-more!!", 3600L)
        val token = otherProvider.generateToken("user-id", Role.MARKETER, UUID.randomUUID().toString())

        assertThrows<Exception> {
            jwtProvider.validateToken(token)
        }
    }
}
