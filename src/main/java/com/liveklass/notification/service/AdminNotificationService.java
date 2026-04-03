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
        NotificationOutbox outbox = outboxRepository.findByNotificationId(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));

        if (outbox.getStatus() != OutboxStatus.FAILED) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE); // 재시도는 FAILED 상태만 가능 
        }

        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // 1. 수동 재시도: 기존 재시도 횟수 무시하고 완전 초기화
        outbox.manualRetry();

        // 2. Notification 상태도 SENT로 다시 전환
        notification.revertToSent();

        log.info("[Admin] 알림 수동 재전송 트리거 완료. notificationId={}, receiverId={}",
            notificationId, notification.getReceiverId());
    }
}
