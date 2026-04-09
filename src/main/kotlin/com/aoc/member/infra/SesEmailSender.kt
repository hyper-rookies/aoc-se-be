package com.aoc.member.infra

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.Body
import software.amazon.awssdk.services.ses.model.Content
import software.amazon.awssdk.services.ses.model.Destination
import software.amazon.awssdk.services.ses.model.Message
import software.amazon.awssdk.services.ses.model.SendEmailRequest

@Component
@Profile("prod")
class SesEmailSender(
    @Value("\${ses.from-address}") private val fromAddress: String
) : EmailSender {

    private val client = SesClient.create()  // AWS_REGION 환경변수 + ECS 태스크 역할로 자동 인증

    override fun send(to: String, code: String) {
        val request = SendEmailRequest.builder()
            .source(fromAddress)
            .destination(Destination.builder().toAddresses(to).build())
            .message(
                Message.builder()
                    .subject(Content.builder().data("업무 이메일 인증 코드").charset("UTF-8").build())
                    .body(
                        Body.builder()
                            .text(Content.builder().data("인증 코드: $code\n\n5분 이내에 입력해주세요.").charset("UTF-8").build())
                            .build()
                    )
                    .build()
            )
            .build()
        client.sendEmail(request)
    }
}
