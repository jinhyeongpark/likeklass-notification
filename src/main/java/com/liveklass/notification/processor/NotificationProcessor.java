package com.liveklass.notification.processor;

import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxQueryRepository;
import com.liveklass.notification.service.OutboxService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessor {

    private final OutboxService outboxService;
    private final NotificationOutboxQueryRepository queryRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional // 💡
    public void process() {
        List<NotificationOutbox> tasks = queryRepository.findPendingTasks(LocalDateTime.now(), 10);

        if (tasks.isEmpty()) return;

        log.info(">>>> [Worker] {}건의 알림을 낚아챘습니다.", tasks.size());

        for (NotificationOutbox task : tasks) {
            try {
                outboxService.process(task.getId());
            } catch (Exception e) {
                log.error("Worker 작업 중 예기치 못한 에러 발생: {}", e.getMessage());
            }
        }
    }
}
