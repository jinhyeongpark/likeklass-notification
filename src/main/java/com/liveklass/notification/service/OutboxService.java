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
import jakarta.persistence.LockTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class OutboxService {

    private final Map<NotificationChannel, NotificationSender> senderMap;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    public OutboxService(List<NotificationSender> senders,
                         NotificationOutboxRepository outboxRepository,
                         NotificationRepository notificationRepository,
                         JdbcTemplate jdbcTemplate) {
        this.senderMap = senders.stream()
            .collect(Collectors.toMap(NotificationSender::getChannel, Function.identity()));
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long create(Long notificationId, Long receiverId, NotificationType type, LocalDateTime scheduledAt) {
        LocalDateTime nextRetryAt = (scheduledAt != null) ? scheduledAt : LocalDateTime.now();

        NotificationOutbox outbox = NotificationOutbox.builder()
            .notificationId(notificationId)
            .receiverId(receiverId)
            .type(type)
            .status(OutboxStatus.INIT)
            .nextRetryAt(nextRetryAt)
            .build();
        outboxRepository.save(outbox);
        return outbox.getId();
    }

    @Transactional
    public void bulkCreate(Long notificationId, List<Long> receiverIds, NotificationType type, LocalDateTime scheduledAt) {
        LocalDateTime nextRetryAt = (scheduledAt != null) ? scheduledAt : LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();

        String sql = """
            INSERT INTO notification_outbox (notification_id, receiver_id, type, status, retry_count, next_retry_at, created_at)
            VALUES (?, ?, ?, 'INIT', 0, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, receiverIds, receiverIds.size(), (ps, receiverId) -> {
            ps.setLong(1, notificationId);
            ps.setLong(2, receiverId);
            ps.setString(3, type.name());
            ps.setObject(4, nextRetryAt);
            ps.setObject(5, now);
        });
    }

    @Transactional
    public void process(Long outboxId) {
        NotificationOutbox outbox;
        try {
            outbox = outboxRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));
        } catch (LockTimeoutException e) {
            // 3초 안에 락을 획득하지 못한 경우 (스케줄러가 이미 작업 중)
            // 이벤트 리스너는 조용히 양보하고 스케줄러에게 위임
            log.info("[Outbox] 락 획득 시간 초과, 스케줄러에게 위임. outboxId={}", outboxId);
            return;
        }

        // Current Read(최신 커밋 데이터) 후 상태 검사
        // 다른 스레드(스케줄러 등)가 이미 PROCESSING 또는 COMPLETED로 바꾼 경우 깔끔하게 이탈
        if (outbox.getStatus() != OutboxStatus.INIT) {
            log.info("[Outbox] 이미 다른 스레드가 작업 중 (status={}), 발송 스킵. outboxId={}",
                outbox.getStatus(), outboxId);
            return;
        }
        // 예약 발송: 아직 발송 시각이 되지 않았으면 스킵 (이벤트 리스너의 즉시 호출 차단)
        if (outbox.getNextRetryAt().isAfter(LocalDateTime.now())) {
            log.info("[Outbox] 예약 알림 — 발송 시각 미도래, 스케줄러에게 위임. outboxId={}", outboxId);
            return;
        }

        Notification notification = notificationRepository.findById(outbox.getNotificationId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        try {
            outbox.startProcessing();

            // 읽음 시 발송 생략 정책
            if (Boolean.TRUE.equals(notification.getIsRead())) {
                outbox.complete();
                log.info("[Outbox] 이미 읽은 알림이므로 발송 스킵. notificationId={}", notification.getId());
                return;
            }

            NotificationSender sender = senderMap.get(notification.getChannel());
            if (sender == null) {
                throw new IllegalStateException("지원하지 않는 발송 채널: " + notification.getChannel());
            }
            sender.send(notification, outbox.getReceiverId());

            outbox.complete();
            notification.markAsSent();
            log.info("[Outbox] 발송 완료. notificationId={}, channel={}", notification.getId(), notification.getChannel());

        } catch (Exception e) {
            log.error("알림 발송 중 에러 발생: {}", e.getMessage());
            outbox.fail(e.getMessage(), 3);

            if (outbox.getStatus() == OutboxStatus.FAILED || outbox.getStatus() == OutboxStatus.EXPIRED) {
                notification.markAsFailed();
                log.warn("[Outbox] 발송 중지 처리 (상태: {}). notificationId={}", outbox.getStatus(), notification.getId());
            }
        }
    }
}
