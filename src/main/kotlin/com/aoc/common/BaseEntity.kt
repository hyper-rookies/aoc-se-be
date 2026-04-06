package com.aoc.common

import com.aoc.history.HistoryEntityListener
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.f4b6a3.ulid.UlidCreator
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.Transient
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(HistoryEntityListener::class)
abstract class BaseEntity {

    @Id
    val id: String = UlidCreator.getUlid().toString()

    val createdAt: LocalDateTime = LocalDateTime.now()
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @Transient
    var snapshot: String? = null
        protected set

    @PostLoad
    fun takeSnapshot() {
        snapshot = jacksonObjectMapper().writeValueAsString(this)
    }
}
