package com.aoc.notification

import com.aoc.notification.dto.NotificationSettingResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationSettingRepository: NotificationSettingRepository
) {

    fun getSettings(userId: String): NotificationSettingResponse {
        val setting = notificationSettingRepository.findByMemberId(userId)
            ?: error("NotificationSetting not found for member $userId")
        return NotificationSettingResponse.from(setting)
    }

    @Transactional
    fun updateSettings(userId: String, inquiryAlert: Boolean, marketingAlert: Boolean) {
        val setting = notificationSettingRepository.findByMemberId(userId)
            ?: error("NotificationSetting not found for member $userId")
        setting.inquiryAlert = inquiryAlert
        setting.marketingAlert = marketingAlert
    }
}
