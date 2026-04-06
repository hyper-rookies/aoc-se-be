package com.aoc

import com.aoc.member.infra.CognitoClient
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(properties = ["spring.profiles.active="])
class AocSeApplicationTests {

    @MockBean
    private lateinit var cognitoClient: CognitoClient

    @Test
    fun contextLoads() {
    }
}
