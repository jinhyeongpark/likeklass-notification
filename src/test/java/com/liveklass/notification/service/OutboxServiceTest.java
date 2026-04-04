package com.liveklass.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationSender;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxService 단위 테스트")
class OutboxServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender emailSender;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        when(emailSender.getChannel()).thenReturn(NotificationChannel.EMAIL);
        outboxService = new OutboxService(List.of(emailSender), outboxRepository, notificationRepository);
    }

    @Nested
    @DisplayName("process 메서드는")
    class Describe_process {

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
                outboxService.process(outboxId);

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
                outboxService.process(outboxId);

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
                outboxService.process(outboxId);

                // then
                verify(notificationRepository, never()).findById(any());
            }
        }

        @Nested
        @DisplayName("이미 읽음(isRead=true) 상태인 알림인 경우")
        class Context_already_read {

            @Test
            @DisplayName("발송을 생략하고 Outbox를 즉시 COMPLETED 처리한다.")
            void it_completes_outbox_without_sending() {
                // given
                Long outboxId = 2L;
                Long notificationId = 10L;

                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .notificationId(notificationId)
                    .status(OutboxStatus.INIT)
                    .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .isRead(true)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                outboxService.process(outboxId);

                // then
                verify(emailSender, never()).send(any());
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
                    .isRead(false)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                outboxService.process(outboxId);

                // then
                verify(emailSender).send(notification);
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
                    .isRead(false)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
                doThrow(new RuntimeException("Timeout")).when(emailSender).send(any());

                // when
                outboxService.process(outboxId);

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.INIT);
                assertThat(outbox.getRetryCount()).isEqualTo(1);
                assertThat(outbox.getLastError()).isEqualTo("Timeout");
            }
        }

        @Nested
        @DisplayName("발송 실패 시 TTL이 만료된 경우")
        class Context_expired {

            @Test
            @DisplayName("상태를 EXPIRED로 변경하고 재시도를 중단한다.")
            void it_marks_as_expired() {
                // given
                Long outboxId = 5L;
                Long notificationId = 40L;

                // 11분 전 생성 (결제 확정 TTL인 10분 초과)
                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(outboxId)
                    .notificationId(notificationId)
                    .type(NotificationType.PAYMENT_CONFIRMED)
                    .status(OutboxStatus.INIT)
                    .createdAt(LocalDateTime.now().minusMinutes(11))
                    .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .isRead(false)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(outboxRepository.findByIdForUpdate(outboxId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
                doThrow(new RuntimeException("Network Error")).when(emailSender).send(any());

                // when
                outboxService.process(outboxId);

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.EXPIRED);
                assertThat(outbox.getLastError()).contains("[EXPIRED]");
                verify(notificationRepository).findById(notificationId);
            }
        }
    }
}
