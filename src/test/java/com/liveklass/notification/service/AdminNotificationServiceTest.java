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
import java.util.List;
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
        @DisplayName("FAILED 상태인 Outbox가 하나도 없으면")
        class Context_no_failed_outbox {

            @Test
            @DisplayName("재시도가 불가하므로 예외를 발생시킨다.")
            void it_throws_exception() {
                // given
                Long notificationId = 1L;
                NotificationOutbox completed = NotificationOutbox.builder()
                    .status(OutboxStatus.COMPLETED)
                    .build();

                when(outboxRepository.findAllByNotificationId(notificationId))
                    .thenReturn(List.of(completed));

                // when & then
                assertThatThrownBy(() -> adminNotificationService.retryFailedNotification(notificationId))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.INVALID_INPUT_VALUE.getMessage());
            }
        }

        @Nested
        @DisplayName("여러 수신자 중 FAILED 상태인 Outbox가 있다면")
        class Context_with_multiple_failed_outboxes {

            @Test
            @DisplayName("FAILED 상태인 Outbox를 전부 초기화(INIT)하고 Notification을 복구(SENT)한다.")
            void it_resets_all_failed_outboxes_and_reverts_notification() {
                // given
                Long notificationId = 2L;

                NotificationOutbox failed1 = NotificationOutbox.builder()
                    .status(OutboxStatus.FAILED).retryCount(4).lastError("Network Error").build();
                NotificationOutbox failed2 = NotificationOutbox.builder()
                    .status(OutboxStatus.FAILED).retryCount(4).lastError("Timeout").build();
                NotificationOutbox completed = NotificationOutbox.builder()
                    .status(OutboxStatus.COMPLETED).build();

                Notification notification = Notification.builder()
                    .id(notificationId).status(NotificationStatus.FAILED).build();

                when(outboxRepository.findAllByNotificationId(notificationId))
                    .thenReturn(List.of(failed1, failed2, completed));
                when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(notification));

                // when
                adminNotificationService.retryFailedNotification(notificationId);

                // then — FAILED 수신자 둘 다 초기화
                assertThat(failed1.getStatus()).isEqualTo(OutboxStatus.INIT);
                assertThat(failed1.getRetryCount()).isEqualTo(0);
                assertThat(failed1.getLastError()).isNull();
                assertThat(failed2.getStatus()).isEqualTo(OutboxStatus.INIT);
                assertThat(failed2.getRetryCount()).isEqualTo(0);
                assertThat(failed2.getLastError()).isNull();

                // then — COMPLETED 수신자는 변경 없음
                assertThat(completed.getStatus()).isEqualTo(OutboxStatus.COMPLETED);

                // then — Notification 복구
                assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            }
        }
    }
}
