package com.aoc.history

enum class HistoryAction {
    // EntityListener 자동 기록
    PERSIST, UPDATE, DELETE,
    // 명시적 기록
    LOGIN,
    SHADOW_LOGIN_START,
    SHADOW_LOGIN_END
}
