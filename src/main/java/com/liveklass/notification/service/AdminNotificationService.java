package com.liveklass.notification.service;

import com.liveklass.notification.api.dto.FailedNotificationResponse;
import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxQueryRepository;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNotificationService {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxQueryRepository outboxQueryRepository;
    private final NotificationRepository notificationRepository;

    public List<FailedNotificationResponse> getFailedNotifications(int limit) {
        return outboxQueryRepository.findFailedNotifications(limit);
    }

    @Transactional
    public void retryFailedNotification(Long notificationId) {
        List<NotificationOutbox> outboxes = outboxRepository.findAllByNotificationId(notificationId);

        if (outboxes.isEmpty()) {
            throw new CustomException(ErrorCode.OUTBOX_NOT_FOUND);
        }

        List<NotificationOutbox> failedOutboxes = outboxes.stream()
            .filter(o -> o.getStatus() == OutboxStatus.FAILED)
            .toList();

        if (failedOutboxes.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE); // 재시도는 FAILED 상태만 가능
        }

        // 1. FAILED 수신자 전체 초기화
        failedOutboxes.forEach(NotificationOutbox::manualRetry);

        // 2. Notification 상태도 SENT로 다시 전환
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.revertToSent();

        log.info("[Admin] 알림 수동 재전송 트리거 완료. notificationId={}, failedCount={}",
            notificationId, failedOutboxes.size());
    }
}
