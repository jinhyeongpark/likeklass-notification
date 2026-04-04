package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "권리자용 실패 알림 목록 응답")
public record FailedNotificationResponse(
    @Schema(description = "알림 ID", example = "10")
    Long notificationId,

    @Schema(description = "수신자 ID", example = "42")
    Long receiverId,

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    NotificationChannel channel,

    @Schema(description = "알림 제목", example = "결제가 확정되었습니다")
    String title,

    @Schema(description = "마지막 실패 사유", example = "Connection timeout: email server unreachable")
    String lastError,

    @Schema(description = "최종 실패 처리된 시각", example = "2024-04-03T09:00:10")
    LocalDateTime failedAt
) {
}
