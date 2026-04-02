package com.liveklass.notification.domain.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxStatus {
    INIT("발행 대기"),
    PROCESSING("처리 중"), // 다중 인스턴스 환경에서 중복 발송 방지를 위한 상태
    COMPLETED("전송 완료"),
    FAILED("최종 실패");

    private final String description;
}
