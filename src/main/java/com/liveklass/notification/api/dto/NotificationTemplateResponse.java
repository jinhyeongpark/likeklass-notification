package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.template.NotificationTemplate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 템플릿 조회 응답")
public record NotificationTemplateResponse(

    @Schema(description = "템플릿 ID", example = "1")
    Long id,

    @Schema(description = "알림 타입", example = "PAYMENT_CONFIRMED")
    NotificationType type,

    @Schema(description = "발송 채널", example = "EMAIL")
    NotificationChannel channel,

    @Schema(description = "제목 템플릿", example = "{lectureName} 결제가 확정되었습니다")
    String titleTemplate,

    @Schema(description = "본문 템플릿", example = "{amount}원 결제가 완료되었습니다. 강의명: {lectureName}")
    String contentTemplate

) {
    public static NotificationTemplateResponse from(NotificationTemplate template) {
        return new NotificationTemplateResponse(
            template.getId(),
            template.getType(),
            template.getChannel(),
            template.getTitleTemplate(),
            template.getContentTemplate()
        );
    }
}
