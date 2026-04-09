package com.aoc.member.infra

import com.aoc.auth.CognitoJwtException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class CognitoClientTest {

    // Spring @PostConstruct 미호출 — 네트워크 요청 없음
    private val cognitoClient = CognitoClient(
        userPoolId = "ap-northeast-2_testPool",
        region = "ap-northeast-2",
        clientId = "test-client-id",
        cognitoDomain = "test.auth.ap-northeast-2.amazoncognito.com"
    )

    companion object {
        // RSA 키 생성은 비용이 크므로 클래스 로딩 시 1회만 생성
        private lateinit var rsaKeyA: RSAKey
        private lateinit var rsaKeyB: RSAKey

        @BeforeAll
        @JvmStatic
        fun generateKeys() {
            rsaKeyA = RSAKeyGenerator(2048).keyID("kid-A").generate()
            rsaKeyB = RSAKeyGenerator(2048).keyID("kid-B").generate()
        }
    }

    @Test
    fun `완전히 잘못된 문자열은 CognitoJwtException을 던진다`() {
        assertThrows<CognitoJwtException> {
            cognitoClient.validateToken("not-a-jwt-string")
        }
    }

    @Test
    fun `kid가 없는 JWT는 CognitoJwtException을 던진다`() {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).build() // keyID 없음
        val claims = JWTClaimsSet.Builder()
            .subject("test-sub")
            .expirationTime(Date(System.currentTimeMillis() + 60_000))
            .build()
        val jwt = SignedJWT(header, claims)
        jwt.sign(RSASSASigner(rsaKeyA))

        assertThrows<CognitoJwtException> {
            cognitoClient.validateToken(jwt.serialize())
        }
    }

    @Test
    fun `캐시에 없는 kid의 JWT는 CognitoJwtException을 던진다`() {
        // keyCache에 kid-A만 넣고, JWT는 kid-B로 서명
        @Suppress("UNCHECKED_CAST")
        val keyCache = ReflectionTestUtils.getField(cognitoClient, "keyCache") as ConcurrentHashMap<String, RSAKey>
        keyCache["kid-A"] = rsaKeyA.toPublicJWK() as RSAKey

        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kid-unknown").build()
        val claimsSet = JWTClaimsSet.Builder()
            .subject("test-sub")
            .expirationTime(Date(System.currentTimeMillis() + 60_000))
            .build()
        val jwt = SignedJWT(header, claimsSet)
        jwt.sign(RSASSASigner(rsaKeyB))

        // kid miss → JWKS 재로드 시도 → 실패 → CognitoJwtException
        assertThrows<CognitoJwtException> {
            cognitoClient.validateToken(jwt.serialize())
        }
    }

    @Test
    fun `서명이 일치하지 않는 JWT는 CognitoJwtException을 던진다`() {
        val kid = "kid-mismatch"

        // kid-A 공개키를 캐시에 등록하되, JWT는 kid-B 개인키로 서명
        @Suppress("UNCHECKED_CAST")
        val keyCache = ReflectionTestUtils.getField(cognitoClient, "keyCache") as ConcurrentHashMap<String, RSAKey>
        keyCache[kid] = rsaKeyA.toPublicJWK() as RSAKey // 검증 키: A

        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build()
        val claimsSet = JWTClaimsSet.Builder()
            .subject("test-sub")
            .audience("test-client-id")
            .expirationTime(Date(System.currentTimeMillis() + 60_000))
            .build()
        val jwt = SignedJWT(header, claimsSet)
        jwt.sign(RSASSASigner(rsaKeyB)) // 서명 키: B → 검증 실패

        assertThrows<CognitoJwtException> {
            cognitoClient.validateToken(jwt.serialize())
        }
    }
}
