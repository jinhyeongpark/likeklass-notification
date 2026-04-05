package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "알림 발송 집계 상태 조회 응답")
public record NotificationStatusSummaryResponse(

    @Schema(description = "알림 ID", example = "1")
    Long notificationId,

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    NotificationChannel channel,

    @Schema(description = "전체 수신자 수", example = "1000")
    long totalCount,

    @Schema(description = "발송 완료 수", example = "980")
    long completedCount,

    @Schema(description = "최종 실패 수", example = "5")
    long failedCount,

    @Schema(description = "대기/처리 중 수", example = "10")
    long pendingCount,

    @Schema(description = "TTL 초과 폐기 수", example = "5")
    long expiredCount

) {
    public static NotificationStatusSummaryResponse of(
        Long notificationId,
        NotificationType type,
        NotificationChannel channel,
        List<NotificationOutbox> outboxes
    ) {
        long completed = outboxes.stream().filter(o -> o.getStatus() == OutboxStatus.COMPLETED).count();
        long failed = outboxes.stream().filter(o -> o.getStatus() == OutboxStatus.FAILED).count();
        long expired = outboxes.stream().filter(o -> o.getStatus() == OutboxStatus.EXPIRED).count();
        long pending = outboxes.stream()
            .filter(o -> o.getStatus() == OutboxStatus.INIT || o.getStatus() == OutboxStatus.PROCESSING)
            .count();

        return new NotificationStatusSummaryResponse(
            notificationId, type, channel, outboxes.size(), completed, failed, pending, expired
        );
    }
}
