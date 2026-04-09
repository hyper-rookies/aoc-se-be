package com.aoc.history

import com.aoc.auth.ActorInfo
import com.aoc.common.BaseEntity
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

data class EntityCreatedEvent(val entity: BaseEntity, val actorInfo: ActorInfo?)
data class EntityUpdatedEvent(val entity: BaseEntity, val snapshot: String?, val actorInfo: ActorInfo?)
data class EntityDeletedEvent(val entity: BaseEntity, val actorInfo: ActorInfo?)

@Component
class HistoryEventHandler(private val historyRepository: HistoryRepository) {

    // snapshot 필드는 @Transient이므로 직렬화에서 제외
    @JsonIgnoreProperties("snapshot")
    private abstract class BaseEntityMixin

    private val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .addMixIn(BaseEntity::class.java, BaseEntityMixin::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleCreated(event: EntityCreatedEvent) {
        saveHistory(event.entity, HistoryAction.PERSIST, null, mapper.writeValueAsString(event.entity), event.actorInfo)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleUpdated(event: EntityUpdatedEvent) {
        saveHistory(event.entity, HistoryAction.UPDATE, event.snapshot, mapper.writeValueAsString(event.entity), event.actorInfo)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleDeleted(event: EntityDeletedEvent) {
        saveHistory(event.entity, HistoryAction.DELETE, mapper.writeValueAsString(event.entity), null, event.actorInfo)
    }

    private fun saveHistory(
        entity: BaseEntity,
        action: HistoryAction,
        before: String?,
        after: String?,
        actor: ActorInfo?
    ) {
        historyRepository.save(
            History(
                entityType = entity::class.simpleName ?: "Unknown",
                entityId = entity.id,
                action = action,
                beforeValue = before,
                afterValue = after,
                actorId = actor?.actorId ?: "system",
                operatorId = actor?.operatorId,
                shadowId = actor?.shadowId,
                isShadow = actor?.isShadow ?: false
            )
        )
    }
}
