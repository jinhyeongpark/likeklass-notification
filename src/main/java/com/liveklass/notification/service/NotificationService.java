package com.liveklass.notification.service;

import com.liveklass.notification.api.dto.NotificationRequestDto;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long requestNotification(NotificationRequestDto request) {
        // 1. Notification 엔티티 생성 및 저장
        Notification notification = Notification.builder()
            .receiverId(request.receiverId())
            .title(request.title())
            .content(request.content())
            .type(request.type())
            .referenceData(request.referenceData()) // Map 데이터
            .build();

        notificationRepository.save(notification);

        // 2. Outbox 생성 위임
        Long outboxId = outboxService.create(notification.getId());

        eventPublisher.publishEvent(new NotificationCreatedEvent(outboxId));

        return notification.getId();
    }

}
