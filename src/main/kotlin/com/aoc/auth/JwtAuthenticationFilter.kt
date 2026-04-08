package com.aoc.auth

import com.aoc.common.ApiResponse
import com.aoc.common.ErrorCode
import com.aoc.member.domain.Role
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val shadowJwtProvider: ShadowJwtProvider,
    private val redisTemplate: RedisTemplate<String, String>
) : OncePerRequestFilter() {

    private val objectMapper = ObjectMapper()

    private data class ParsedClaims(
        val jti: String,
        val userId: String,
        val role: Role,
        val isShadow: Boolean,
        val operatorId: String?,
        val shadowId: String?
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)

        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        val parsed = parseToken(token)
        if (parsed == null) {
            sendUnauthorized(response)
            return
        }

        if (redisTemplate.hasKey("blacklist:${parsed.jti}") == true) {
            sendUnauthorized(response)
            return
        }

        if (!parsed.isShadow) {
            val sessionJti = redisTemplate.opsForValue().get("session:${parsed.userId}")
            if (sessionJti != null && sessionJti != parsed.jti) {
                sendUnauthorized(response)
                return
            }
        }

        try {
            ActorContext.set(parsed.userId, parsed.operatorId, parsed.shadowId, parsed.isShadow)

            val auth = UsernamePasswordAuthenticationToken(
                parsed.userId,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${parsed.role.name}"))
            )
            SecurityContextHolder.getContext().authentication = auth

            filterChain.doFilter(request, response)
        } finally {
            ActorContext.clear()
            SecurityContextHolder.clearContext()
        }
    }

    private fun parseToken(token: String): ParsedClaims? {
        try {
            val claims = jwtProvider.validateToken(token)
            return ParsedClaims(
                jti = claims.jti,
                userId = claims.userId,
                role = claims.role,
                isShadow = false,
                operatorId = null,
                shadowId = null
            )
        } catch (_: Exception) {}

        try {
            val claims = shadowJwtProvider.validateToken(token)
            return ParsedClaims(
                jti = claims.jti,
                userId = claims.userId,
                role = claims.role,
                isShadow = true,
                operatorId = claims.operatorId,
                shadowId = claims.jti
            )
        } catch (_: Exception) {}

        return null
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ")
    }

    private fun sendUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(
            objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.INVALID_ACCESS_TOKEN)
            )
        )
    }
}
