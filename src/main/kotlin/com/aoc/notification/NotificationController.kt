package com.aoc.notification

import com.aoc.auth.ActorContext
import com.aoc.common.ApiResponse
import com.aoc.notification.dto.NotificationSettingResponse
import com.aoc.notification.dto.UpdateNotificationRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notification-settings")
class NotificationController(
    private val notificationService: NotificationService
) {

    @GetMapping
    fun getSettings(): ResponseEntity<ApiResponse<NotificationSettingResponse>> {
        val userId = ActorContext.get()!!.actorId
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getSettings(userId)))
    }

    @PutMapping
    @PreAuthorize("hasRole('MARKETER') and !@actorContext.isShadow()")
    fun updateSettings(@RequestBody req: UpdateNotificationRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = ActorContext.get()!!.actorId
        notificationService.updateSettings(userId, req.inquiryAlert, req.marketingAlert)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }
}
