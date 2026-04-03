package com.liveklass.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationStatus;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxQueryRepository;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminNotificationService 단위 테스트")
class AdminNotificationServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private NotificationOutboxQueryRepository outboxQueryRepository;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private AdminNotificationService adminNotificationService;

    @Nested
    @DisplayName("retryFailedNotification 메서드는")
    class Describe_retryFailedNotification {

        @Nested
        @DisplayName("Outbox 상태가 FAILED가 아니면")
        class Context_not_failed_outbox {

            @Test
            @DisplayName("재시도가 불가하므로 예외를 발생시킨다.")
            void it_throws_exception() {
                // given
                Long notificationId = 1L;
                NotificationOutbox outbox = NotificationOutbox.builder()
                    .status(OutboxStatus.COMPLETED)
                    .build();

                when(outboxRepository.findByNotificationId(notificationId)).thenReturn(Optional.of(outbox));

                // when & then
                assertThatThrownBy(() -> adminNotificationService.retryFailedNotification(notificationId))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.INVALID_INPUT_VALUE.getMessage());
            }
        }

        @Nested
        @DisplayName("Outbox 상태가 정상적인 FAILED라면")
        class Context_with_failed_outbox {

            @Test
            @DisplayName("Outbox 속성을 초기화(INIT)하고 Notification을 복구(SENT)한다.")
            void it_resets_outbox_and_reverts_notification() {
                // given
                Long notificationId = 2L;
                NotificationOutbox outbox = NotificationOutbox.builder()
                    .status(OutboxStatus.FAILED)
                    .retryCount(3)
                    .lastError("Network Error")
                    .build();

                Notification notification = Notification.builder()
                    .id(notificationId)
                    .status(NotificationStatus.FAILED)
                    .build();

                when(outboxRepository.findByNotificationId(notificationId)).thenReturn(Optional.of(outbox));
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

                // when
                adminNotificationService.retryFailedNotification(notificationId);

                // then
                assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.INIT);
                assertThat(outbox.getRetryCount()).isEqualTo(0);
                assertThat(outbox.getLastError()).isNull();
                assertThat(outbox.getNextRetryAt()).isNotNull();

                assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            }
        }
    }
}
