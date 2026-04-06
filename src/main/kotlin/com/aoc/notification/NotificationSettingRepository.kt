package com.aoc.notification

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingRepository : JpaRepository<NotificationSetting, String> {
    fun findByMemberId(memberId: String): NotificationSetting?
}
