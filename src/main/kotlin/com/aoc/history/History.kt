package com.aoc.history

import com.github.f4b6a3.ulid.UlidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "history")
class History(
    @Id
    val id: String = UlidCreator.getUlid().toString(),
    val entityType: String,
    val entityId: String,
    @Enumerated(EnumType.STRING)
    val action: HistoryAction,
    @Column(columnDefinition = "jsonb")
    var beforeValue: String? = null,
    @Column(columnDefinition = "jsonb")
    var afterValue: String? = null,
    val actorId: String,
    val operatorId: String? = null,
    @Column(name = "shadow_id", length = 36)
    var shadowId: String? = null,
    val isShadow: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
