package com.liveklass.notification.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===================== 400 Bad Request =====================
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON-400", "입력값이 올바르지 않습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "COMMON-405", "잘못된 타입의 값입니다."),
    UNSUPPORTED_NOTIFICATION_TYPE(HttpStatus.BAD_REQUEST, "NOTI-001", "지원하지 않는 알림 타입입니다."),
    NOTIFICATION_ALREADY_READ(HttpStatus.BAD_REQUEST, "NOTI-002", "이미 읽은 알림입니다."),

    // ===================== 404 Not Found =====================
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "요청한 리소스를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI-404", "알림을 찾을 수 없습니다."),
    OUTBOX_NOT_FOUND(HttpStatus.NOT_FOUND, "OUTBOX-404", "Outbox 이벤트를 찾을 수 없습니다."),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "TMPL-404", "알림 템플릿을 찾을 수 없습니다."),

    // ===================== 409 Conflict =====================
    TEMPLATE_ALREADY_EXISTS(HttpStatus.CONFLICT, "TMPL-409", "동일 타입+채널 조합의 템플릿이 이미 존재합니다."),
    TEMPLATE_PLACEHOLDER_MISSING(HttpStatus.BAD_REQUEST, "TMPL-400", "referenceData에 템플릿 플레이스홀더에 해당하는 키가 없습니다."),

    // ===================== 422 Unprocessable Entity =====================
    NOTIFICATION_CONTENT_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "NOTI-422", "알림 제목 또는 본문이 비어 있고, 해당 타입+채널 조합의 템플릿도 등록되어 있지 않습니다."),

    // ===================== 500 Internal Server Error =====================
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "서버 내부 오류가 발생했습니다."),
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NOTI-500", "알림 전송에 실패했습니다."),
    OUTBOX_MAX_RETRY_EXCEEDED(HttpStatus.INTERNAL_SERVER_ERROR, "OUTBOX-501", "Outbox 최대 재시도 횟수를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String code; // 추가된 필드
    private final String message;
}
