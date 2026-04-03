package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationStatus;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "알림 처리 상태 조회 응답")
public record NotificationStatusResponse(

    @Schema(description = "알림 ID", example = "10")
    Long notificationId,

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    NotificationChannel channel,

    @Schema(description = "알림 상태. SCHEDULED=발송 대기, SENT=발송 완료, FAILED=최종 실패", example = "SENT")
    NotificationStatus status,

    @Schema(description = "예약 발송 시각 (즉시 발송이면 null)", example = "2024-04-03T09:00:00", nullable = true)
    LocalDateTime scheduledAt,

    @Schema(description = "Outbox 처리 상태. INIT=대기, PROCESSING=처리중, COMPLETED=완료, FAILED=최종실패", example = "COMPLETED")
    OutboxStatus outboxStatus,

    @Schema(description = "현재까지 재시도 횟수 (최대 3회)", example = "0")
    int retryCount,

    @Schema(description = "다음 재시도 예정 시각 (재시도 대기 중일 때만 값 존재)", example = "2024-04-03T09:00:10", nullable = true)
    LocalDateTime nextRetryAt,

    @Schema(description = "마지막 실패 사유 (성공 시 null)", example = "Connection timeout: email server unreachable", nullable = true)
    String lastError

) {
    public static NotificationStatusResponse of(Notification notification, NotificationOutbox outbox) {
        return new NotificationStatusResponse(
            notification.getId(),
            notification.getType(),
            notification.getChannel(),
            notification.getStatus(),
            notification.getScheduledAt(),
            outbox.getStatus(),
            outbox.getRetryCount(),
            outbox.getNextRetryAt(),
            outbox.getLastError()
        );
    }
}
