package com.liveklass.notification.domain.notification;

public enum NotificationStatus {
    SCHEDULED,  // 예약됨 — 발송 시각 미도래, 인박스 숨김
    SENT,       // 발송 완료 — 인박스 표시
    FAILED      // 최종 실패 — 인박스 숨김
}
