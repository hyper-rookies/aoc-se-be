package com.aoc.member.presentation.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class WorkEmailVerifyRequest(
    @field:NotBlank
    @field:Email
    val workEmail: String
)
