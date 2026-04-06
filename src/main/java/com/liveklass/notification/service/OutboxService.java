package com.liveklass.notification.service;

import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationSender;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxQueryRepository;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import jakarta.persistence.LockTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OutboxService {

    private final Map<NotificationChannel, NotificationSender> senderMap;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationOutboxQueryRepository queryRepository;

    @Lazy
    @Autowired
    private OutboxService self;

    public OutboxService(List<NotificationSender> senders,
                         NotificationOutboxRepository outboxRepository,
                         NotificationRepository notificationRepository,
                         JdbcTemplate jdbcTemplate,
                         NotificationOutboxQueryRepository queryRepository) {
        this.senderMap = senders.stream()
            .collect(Collectors.toMap(NotificationSender::getChannel, Function.identity()));
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.queryRepository = queryRepository;
    }

    @Transactional
    public Long create(Long notificationId, Long receiverId, NotificationType type, LocalDateTime scheduledAt) {
        LocalDateTime nextRetryAt = (scheduledAt != null) ? scheduledAt : LocalDateTime.now();
        LocalDateTime expiredAt = calculateExpiredAt(type, scheduledAt);

        NotificationOutbox outbox = NotificationOutbox.builder()
            .notificationId(notificationId)
            .receiverId(receiverId)
            .type(type)
            .status(OutboxStatus.INIT)
            .nextRetryAt(nextRetryAt)
            .expiredAt(expiredAt)
            .build();
        outboxRepository.save(outbox);
        return outbox.getId();
    }

    @Transactional
    public void bulkCreate(Long notificationId, List<Long> receiverIds, NotificationType type, LocalDateTime scheduledAt) {
        LocalDateTime nextRetryAt = (scheduledAt != null) ? scheduledAt : LocalDateTime.now();
        LocalDateTime expiredAt = calculateExpiredAt(type, scheduledAt);
        LocalDateTime now = LocalDateTime.now();

        String sql = """
            INSERT INTO notification_outbox (notification_id, receiver_id, type, status, retry_count, is_read, next_retry_at, expired_at, created_at)
            VALUES (?, ?, ?, 'INIT', 0, 0, ?, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, receiverIds, receiverIds.size(), (ps, receiverId) -> {
            ps.setLong(1, notificationId);
            ps.setLong(2, receiverId);
            ps.setString(3, type.name());
            ps.setObject(4, nextRetryAt);
            ps.setObject(5, expiredAt);
            ps.setObject(6, now);
        });
    }

    /**
     * 스케줄러용: SKIP LOCKED으로 후보 ID만 조회 후 즉시 커밋.
     * findPendingTasks의 PESSIMISTIC_WRITE 락은 이 짧은 TX 안에서만 유지된다.
     */
    @Transactional
    public List<Long> findPendingTaskIds(int limit) {
        return queryRepository.findPendingTasks(limit)
            .stream().map(NotificationOutbox::getId).toList();
    }

    /**
     * 이벤트 리스너 & 스케줄러 공용 진입점.
     * 트랜잭션 없음 — claimTask / send / recordResult 각각 짧은 독립 TX로 분리.
     */
    public void processTask(Long outboxId) {
        Optional<SendContext> ctxOpt = self.claimTask(outboxId);
        if (ctxOpt.isEmpty()) return;

        SendContext ctx = ctxOpt.get();
        try {
            NotificationSender sender = senderMap.get(ctx.notification().getChannel());
            if (sender == null) {
                throw new IllegalStateException("지원하지 않는 발송 채널: " + ctx.notification().getChannel());
            }
            sender.send(ctx.notification(), ctx.receiverId());
            self.recordSuccess(outboxId, ctx.notificationId());
            log.info("[Outbox] 발송 완료. notificationId={}, channel={}", ctx.notificationId(), ctx.notification().getChannel());
        } catch (Exception e) {
            log.error("알림 발송 중 에러 발생: {}", e.getMessage());
            self.recordFailure(outboxId, ctx.notificationId(), e.getMessage());
        }
    }

    /**
     * 짧은 TX: FOR UPDATE 락 획득 → 상태 검증 → PROCESSING 마킹 → Notification 로드.
     * 커밋 즉시 락 해제 — 발송(네트워크 호출) 중 DB 커넥션을 점유하지 않는다.
     */
    @Transactional
    public Optional<SendContext> claimTask(Long outboxId) {
        NotificationOutbox outbox;
        try {
            outbox = outboxRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));
        } catch (LockTimeoutException e) {
            log.info("[Outbox] 락 획득 시간 초과, 스케줄러에게 위임. outboxId={}", outboxId);
            return Optional.empty();
        }

        if (outbox.getStatus() != OutboxStatus.INIT) {
            log.info("[Outbox] 이미 다른 스레드가 작업 중 (status={}), 발송 스킵. outboxId={}", outbox.getStatus(), outboxId);
            return Optional.empty();
        }
        if (outbox.getNextRetryAt().isAfter(LocalDateTime.now())) {
            log.info("[Outbox] 예약 알림 — 발송 시각 미도래, 스케줄러에게 위임. outboxId={}", outboxId);
            return Optional.empty();
        }

        Notification notification = notificationRepository.findById(outbox.getNotificationId())
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        outbox.startProcessing();

        return Optional.of(new SendContext(outbox.getNotificationId(), notification, outbox.getReceiverId()));
    }

    /** 짧은 TX: 발송 성공 기록 */
    @Transactional
    public void recordSuccess(Long outboxId, Long notificationId) {
        outboxRepository.findById(outboxId).ifPresent(NotificationOutbox::complete);
        notificationRepository.findById(notificationId).ifPresent(Notification::markAsSent);
    }

    /** 짧은 TX: 발송 실패 기록 (재시도 예약 또는 FAILED/EXPIRED 처리) */
    @Transactional
    public void recordFailure(Long outboxId, Long notificationId, String errorMessage) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId)
            .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));
        outbox.fail(errorMessage, 4);

        if (outbox.getStatus() == OutboxStatus.FAILED || outbox.getStatus() == OutboxStatus.EXPIRED) {
            notificationRepository.findById(notificationId).ifPresent(Notification::markAsFailed);
            log.warn("[Outbox] 발송 중지 처리 (상태: {}). outboxId={}", outbox.getStatus(), outboxId);
        }
    }

    private LocalDateTime calculateExpiredAt(NotificationType type, LocalDateTime scheduledAt) {
        if (type == NotificationType.LECTURE_REMINDER_D1) {
            LocalDateTime base = (scheduledAt != null) ? scheduledAt : LocalDateTime.now();
            return base.toLocalDate().plusDays(1).atStartOfDay();
        }
        return null;
    }

    public record SendContext(Long notificationId, Notification notification, Long receiverId) {}
}
