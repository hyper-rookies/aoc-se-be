package com.aoc.member.infra

interface EmailSender {
    fun send(to: String, code: String)
}
