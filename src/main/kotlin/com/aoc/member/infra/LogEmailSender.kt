package com.aoc.member.infra

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!prod")
class LogEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(to: String, code: String) {
        log.info("[이메일 인증] to={} 인증 코드: {}", to, code)
    }
}
