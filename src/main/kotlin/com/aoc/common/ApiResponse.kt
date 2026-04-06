package com.aoc.common

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null
) {
    companion object {
        fun <T> ok(data: T) = ApiResponse(success = true, data = data)
        fun error(errorCode: ErrorCode) = ApiResponse<Nothing>(
            success = false,
            message = errorCode.message,
            code = errorCode.code
        )
    }
}
