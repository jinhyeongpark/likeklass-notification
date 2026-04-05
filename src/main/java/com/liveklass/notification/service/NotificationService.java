package com.liveklass.notification.service;

import com.liveklass.notification.api.dto.BulkNotificationRequestDto;
import com.liveklass.notification.api.dto.BulkNotificationResponse;
import com.liveklass.notification.api.dto.NotificationRequestDto;
import com.liveklass.notification.api.dto.NotificationStatusSummaryResponse;
import com.liveklass.notification.api.dto.NotificationSummary;
import com.liveklass.notification.api.dto.ReceiverStatusResponse;
import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.idempotency.NotificationIdempotency;
import com.liveklass.notification.domain.idempotency.NotificationIdempotencyRepository;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationQueryRepository;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationStatus;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.template.NotificationTemplate;
import com.liveklass.notification.domain.template.NotificationTemplateRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final NotificationRepository notificationRepository;
    private final NotificationQueryRepository notificationQueryRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationIdempotencyRepository idempotencyRepository;
    private final NotificationTemplateRepository templateRepository;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long requestNotification(NotificationRequestDto request) {
        String idempotencyKey = buildKey(request.type().name(), request.receiverId(), request.eventId());
        LocalDateTime now = LocalDateTime.now();

        Optional<NotificationIdempotency> active =
            idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(idempotencyKey, now);
        if (active.isPresent()) {
            log.info(">>> [Idempotency] TTL 윈도우 내 중복 요청. key={}", idempotencyKey);
            return active.get().getNotificationId();
        }

        try {
            idempotencyRepository.deleteByIdempotencyKey(idempotencyKey);

            String title = request.title();
            String content = request.content();

            Optional<NotificationTemplate> templateOpt =
                templateRepository.findByTypeAndChannel(request.type(), request.channel());

            if (templateOpt.isPresent()) {
                NotificationTemplate template = templateOpt.get();
                template.validateReferenceData(request.referenceData());
                if (title == null || title.isBlank()) {
                    title = template.resolveTitle(request.referenceData());
                }
                if (content == null || content.isBlank()) {
                    content = template.resolveContent(request.referenceData());
                }
            }

            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new CustomException(ErrorCode.NOTIFICATION_CONTENT_REQUIRED);
            }

            boolean isScheduled = request.scheduledAt() != null && request.scheduledAt().isAfter(now);
            NotificationStatus status = isScheduled ? NotificationStatus.SCHEDULED : NotificationStatus.SENT;

            Notification notification = Notification.builder()
                .title(title)
                .content(content)
                .type(request.type())
                .channel(request.channel())
                .status(status)
                .idempotencyKey(idempotencyKey)
                .scheduledAt(request.scheduledAt())
                .referenceData(request.referenceData())
                .build();

            notificationRepository.save(notification);

            idempotencyRepository.save(
                NotificationIdempotency.of(idempotencyKey, notification.getId(), now.plusHours(IDEMPOTENCY_TTL_HOURS))
            );

            Long outboxId = outboxService.create(notification.getId(), request.receiverId(), notification.getType(), request.scheduledAt());
            eventPublisher.publishEvent(new NotificationCreatedEvent(outboxId));

            return notification.getId();

        } catch (DataIntegrityViolationException e) {
            log.warn(">>> [Concurrency] 동시 중복 요청 감지. key={}", idempotencyKey);
            return idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(idempotencyKey, LocalDateTime.now())
                .map(NotificationIdempotency::getNotificationId)
                .orElseThrow(() -> e);
        }
    }

    @Transactional
    public BulkNotificationResponse requestNotificationsBulk(BulkNotificationRequestDto request) {
        LocalDateTime now = LocalDateTime.now();
        int total = request.receiverIds().size();

        String title = request.title();
        String content = request.content();

        Optional<NotificationTemplate> templateOpt =
            templateRepository.findByTypeAndChannel(request.type(), request.channel());

        if (templateOpt.isPresent()) {
            NotificationTemplate template = templateOpt.get();
            template.validateReferenceData(request.referenceData());
            if (title == null || title.isBlank()) {
                title = template.resolveTitle(request.referenceData());
            }
            if (content == null || content.isBlank()) {
                content = template.resolveContent(request.referenceData());
            }
        }

        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.NOTIFICATION_CONTENT_REQUIRED);
        }

        // 수신자별 멱등성 체크
        List<Long> acceptedReceiverIds = new ArrayList<>();
        for (Long receiverId : request.receiverIds()) {
            String key = buildKey(request.type().name(), receiverId, request.eventId());
            if (idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(key, now).isPresent()) {
                log.info(">>> [Idempotency] 벌크 요청 중 중복 수신자 필터링. receiverId={}", receiverId);
                continue;
            }
            acceptedReceiverIds.add(receiverId);
        }

        int skipped = total - acceptedReceiverIds.size();

        if (acceptedReceiverIds.isEmpty()) {
            return new BulkNotificationResponse(null, total, 0, skipped);
        }

        boolean isScheduled = request.scheduledAt() != null && request.scheduledAt().isAfter(now);
        NotificationStatus status = isScheduled ? NotificationStatus.SCHEDULED : NotificationStatus.SENT;

        Notification notification = Notification.builder()
            .title(title)
            .content(content)
            .type(request.type())
            .channel(request.channel())
            .status(status)
            .scheduledAt(request.scheduledAt())
            .referenceData(request.referenceData())
            .build();
        notificationRepository.save(notification);

        // 수신자별 멱등성 레코드 저장
        List<NotificationIdempotency> idempotencyRecords = acceptedReceiverIds.stream()
            .map(receiverId -> {
                String key = buildKey(request.type().name(), receiverId, request.eventId());
                idempotencyRepository.deleteByIdempotencyKey(key);
                return NotificationIdempotency.of(key, notification.getId(), now.plusHours(IDEMPOTENCY_TTL_HOURS));
            })
            .toList();
        idempotencyRepository.saveAll(idempotencyRecords);

        // 벌크 Outbox 삽입 (JdbcTemplate 단일 쿼리)
        outboxService.bulkCreate(notification.getId(), acceptedReceiverIds, notification.getType(), request.scheduledAt());

        log.info("[Bulk] 벌크 알림 접수 완료. notificationId={}, total={}, accepted={}, skipped={}",
            notification.getId(), total, acceptedReceiverIds.size(), skipped);

        return new BulkNotificationResponse(notification.getId(), total, acceptedReceiverIds.size(), skipped);
    }

    @Transactional(readOnly = true)
    public NotificationStatusSummaryResponse getStatus(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        List<NotificationOutbox> outboxes = outboxRepository.findAllByNotificationId(notificationId);
        if (outboxes.isEmpty()) {
            throw new CustomException(ErrorCode.OUTBOX_NOT_FOUND);
        }

        return NotificationStatusSummaryResponse.of(
            notificationId, notification.getType(), notification.getChannel(), outboxes
        );
    }

    @Transactional(readOnly = true)
    public List<ReceiverStatusResponse> getReceiverStatuses(Long notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        return outboxRepository.findAllByNotificationId(notificationId)
            .stream()
            .map(ReceiverStatusResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> getNotifications(Long receiverId, Boolean isRead) {
        return notificationQueryRepository.findByReceiver(receiverId, isRead);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long receiverId) {
        NotificationOutbox outbox = outboxRepository
            .findByNotificationIdAndReceiverId(notificationId, receiverId)
            .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));
        outbox.markAsRead();
    }

    private String buildKey(String type, Long receiverId, String eventId) {
        return type + ":" + receiverId + ":" + eventId;
    }
}
