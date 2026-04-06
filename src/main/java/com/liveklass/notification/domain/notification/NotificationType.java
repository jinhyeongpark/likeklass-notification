package com.liveklass.notification.domain.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    ENROLLMENT_COMPLETED("수강 신청 완료"),
    PAYMENT_CONFIRMED("결제 확정"),
    LECTURE_REMINDER_D1("강의 시작 D-1"),
    CANCELLATION_PROCESSED("취소 처리");

    private final String description;
}
