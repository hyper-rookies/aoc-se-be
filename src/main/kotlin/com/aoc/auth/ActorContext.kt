package com.aoc.auth

import org.springframework.stereotype.Component

data class ActorInfo(
    val actorId: String,
    val operatorId: String?,
    val isShadow: Boolean
)

@Component("actorContext")
object ActorContext {
    private val holder = ThreadLocal<ActorInfo>()

    fun set(actorId: String, operatorId: String?, isShadow: Boolean) {
        holder.set(ActorInfo(actorId, operatorId, isShadow))
    }

    fun get(): ActorInfo? = holder.get()

    fun clear() = holder.remove()

    fun isShadow(): Boolean = holder.get()?.isShadow ?: false
}
