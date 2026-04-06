package com.aoc.common

open class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.message
) : RuntimeException(message)

class MemberNotFoundException : BusinessException(ErrorCode.MEMBER_NOT_FOUND)
class AccessDeniedException : BusinessException(ErrorCode.ACCESS_DENIED)
class ShadowActionNotAllowedException : BusinessException(ErrorCode.SHADOW_ACTION_NOT_ALLOWED)
