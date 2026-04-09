package com.aoc.notification.dto

data class UpdateNotificationRequest(
    val inquiryAlert: Boolean,
    val marketingAlert: Boolean
)
