package com.aoc.notification

import com.aoc.notification.dto.NotificationSettingResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationServiceTest {

    private lateinit var notificationSettingRepository: NotificationSettingRepository
    private lateinit var notificationService: NotificationService

    @BeforeEach
    fun setUp() {
        notificationSettingRepository = mock()
        notificationService = NotificationService(notificationSettingRepository)
    }

    @Test
    fun `getSettings는 멤버의 알림 설정을 반환한다`() {
        val setting = NotificationSetting(memberId = "user-001", inquiryAlert = true, marketingAlert = false)
        whenever(notificationSettingRepository.findByMemberId("user-001")).thenReturn(setting)

        val result = notificationService.getSettings("user-001")

        assertEquals(NotificationSettingResponse(inquiryAlert = true, marketingAlert = false), result)
    }

    @Test
    fun `getSettings는 설정이 없으면 예외를 던진다`() {
        whenever(notificationSettingRepository.findByMemberId("ghost-id")).thenReturn(null)

        assertThrows<IllegalStateException> {
            notificationService.getSettings("ghost-id")
        }
    }

    @Test
    fun `updateSettings는 알림 설정 값을 변경한다`() {
        val setting = NotificationSetting(memberId = "user-001", inquiryAlert = true, marketingAlert = false)
        whenever(notificationSettingRepository.findByMemberId("user-001")).thenReturn(setting)

        notificationService.updateSettings("user-001", inquiryAlert = false, marketingAlert = true)

        assertFalse(setting.inquiryAlert)
        assertTrue(setting.marketingAlert)
    }

    @Test
    fun `updateSettings는 설정이 없으면 예외를 던진다`() {
        whenever(notificationSettingRepository.findByMemberId("ghost-id")).thenReturn(null)

        assertThrows<IllegalStateException> {
            notificationService.updateSettings("ghost-id", inquiryAlert = true, marketingAlert = false)
        }
    }
}
