package com.aoc.history

import java.time.LocalDateTime

data class HistoryResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val action: HistoryAction,
    val beforeValue: String?,
    val afterValue: String?,
    val actorId: String,
    val operatorId: String?,
    val shadowId: String?,
    val isShadow: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(history: History) = HistoryResponse(
            id = history.id,
            entityType = history.entityType,
            entityId = history.entityId,
            action = history.action,
            beforeValue = history.beforeValue,
            afterValue = history.afterValue,
            actorId = history.actorId,
            operatorId = history.operatorId,
            shadowId = history.shadowId,
            isShadow = history.isShadow,
            createdAt = history.createdAt
        )
    }
}
