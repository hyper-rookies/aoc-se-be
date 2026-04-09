package com.aoc.member.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateMemberRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 50)
    val name: String
)
