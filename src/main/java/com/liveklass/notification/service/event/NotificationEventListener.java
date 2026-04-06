package com.liveklass.notification.service.event;

import com.liveklass.notification.service.NotificationCreatedEvent;
import com.liveklass.notification.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final OutboxService outboxService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        log.info("[Event] 커밋 감지! 즉시 발송 처리를 시작합니다. OutboxID: {}", event.outboxId());
        try {
            outboxService.processTask(event.outboxId());
        } catch (Exception e) {
            log.error("[Event] 즉시 발송 실패 (스케줄러에게 위임): {}", e.getMessage());
        }
    }
}
