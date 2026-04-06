package com.liveklass.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationStatus;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxService 단위 테스트")
class OutboxServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender emailSender;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NotificationOutboxQueryRepository queryRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        when(emailSender.getChannel()).thenReturn(NotificationChannel.EMAIL);
        outboxService = new OutboxService(List.of(emailSender), outboxRepository, notificationRepository, jdbcTemplate, queryRepository);
        // self-injection: 프록시 없는 단위 테스트 환경에서 self 필드를 직접 주입
        ReflectionTestUtils.setField(outboxService, "self", outboxService);
    }

    @Nested
    @DisplayName("processTask 메서드는")
    class Describe_processTask {

        @Nested
        @DisplayName("발송 시각이 미도래한 예약 알림의 경우")
        class Context_scheduled_future {

            @Test
            @DisplayName("아무 작업도 하지 않고 반환(Skip)한다.")
            void it_skips_processing() {
                // given
                Long outboxId = 1L;
                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .status(OutboxStatus.INIT)
                    .nextRetryAt(LocalDateTime.now().plusHours(1))
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));

                // when
                outboxService.processTask(outboxId);

                // then
                verify(notificationRepository, never()).findById(any());
            }
        }

        @Nested
        @DisplayName("락 획득 중 타임아웃(LockTimeoutException)이 발생하는 경우")
        class Context_lock_timeout {

            @Test
            @DisplayName("발송 작업을 중단하고 즉시 반환한다.")
            void it_aborts_processing() {
                // given
                Long outboxId = 99L;
                when(outboxRepository.findByIdForUpdate(outboxId)).thenThrow(new LockTimeoutException("Lock wait timeout"));

                // when
                outboxService.processTask(outboxId);

                // then
                verify(notificationRepository, never()).findById(any());
            }
        }

        @Nested
        @DisplayName("이미 처리 중(PROCESSING)이거나 완료된 상태인 경우")
        class Context_not_init_status {

            @Test
            @DisplayName("발송 작업을 수행하지 않고 즉시 반환한다.")
            void it_skips_processing() {
                // given
                Long outboxId = 100L;
                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .status(OutboxStatus.PROCESSING)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));

                // when
                outboxService.processTask(outboxId);

                // then
                verify(notificationRepository, never()).findById(any());
            }
        }

        @Nested
        @DisplayName("이미 읽음(isRead=true) 상태인 Outbox인 경우")
        class Context_already_read {

            @Test
            @DisplayName("읽음 여부와 무관하게 발송하고 COMPLETED 처리한다.")
            void it_sends_and_completes_regardless_of_read_status() {
                // given
                Long outboxId = 2L;
                Long notificationId = 10L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .notificationId(notificationId)
                    .status(OutboxStatus.INIT)
                    .isRead(true)
                    .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                outboxService.processTask(outboxId);

                // then
                verify(emailSender).send(eq(notification), any());
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            }
        }

        @Nested
        @DisplayName("정상적이고 발송해야 할 알림인 경우")
        class Context_valid_notification {

            @Test
            @DisplayName("sender를 통해 발송하고 상태를 COMPLETED로 변경한다.")
            void it_sends_and_completes() {
                // given
                Long outboxId = 3L;
                Long notificationId = 20L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .notificationId(notificationId)
                    .status(OutboxStatus.INIT)
                    .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                outboxService.processTask(outboxId);

                // then
                verify(emailSender).send(eq(notification), any());
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            }
        }

        @Nested
        @DisplayName("송신 중 장애(예외)가 발생하는 경우")
        class Context_sender_fails {

            @Test
            @DisplayName("outbox.fail을 호출하여 재시도 상태(INIT)로 되돌린다.")
            void it_marks_as_failed_and_retries() {
                // given
                Long outboxId = 4L;
                Long notificationId = 30L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .notificationId(notificationId)
                    .type(NotificationType.PAYMENT_CONFIRMED)
                    .status(OutboxStatus.INIT)
                    .retryCount(0)
                    .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
                doThrow(new RuntimeException("Timeout")).when(emailSender).send(any(), any());

                // when
                outboxService.processTask(outboxId);

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.INIT);
                assertThat(outbox.getRetryCount()).isEqualTo(1);
                assertThat(outbox.getLastError()).isEqualTo("Timeout");
            }
        }

        @Nested
        @DisplayName("지원하지 않는 채널(IN_APP)의 알림인 경우")
        class Context_unsupported_channel {

            @Test
            @DisplayName("발송을 시도하지 않고 recordFailure를 호출하여 재시도 상태로 처리한다.")
            void it_records_failure_for_unsupported_channel() {
                // given
                Long outboxId = 6L;
                Long notificationId = 50L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .notificationId(notificationId)
                    .type(NotificationType.PAYMENT_CONFIRMED)
                    .status(OutboxStatus.INIT)
                    .retryCount(0)
                    .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .channel(NotificationChannel.IN_APP) // senderMap에 없는 채널
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                outboxService.processTask(outboxId);

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.INIT);
                assertThat(outbox.getLastError()).contains("지원하지 않는 발송 채널");
            }
        }

        @Nested
        @DisplayName("발송 실패 시 expiredAt이 경과한 경우")
        class Context_expired {

            @Test
            @DisplayName("상태를 EXPIRED로 변경하고 재시도를 중단한다.")
            void it_marks_as_expired() {
                // given
                Long outboxId = 5L;
                Long notificationId = 40L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .notificationId(notificationId)
                    .type(NotificationType.PAYMENT_CONFIRMED)
                    .status(OutboxStatus.INIT)
                    .expiredAt(LocalDateTime.now().minusMinutes(1))
                    .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
                doThrow(new RuntimeException("Network Error")).when(emailSender).send(any(), any());

                // when
                outboxService.processTask(outboxId);

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.EXPIRED);
                assertThat(outbox.getLastError()).contains("[EXPIRED]");
            }
        }
    }

    @Nested
    @DisplayName("findPendingTaskIds 메서드는")
    class Describe_findPendingTaskIds {

        @Nested
        @DisplayName("대기 중인 Outbox가 존재하면")
        class Context_with_pending_outboxes {

            @Test
            @DisplayName("queryRepository에서 조회한 ID 목록을 반환한다.")
            void it_returns_pending_outbox_id_list() {
                // given
                NotificationOutbox outbox1 = NotificationOutbox.builder().build();
                NotificationOutbox outbox2 = NotificationOutbox.builder().build();
                ReflectionTestUtils.setField(outbox1, "id", 10L);
                ReflectionTestUtils.setField(outbox2, "id", 20L);

                when(queryRepository.findPendingTasks(5)).thenReturn(List.of(outbox1, outbox2));

                // when
                List<Long> result = outboxService.findPendingTaskIds(5);

                // then
                assertThat(result).containsExactly(10L, 20L);
            }
        }
    }

    @Nested
    @DisplayName("recordSuccess 메서드는")
    class Describe_recordSuccess {

        @Nested
        @DisplayName("outboxId와 notificationId가 유효하면")
        class Context_with_valid_ids {

            @Test
            @DisplayName("Outbox를 COMPLETED로, Notification을 SENT로 변경한다.")
            void it_marks_outbox_completed_and_notification_sent() {
                // given
                Long outboxId = 1L;
                Long notificationId = 10L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId).status(OutboxStatus.PROCESSING).build();
                Notification notification = Notification.builder()
                    .id(notificationId).status(NotificationStatus.SENT).build();

                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                outboxService.recordSuccess(outboxId, notificationId);

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
                assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            }
        }
    }

    @Nested
    @DisplayName("recordFailure 메서드는")
    class Describe_recordFailure {

        @Nested
        @DisplayName("재시도 횟수가 최대 미만이면")
        class Context_with_retryable_failure {

            @Test
            @DisplayName("상태를 INIT으로 유지하고 retryCount를 증가시킨다.")
            void it_increments_retry_count_and_stays_init() {
                // given
                Long outboxId = 1L;
                Long notificationId = 10L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId).status(OutboxStatus.PROCESSING).retryCount(0).build();

                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

                // when
                outboxService.recordFailure(outboxId, notificationId, "Timeout");

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.INIT);
                assertThat(outbox.getRetryCount()).isEqualTo(1);
                assertThat(outbox.getLastError()).isEqualTo("Timeout");
            }
        }

        @Nested
        @DisplayName("재시도 횟수가 최대(4)에 도달하면")
        class Context_with_max_retry_exceeded {

            @Test
            @DisplayName("상태를 FAILED로 변경하고 Notification도 FAILED로 처리한다.")
            void it_marks_outbox_failed_and_notification_failed() {
                // given
                Long outboxId = 2L;
                Long notificationId = 20L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId).status(OutboxStatus.PROCESSING).retryCount(4).build();
                Notification notification = Notification.builder()
                    .id(notificationId).status(NotificationStatus.SENT).build();

                when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                outboxService.recordFailure(outboxId, notificationId, "Network Error");

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
                assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
            }
        }

        @Nested
        @DisplayName("outboxId에 해당하는 Outbox가 없으면")
        class Context_with_outbox_not_found {

            @Test
            @DisplayName("OUTBOX_NOT_FOUND 예외를 발생시킨다.")
            void it_throws_outbox_not_found() {
                // given
                Long outboxId = 999L;
                when(outboxRepository.findById(outboxId)).thenReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> outboxService.recordFailure(outboxId, 1L, "error"))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.OUTBOX_NOT_FOUND.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("create 메서드는 (calculateExpiredAt 간접 검증)")
    class Describe_create {

        @Nested
        @DisplayName("타입이 LECTURE_REMINDER_D1이면")
        class Context_with_lecture_reminder_d1 {

            @Test
            @DisplayName("expiredAt을 익일 자정(00:00)으로 설정한다.")
            void it_sets_expired_at_to_next_day_midnight() {
                // given
                LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);

                ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);

                // when
                outboxService.create(100L, 1L, NotificationType.LECTURE_REMINDER_D1, scheduledAt);

                // then
                verify(outboxRepository).save(captor.capture());
                LocalDateTime expectedExpiredAt = scheduledAt.toLocalDate().plusDays(1).atStartOfDay();
                assertThat(captor.getValue().getExpiredAt()).isEqualTo(expectedExpiredAt);
            }
        }

        @Nested
        @DisplayName("타입이 LECTURE_REMINDER_D1이 아니면")
        class Context_with_non_lecture_type {

            @Test
            @DisplayName("expiredAt을 null로 설정한다.")
            void it_sets_expired_at_to_null() {
                // given
                ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);

                // when
                outboxService.create(100L, 1L, NotificationType.PAYMENT_CONFIRMED, null);

                // then
                verify(outboxRepository).save(captor.capture());
                assertThat(captor.getValue().getExpiredAt()).isNull();
            }
        }
    }

    @Nested
    @DisplayName("bulkCreate 메서드는")
    class Describe_bulkCreate {

        @Nested
        @DisplayName("수신자 ID 목록이 주어지면")
        class Context_with_receiver_ids {

            @Test
            @DisplayName("수신자 수만큼 JdbcTemplate.batchUpdate를 호출한다.")
            void it_calls_batch_update_with_all_receiver_ids() {
                // given
                Long notificationId = 100L;
                List<Long> receiverIds = List.of(1L, 2L, 3L, 4L, 5L);

                // when
                outboxService.bulkCreate(notificationId, receiverIds, NotificationType.PAYMENT_CONFIRMED, null);

                // then
                verify(jdbcTemplate).batchUpdate(anyString(), eq(receiverIds), eq(receiverIds.size()), any());
            }
        }

        @Nested
        @DisplayName("수신자가 1명인 경우에도")
        class Context_with_single_receiver {

            @Test
            @DisplayName("batchUpdate를 정상적으로 호출한다.")
            void it_calls_batch_update_for_single_receiver() {
                // given
                Long notificationId = 200L;
                List<Long> receiverIds = List.of(42L);

                // when
                outboxService.bulkCreate(notificationId, receiverIds, NotificationType.PAYMENT_CONFIRMED, null);

                // then
                verify(jdbcTemplate).batchUpdate(anyString(), eq(receiverIds), eq(1), any());
            }
        }
    }
}
