package com.liveklass.notification.service;

import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxService {

    private final NotificationOutboxRepository outboxRepository;

    @Transactional
    public void create(Long notificationId) {
        NotificationOutbox outbox = NotificationOutbox.builder()
            .notificationId(notificationId)
            .status(OutboxStatus.INIT)
            .nextRetryAt(LocalDateTime.now())
            .build();
        outboxRepository.save(outbox);
    }
}
