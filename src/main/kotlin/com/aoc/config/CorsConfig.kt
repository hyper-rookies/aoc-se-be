package com.aoc.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    @Value("\${ALLOWED_ORIGINS:https://d2aom86zopona0.cloudfront.net}")
    private val allowedOriginsEnv: String,

    @Value("\${cors.allowed-origins:}")
    private val allowedOriginsLocal: String
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = buildList {
            if (allowedOriginsLocal.isNotBlank()) add(allowedOriginsLocal)
            addAll(allowedOriginsEnv.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }.distinct()

        val config = CorsConfiguration()
        config.allowedOrigins = origins
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", config)
        source.registerCorsConfiguration("/auth/**", config)
        return source
    }
}
