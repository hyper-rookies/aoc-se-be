package com.aoc.member.infra

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class CognitoJwtException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

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
    @Value("\${cognito.client-id}") private val clientId: String
) {

    private val jwksUrl get() = "https://cognito-idp.$region.amazonaws.com/$userPoolId/.well-known/jwks.json"
    private val keyCache = ConcurrentHashMap<String, RSAKey>()

    @PostConstruct
    fun loadJwks() {
        loadAndCacheKeys()
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
