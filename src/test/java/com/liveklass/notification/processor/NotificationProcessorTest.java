package com.liveklass.notification.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liveklass.notification.domain.idempotency.NotificationIdempotencyRepository;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.service.OutboxService;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationProcessor 단위 테스트")
class NotificationProcessorTest {

    @Mock
    private OutboxService outboxService;
    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private NotificationIdempotencyRepository idempotencyRepository;

    // 테스트에서는 동기 실행기를 사용해 CompletableFuture 완료를 보장
    private final Executor syncExecutor = Runnable::run;

    private NotificationProcessor notificationProcessor;

    @BeforeEach
    void setUp() {
        notificationProcessor = new NotificationProcessor(
            outboxService, outboxRepository, idempotencyRepository, syncExecutor
        );
    }

    @Nested
    @DisplayName("process 메서드는")
    class Describe_process {

        @Nested
        @DisplayName("처리 대기 중인 태스크가 여러 건 있으면")
        class Context_with_multiple_pending_tasks {

            @Test
            @DisplayName("각 태스크를 모두 처리한다.")
            void it_processes_all_tasks() {
                // given
                when(outboxService.findPendingTaskIds(200))
                    .thenReturn(List.of(1L, 2L, 3L))
                    .thenReturn(List.of());

                // when
                notificationProcessor.process();

                // then
                verify(outboxService).processTask(1L);
                verify(outboxService).processTask(2L);
                verify(outboxService).processTask(3L);
            }
        }

        @Nested
        @DisplayName("처리 대기 중인 태스크가 없으면")
        class Context_with_no_pending_tasks {

            @Test
            @DisplayName("processTask를 호출하지 않는다.")
            void it_does_not_call_process_task() {
                // given
                when(outboxService.findPendingTaskIds(200)).thenReturn(List.of());

                // when
                notificationProcessor.process();

                // then
                verify(outboxService, never()).processTask(any());
            }
        }

        @Nested
        @DisplayName("개별 태스크 처리 중 예외가 발생하면")
        class Context_with_task_processing_exception {

            @Test
            @DisplayName("다른 태스크의 처리에 영향을 주지 않는다.")
            void it_continues_processing_other_tasks() {
                // given
                when(outboxService.findPendingTaskIds(200))
                    .thenReturn(List.of(1L, 2L))
                    .thenReturn(List.of());
                doThrow(new RuntimeException("처리 오류")).when(outboxService).processTask(1L);

                // when
                notificationProcessor.process();

                // then
                verify(outboxService).processTask(2L);
            }
        }
    }
}
