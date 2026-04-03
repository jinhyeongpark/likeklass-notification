package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "알림 발송 요청")
public record NotificationRequestDto(

    @Schema(description = "수신자 ID", example = "42")
    @NotNull Long receiverId,

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    @NotNull NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    @NotNull NotificationChannel channel,

    @Schema(description = "알림 제목", example = "결제가 확정되었습니다")
    String title,

    @Schema(description = "알림 본문. 생략 시 해당 타입+채널의 템플릿에서 자동 생성됩니다.", example = "39,000원 결제가 완료되었습니다.")
    String content,

    @Schema(
        description = "이벤트 발생 단위 고유 식별자 (결제 ID, 취소 ID 등). 서버가 {type}:{receiverId}:{eventId} 형식으로 멱등성 키를 자동 조합합니다.",
        example = "txn-20240403-abc123"
    )
    @NotBlank String eventId,

    @Schema(
        description = "예약 발송 시각. null이면 즉시 발송, 과거 시각이면 즉시 발송으로 처리됩니다.",
        example = "2024-04-03T09:00:00",
        nullable = true
    )
    LocalDateTime scheduledAt,

    @Schema(description = "부가 참조 데이터 (강의 ID, 이벤트 ID 등)", example = "{\"lectureId\": 7, \"amount\": 39000}")
    Map<String, Object> referenceData

) {}
