package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 템플릿 생성/수정 요청")
public record NotificationTemplateRequest(

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    @NotNull NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    @NotNull NotificationChannel channel,

    @Schema(description = "제목 템플릿. {key} 형식의 플레이스홀더를 referenceData로 치환합니다.", example = "{lectureName} 결제가 확정되었습니다")
    @NotNull String titleTemplate,

    @Schema(description = "본문 템플릿. {key} 형식의 플레이스홀더를 referenceData로 치환합니다.", example = "{amount}원 결제가 완료되었습니다. 강의명: {lectureName}")
    @NotNull String contentTemplate

) {}
