package com.aoc.common

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    // Auth
    INVALID_COGNITO_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "유효하지 않은 Cognito 토큰입니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "유효하지 않은 액세스 토큰입니다."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "만료된 액세스 토큰입니다."),
    BLACKLISTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "무효화된 토큰입니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "회원을 찾을 수 없습니다."),
    DUPLICATE_MEMBER(HttpStatus.CONFLICT, "MEMBER_002", "이미 가입된 회원입니다."),
    CANNOT_CHANGE_OWN_ROLE(HttpStatus.BAD_REQUEST, "MEMBER_003", "본인의 역할은 변경할 수 없습니다."),

    // Member Status
    MEMBER_DORMANT(HttpStatus.FORBIDDEN, "MEMBER_STATUS_001", "휴면 계정입니다. 고객센터에 문의해주세요."),
    MEMBER_SUSPENDED(HttpStatus.FORBIDDEN, "MEMBER_STATUS_002", "정지된 계정입니다. 고객센터에 문의해주세요."),
    MEMBER_SECURITY_LOCKOUT(HttpStatus.FORBIDDEN, "MEMBER_STATUS_003", "보안 잠금 상태입니다. 잠시 후 다시 시도해주세요."),
    MEMBER_PENDING_DELETION(HttpStatus.FORBIDDEN, "MEMBER_STATUS_004", "탈퇴 처리 중인 계정입니다."),

    // Shadow
    SHADOW_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SHADOW_001", "쉐도우 세션을 찾을 수 없습니다."),
    SHADOW_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "SHADOW_002", "쉐도우 세션이 만료되었습니다."),

    // Permission
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "PERMISSION_001", "접근 권한이 없습니다."),
    SHADOW_ACTION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "PERMISSION_002", "쉐도우 세션 중에는 이 작업을 수행할 수 없습니다."),

    // Email
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_001", "이메일 발송에 실패했습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "EMAIL_002", "유효하지 않은 인증 코드입니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "EMAIL_003", "만료된 인증 코드입니다."),
    EMAIL_VERIFICATION_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_004", "이메일 인증 횟수를 초과했습니다."),

    // Server
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_001", "서버 오류가 발생했습니다.")
}
