package com.aoc.history

import com.aoc.auth.ActorContext
import com.aoc.common.BaseEntity
import com.aoc.common.SpringApplicationContext
import jakarta.persistence.PrePersist
import jakarta.persistence.PreRemove
import jakarta.persistence.PreUpdate
import org.springframework.context.ApplicationEventPublisher

class HistoryEntityListener {

    @PrePersist
    fun onPrePersist(entity: Any) {
        if (entity !is BaseEntity) return
        publish(EntityCreatedEvent(entity, ActorContext.get()))
    }

    @PreUpdate
    fun onPreUpdate(entity: Any) {
        if (entity !is BaseEntity) return
        publish(EntityUpdatedEvent(entity, entity.snapshot, ActorContext.get()))
    }

    @PreRemove
    fun onPreRemove(entity: Any) {
        if (entity !is BaseEntity) return
        publish(EntityDeletedEvent(entity, ActorContext.get()))
    }

    private fun publish(event: Any) {
        runCatching {
            SpringApplicationContext.getBean(ApplicationEventPublisher::class.java).publishEvent(event)
        }
    }
}
