package com.liveklass.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "벌크 알림 발송 요청 처리 결과")
public record BulkNotificationResponse(

    @Schema(description = "생성된 공통 알림 ID (전원 멱등성 필터링 시 null)", example = "42", nullable = true)
    Long notificationId,

    @Schema(description = "요청된 전체 수신자 수", example = "1000")
    int totalRequested,

    @Schema(description = "Outbox에 등록된 수신자 수", example = "998")
    int accepted,

    @Schema(description = "멱등성 키 중복으로 필터링된 수신자 수", example = "2")
    int skipped

) {}
