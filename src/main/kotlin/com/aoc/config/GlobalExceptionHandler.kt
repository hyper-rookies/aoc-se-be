package com.aoc.config

import com.aoc.common.ApiResponse
import com.aoc.common.BusinessException
import com.aoc.common.ErrorCode
import com.aoc.member.infra.CognitoJwtException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.error(e.errorCode))
    }

    @ExceptionHandler(CognitoJwtException::class)
    fun handleCognitoJwtException(e: CognitoJwtException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(ErrorCode.INVALID_COGNITO_TOKEN.status)
            .body(ApiResponse.error(ErrorCode.INVALID_COGNITO_TOKEN))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .badRequest()
            .body(ApiResponse(success = false, message = e.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(ErrorCode.INTERNAL_SERVER_ERROR.status)
            .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR))
    }
}
