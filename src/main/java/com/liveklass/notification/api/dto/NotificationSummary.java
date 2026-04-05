package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "수신함 알림 항목")
public record NotificationSummary(

    @Schema(description = "알림 ID", example = "3")
    Long notificationId,

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    NotificationChannel channel,

    @Schema(description = "알림 제목", example = "결제가 확정되었습니다")
    String title,

    @Schema(description = "알림 본문", example = "39,000원 결제가 완료되었습니다.")
    String content,

    @Schema(description = "읽음 여부", example = "false")
    Boolean isRead,

    @Schema(description = "읽은 시각 (읽지 않았으면 null)", example = "2024-04-03T10:00:00", nullable = true)
    LocalDateTime readAt,

    @Schema(description = "예약 발송 시각 (즉시 발송이면 null)", example = "2024-04-03T09:00:00", nullable = true)
    LocalDateTime scheduledAt

) {
}
