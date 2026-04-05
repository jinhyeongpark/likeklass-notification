package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "수신자별 발송 상태 응답")
public record ReceiverStatusResponse(

    @Schema(description = "수신자 ID", example = "42")
    Long receiverId,

    @Schema(description = "Outbox 처리 상태", example = "COMPLETED")
    OutboxStatus outboxStatus,

    @Schema(description = "읽음 여부", example = "false")
    Boolean isRead,

    @Schema(description = "현재까지 재시도 횟수", example = "0")
    int retryCount,

    @Schema(description = "다음 재시도 예정 시각 (대기 중일 때만 값 존재)", nullable = true)
    LocalDateTime nextRetryAt,

    @Schema(description = "마지막 실패 사유 (성공 시 null)", nullable = true)
    String lastError

) {
    public static ReceiverStatusResponse from(NotificationOutbox outbox) {
        return new ReceiverStatusResponse(
            outbox.getReceiverId(),
            outbox.getStatus(),
            outbox.getIsRead(),
            outbox.getRetryCount(),
            outbox.getNextRetryAt(),
            outbox.getLastError()
        );
    }
}
