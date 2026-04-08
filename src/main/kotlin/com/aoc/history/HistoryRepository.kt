package com.aoc.history

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HistoryRepository : JpaRepository<History, String>
