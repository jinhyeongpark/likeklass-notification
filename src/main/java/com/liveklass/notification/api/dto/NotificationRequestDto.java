package com.liveklass.notification.api.dto;

import com.liveklass.notification.domain.notification.NotificationType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record NotificationRequestDto(
    @NotNull Long receiverId,
    @NotNull NotificationType type,
    String title,
    @NotNull String content,
    Map<String, Object> referenceData // 이벤트 ID, 강의 ID 등
) {}
