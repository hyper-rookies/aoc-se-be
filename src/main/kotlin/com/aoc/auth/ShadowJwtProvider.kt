package com.aoc.auth

import com.aoc.member.domain.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

data class ShadowClaims(
    val jti: String,
    val userId: String,
    val role: Role,
    val operatorId: String,
    val isShadow: Boolean = true,
    val targetName: String,
    val targetWorkEmail: String?
)

@Component
class ShadowJwtProvider(
    @Value("\${shadow-jwt.secret}") private val secret: String,
    @Value("\${shadow-jwt.expiration:1800}") private val expirationSeconds: Long = 1800L
) {

    private val signingKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    }

    fun generateToken(
        userId: String,
        role: Role,
        operatorId: String,
        targetName: String,
        targetWorkEmail: String?
    ): String {
        val now = Date()
        val expiry = Date(now.time + expirationSeconds * 1000)
        val jti = UUID.randomUUID().toString()

        return Jwts.builder()
            .subject(userId)
            .claim("role", role.name)
            .claim("isShadow", true)
            .claim("operatorId", operatorId)
            .claim("targetName", targetName)
            .claim("targetWorkEmail", targetWorkEmail)
            .id(jti)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact()
    }

    fun validateToken(token: String): ShadowClaims {
        val claims = parseToken(token)
        return ShadowClaims(
            jti = claims.id,
            userId = claims.subject,
            role = Role.valueOf(claims.get("role", String::class.java)),
            operatorId = claims.get("operatorId", String::class.java),
            isShadow = true,
            targetName = claims.get("targetName", String::class.java),
            targetWorkEmail = claims.get("targetWorkEmail", String::class.java)
        )
    }

    private fun parseToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
