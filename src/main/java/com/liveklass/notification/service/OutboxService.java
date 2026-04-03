package com.liveklass.notification.service;

import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationSender;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class OutboxService {

    private final Map<NotificationChannel, NotificationSender> senderMap;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;

    public OutboxService(List<NotificationSender> senders,
                         NotificationOutboxRepository outboxRepository,
                         NotificationRepository notificationRepository) {
        this.senderMap = senders.stream()
            .collect(Collectors.toMap(NotificationSender::getChannel, Function.identity()));
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Long create(Long notificationId, NotificationType type, LocalDateTime scheduledAt) {
        // scheduledAt이 있으면 해당 시각에 발송, 없으면 즉시 처리 대기
        LocalDateTime nextRetryAt = (scheduledAt != null) ? scheduledAt : LocalDateTime.now();

        NotificationOutbox outbox = NotificationOutbox.builder()
            .notificationId(notificationId)
            .type(type)
            .status(OutboxStatus.INIT)
            .nextRetryAt(nextRetryAt)
            .build();
        outboxRepository.save(outbox);
        return outbox.getId();
    }

    @Transactional
    public void process(Long outboxId) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId)
            .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));

        // 예약 발송: 아직 발송 시각이 되지 않았으면 스킵 (이벤트 리스너의 즉시 호출 차단)
        if (outbox.getNextRetryAt().isAfter(LocalDateTime.now())) {
            log.info("[Outbox] 예약 알림 — 발송 시각 미도래, 스케줄러에게 위임. outboxId={}", outboxId);
            return;
        }

        Notification notification = notificationRepository.findById(outbox.getNotificationId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        try {
            outbox.startProcessing();

            NotificationSender sender = senderMap.get(notification.getChannel());
            if (sender == null) {
                throw new IllegalStateException("지원하지 않는 발송 채널: " + notification.getChannel());
            }
            sender.send(notification);

            outbox.complete();
            notification.markAsSent(); // 인박스 노출 상태로 전환
            log.info("[Outbox] 발송 완료. notificationId={}, channel={}", notification.getId(), notification.getChannel());

        } catch (Exception e) {
            log.error("알림 발송 중 에러 발생: {}", e.getMessage());
            outbox.fail(e.getMessage(), 3);

            // 최종 실패 시 Notification도 FAILED 처리
            if (outbox.getStatus() == OutboxStatus.FAILED) {
                notification.markAsFailed();
                log.warn("[Outbox] 최종 실패 처리. notificationId={}", notification.getId());
            }
        }
    }
}
