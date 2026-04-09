package com.aoc.member.infra

import com.aoc.auth.CognitoJwtException
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class CognitoClaims(
    val sub: String,
    val email: String,
    val name: String,
    val provider: String = "GOOGLE"
)

@Component
class CognitoClient(
    @Value("\${cognito.user-pool-id}") private val userPoolId: String,
    @Value("\${cognito.region}") private val region: String,
    @Value("\${cognito.client-id}") private val clientId: String,
    @Value("\${cognito.domain}") private val cognitoDomain: String
) {

    private val restTemplate = RestTemplate()

    private val jwksUrl get() = "https://cognito-idp.$region.amazonaws.com/$userPoolId/.well-known/jwks.json"
    private val keyCache = ConcurrentHashMap<String, RSAKey>()

    @PostConstruct
    fun loadJwks() {
        loadAndCacheKeys()
    }

    fun exchangeCodeForToken(code: String, redirectUri: String): String {
        val tokenUrl = "https://$cognitoDomain/oauth2/token"

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            add("code", code)
            add("redirect_uri", redirectUri)
        }

        val response = try {
            restTemplate.postForObject(tokenUrl, HttpEntity(body, headers), Map::class.java)
                ?: throw CognitoJwtException("Cognito Token Endpoint 응답이 없습니다")
        } catch (e: CognitoJwtException) {
            throw e
        } catch (e: Exception) {
            throw CognitoJwtException("Cognito 토큰 교환 실패", e)
        }

        return response["id_token"] as? String
            ?: throw CognitoJwtException("Cognito 응답에 id_token이 없습니다")
    }

    fun validateToken(token: String): CognitoClaims {
        val signedJwt = try {
            SignedJWT.parse(token)
        } catch (e: Exception) {
            throw CognitoJwtException("JWT 파싱 실패", e)
        }

        val kid = signedJwt.header.keyID
            ?: throw CognitoJwtException("JWT 헤더에 kid가 없습니다")

        val rsaKey = keyCache[kid] ?: run {
            loadAndCacheKeys()
            keyCache[kid] ?: throw CognitoJwtException("kid에 해당하는 공개키를 찾을 수 없습니다: $kid")
        }

        try {
            val verifier = com.nimbusds.jose.crypto.RSASSAVerifier(rsaKey)
            if (!signedJwt.verify(verifier)) {
                throw CognitoJwtException("JWT 서명 검증 실패")
            }
        } catch (e: CognitoJwtException) {
            throw e
        } catch (e: Exception) {
            throw CognitoJwtException("JWT 서명 검증 중 오류", e)
        }

        val claims = signedJwt.jwtClaimsSet

        val expiration = claims.expirationTime
            ?: throw CognitoJwtException("JWT에 만료 시간이 없습니다")
        if (expiration.before(java.util.Date())) {
            throw CognitoJwtException("JWT가 만료되었습니다")
        }

        val audience = claims.audience
        if (clientId !in audience) {
            throw CognitoJwtException("JWT audience가 일치하지 않습니다")
        }

        val sub = claims.subject ?: throw CognitoJwtException("JWT에 sub 클레임이 없습니다")
        val email = claims.getStringClaim("email") ?: throw CognitoJwtException("JWT에 email 클레임이 없습니다")
        val name = claims.getStringClaim("name") ?: throw CognitoJwtException("JWT에 name 클레임이 없습니다")

        return CognitoClaims(sub = sub, email = email, name = name)
    }

    private fun loadAndCacheKeys() {
        try {
            val jwkSet = JWKSet.load(URL(jwksUrl))
            jwkSet.keys.filterIsInstance<RSAKey>().forEach { key ->
                keyCache[key.keyID] = key
            }
        } catch (e: Exception) {
            throw CognitoJwtException("JWKS 로드 실패: $jwksUrl", e)
        }
    }
}
