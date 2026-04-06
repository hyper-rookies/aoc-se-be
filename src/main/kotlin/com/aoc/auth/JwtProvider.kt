package com.aoc.auth

import com.aoc.member.domain.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date

data class JwtClaims(
    val userId: String,
    val role: Role,
    val isShadow: Boolean,
    val jti: String
)

@Component
class JwtProvider(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-token-expiration}") private val accessTokenExpiration: Long
) {

    private val signingKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    }

    fun generateToken(userId: String, role: Role, jti: String): String {
        val now = Date()
        val expiry = Date(now.time + accessTokenExpiration * 1000)

        return Jwts.builder()
            .subject(userId)
            .claim("role", role.name)
            .claim("isShadow", false)
            .id(jti)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact()
    }

    fun validateToken(token: String): JwtClaims {
        val claims = parseToken(token)
        return JwtClaims(
            userId = claims.subject,
            role = Role.valueOf(claims.get("role", String::class.java)),
            isShadow = claims.get("isShadow", Boolean::class.javaObjectType) ?: false,
            jti = claims.id
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
