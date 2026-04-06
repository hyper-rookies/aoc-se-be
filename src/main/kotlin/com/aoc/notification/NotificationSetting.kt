package com.aoc.notification

import com.aoc.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "notification_setting")
class NotificationSetting(
    val memberId: String,
    var inquiryAlert: Boolean = true,
    var marketingAlert: Boolean = false
) : BaseEntity()
