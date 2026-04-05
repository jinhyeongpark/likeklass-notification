package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "벌크 알림 발송 요청")
public record BulkNotificationRequestDto(

    @Schema(description = "수신자 ID 목록 (최대 10,000명)", example = "[1, 2, 3]")
    @NotEmpty @Size(max = 10000) List<Long> receiverIds,

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    @NotNull NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    @NotNull NotificationChannel channel,

    @Schema(description = "알림 제목 (생략 시 템플릿에서 자동 생성)", example = "결제가 확정되었습니다")
    String title,

    @Schema(description = "알림 본문 (생략 시 템플릿에서 자동 생성)", example = "39,000원 결제가 완료되었습니다.")
    String content,

    @Schema(
        description = "이벤트 고유 식별자. {type}:{receiverId}:{eventId} 조합으로 수신자별 멱등성 키를 생성합니다.",
        example = "campaign-2024-spring"
    )
    @NotBlank String eventId,

    @Schema(description = "예약 발송 시각. null이면 즉시 발송.", example = "2024-04-03T09:00:00", nullable = true)
    LocalDateTime scheduledAt,

    @Schema(description = "부가 참조 데이터 (템플릿 플레이스홀더 치환용)", example = "{\"lectureId\": 7}")
    Map<String, Object> referenceData

) {}
