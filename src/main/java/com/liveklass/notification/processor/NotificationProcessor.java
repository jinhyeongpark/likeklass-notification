package com.liveklass.notification.processor;

import com.liveklass.notification.domain.idempotency.NotificationIdempotencyRepository;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxQueryRepository;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
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
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationIdempotencyRepository idempotencyRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional // 💡
    public void process() {
        List<NotificationOutbox> tasks = queryRepository.findPendingTasks(10);

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

    // 5분 이상 PROCESSING 상태인 레코드를 INIT으로 복구 (서버 크래시 대비, 1분마다)
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverStuckProcessing() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        int recovered = outboxRepository.resetStuckProcessing(threshold);
        if (recovered > 0) {
            log.warn(">>>> [Recovery] PROCESSING 상태 복구: {}건", recovered);
        }
    }

    // 만료된 멱등성 레코드를 정리해 테이블 비대화 방지 (1시간마다)
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanupExpiredIdempotencyKeys() {
        idempotencyRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.debug(">>>> [Cleanup] 만료된 멱등성 키 정리 완료");
    }
}
