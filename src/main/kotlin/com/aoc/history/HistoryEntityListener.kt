package com.aoc.history

import jakarta.persistence.PrePersist
import jakarta.persistence.PreRemove
import jakarta.persistence.PreUpdate

class HistoryEntityListener {

    @PrePersist
    fun onPrePersist(entity: Any) {
    }

    @PreUpdate
    fun onPreUpdate(entity: Any) {
    }

    @PreRemove
    fun onPreRemove(entity: Any) {
    }
}
