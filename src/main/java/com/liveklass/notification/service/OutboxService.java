package com.liveklass.notification.service;

import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationSender;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxService {

    private final NotificationSender notificationSender;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public Long create(Long notificationId, NotificationType type) {
        NotificationOutbox outbox = NotificationOutbox.builder()
            .notificationId(notificationId)
            .type(type)
            .status(OutboxStatus.INIT)
            .nextRetryAt(LocalDateTime.now())
            .build();
        outboxRepository.save(outbox);
        return notificationId;
    }

    @Transactional
    public void process(Long outboxId) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId)
            .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));

        try {
            // 엔티티의 비즈니스 로직 활용 (상태: PROCESSING, 시간 기록)
            outbox.startProcessing();

            Notification notification = notificationRepository.findById(outbox.getNotificationId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

            // [발송 로직] - 실제 외부 API 호출이 일어나는 곳
            notificationSender.send(notification);

            // 성공 시 마감 처리 (상태: COMPLETED)
            outbox.complete();

        } catch (Exception e) {
            log.error("알림 발송 중 에러 발생: {}", e.getMessage());
            // 실패 시 재시도 로직 실행 (최대 3회 재시도 가정)
            outbox.fail(e.getMessage(), 3);
        }
    }
}
