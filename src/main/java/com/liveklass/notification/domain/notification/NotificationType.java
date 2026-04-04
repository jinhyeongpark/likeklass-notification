package com.liveklass.notification.domain.notification;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    ENROLLMENT_COMPLETED("수강 신청 완료", Duration.ofHours(2)),
    PAYMENT_CONFIRMED("결제 확정", Duration.ofMinutes(10)),
    LECTURE_REMINDER_D1("강의 시작 D-1", null),
    CANCELLATION_PROCESSED("취소 처리", Duration.ofHours(2));

    private final String description;
    private final Duration ttl;
}
