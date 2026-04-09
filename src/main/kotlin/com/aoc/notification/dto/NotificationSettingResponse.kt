package com.aoc.notification.dto

import com.aoc.notification.NotificationSetting

data class NotificationSettingResponse(
    val inquiryAlert: Boolean,
    val marketingAlert: Boolean
) {
    companion object {
        fun from(setting: NotificationSetting) = NotificationSettingResponse(
            inquiryAlert = setting.inquiryAlert,
            marketingAlert = setting.marketingAlert
        )
    }
}
