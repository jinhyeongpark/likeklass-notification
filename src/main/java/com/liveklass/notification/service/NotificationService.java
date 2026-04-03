package com.liveklass.notification.service;

import com.liveklass.notification.api.dto.NotificationRequestDto;
import com.liveklass.notification.api.dto.NotificationStatusResponse;
import com.liveklass.notification.api.dto.NotificationSummary;
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
        String idempotencyKey = buildKey(request);
        LocalDateTime now = LocalDateTime.now();

        Optional<NotificationIdempotency> active =
            idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(idempotencyKey, now);
        if (active.isPresent()) {
            log.info(">>> [Idempotency] TTL 윈도우 내 중복 요청. key={}", idempotencyKey);
            return active.get().getNotificationId();
        }

        try {
            idempotencyRepository.deleteByIdempotencyKey(idempotencyKey);

            // 템플릿 기반 제목/본문 해석
            String title = request.title();
            String content = request.content();

            Optional<NotificationTemplate> templateOpt =
                templateRepository.findByTypeAndChannel(request.type(), request.channel());

            if (templateOpt.isPresent()) {
                NotificationTemplate template = templateOpt.get();
                if (title == null || title.isBlank()) {
                    title = template.resolveTitle(request.referenceData());
                }
                if (content == null || content.isBlank()) {
                    content = template.resolveContent(request.referenceData());
                }
            }

            // 템플릿도 없고 content도 없으면 발송 불가
            if (content == null || content.isBlank()) {
                throw new CustomException(ErrorCode.NOTIFICATION_CONTENT_REQUIRED);
            }

            // scheduledAt이 미래 시각이면 SCHEDULED, 그 외(null 포함)는 SENT
            boolean isScheduled = request.scheduledAt() != null && request.scheduledAt().isAfter(now);
            NotificationStatus status = isScheduled ? NotificationStatus.SCHEDULED : NotificationStatus.SENT;

            Notification notification = Notification.builder()
                .receiverId(request.receiverId())
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

            Long outboxId = outboxService.create(notification.getId(), notification.getType(), request.scheduledAt());
            eventPublisher.publishEvent(new NotificationCreatedEvent(outboxId));

            return notification.getId();

        } catch (DataIntegrityViolationException e) {
            log.warn(">>> [Concurrency] 동시 중복 요청 감지. key={}", idempotencyKey);
            return idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(idempotencyKey, LocalDateTime.now())
                .map(NotificationIdempotency::getNotificationId)
                .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public NotificationStatusResponse getStatus(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        NotificationOutbox outbox = outboxRepository.findByNotificationId(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_NOT_FOUND));

        return NotificationStatusResponse.of(notification, outbox);
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> getNotifications(Long receiverId, Boolean isRead) {
        return notificationQueryRepository.findByReceiver(receiverId, isRead)
            .stream()
            .map(NotificationSummary::from)
            .toList();
    }

    /**
     * 읽음 처리.
     * markAsRead()는 멱등 — 이미 읽은 상태이면 상태를 변경하지 않으므로
     * 여러 기기에서 동시에 호출되어도 안전합니다.
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    private String buildKey(NotificationRequestDto request) {
        return request.type() + ":" + request.receiverId() + ":" + request.eventId();
    }
}
