package com.aoc.auth

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
    private val redisTemplate: RedisTemplate<String, String>
) : OncePerRequestFilter() {

    private val objectMapper = ObjectMapper()

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

        val claims = try {
            jwtProvider.validateToken(token)
        } catch (e: Exception) {
            sendUnauthorized(response)
            return
        }

        if (redisTemplate.hasKey("blacklist:${claims.jti}") == true) {
            sendUnauthorized(response)
            return
        }

        try {
            ActorContext.set(claims.userId, null, claims.isShadow)

            val auth = UsernamePasswordAuthenticationToken(
                claims.userId,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}"))
            )
            SecurityContextHolder.getContext().authentication = auth

            filterChain.doFilter(request, response)
        } finally {
            ActorContext.clear()
            SecurityContextHolder.clearContext()
        }
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
                mapOf("success" to false, "message" to "인증이 필요합니다.")
            )
        )
    }
}
