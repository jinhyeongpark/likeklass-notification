package com.liveklass.notification.processor;

import com.liveklass.notification.domain.idempotency.NotificationIdempotencyRepository;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.service.OutboxService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class NotificationProcessor {

    private final OutboxService outboxService;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationIdempotencyRepository idempotencyRepository;
    private final Executor taskExecutor;

    public NotificationProcessor(
            OutboxService outboxService,
            NotificationOutboxRepository outboxRepository,
            NotificationIdempotencyRepository idempotencyRepository,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.outboxService = outboxService;
        this.outboxRepository = outboxRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelay = 3000)
    public void process() {
        int maxLoopCount = 5; // 3초에 최대 200 * 5 = 1000건
        int processedTotal = 0;

        while (maxLoopCount-- > 0) {
            List<Long> taskIds = outboxService.findPendingTaskIds(200);

            if (taskIds.isEmpty()) {
                break;
            }

            log.info(">>>> [Worker] {}건의 알림을 낚아챘습니다. (잔여 루프 횟수: {})", taskIds.size(), maxLoopCount);

            CompletableFuture<?>[] futures = taskIds.stream()
                .map(taskId -> CompletableFuture.runAsync(() -> {
                    try {
                        outboxService.processTask(taskId);
                    } catch (Exception e) {
                        log.error("Worker 작업 중 예기치 못한 에러 발생: {}", e.getMessage());
                    }
                }, taskExecutor))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            processedTotal += taskIds.size();
        }

        if (processedTotal > 0) {
            log.info("==== [Worker] 이번 3초 주기 동안 총 {}건의 밀린 알림을 해소했습니다! ====", processedTotal);
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
