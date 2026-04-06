package com.liveklass.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liveklass.notification.api.dto.BulkNotificationRequestDto;
import com.liveklass.notification.api.dto.BulkNotificationResponse;
import com.liveklass.notification.api.dto.NotificationRequestDto;
import com.liveklass.notification.api.dto.NotificationStatusSummaryResponse;
import com.liveklass.notification.api.dto.NotificationSummary;
import com.liveklass.notification.api.dto.ReceiverStatusResponse;
import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.idempotency.NotificationIdempotency;
import com.liveklass.notification.domain.idempotency.NotificationIdempotencyRepository;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationQueryRepository;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.notification.NotificationStatus;
import com.liveklass.notification.domain.outbox.NotificationOutbox;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.outbox.OutboxStatus;
import com.liveklass.notification.domain.template.NotificationTemplate;
import com.liveklass.notification.domain.template.NotificationTemplateRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationQueryRepository notificationQueryRepository;
    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private NotificationIdempotencyRepository idempotencyRepository;
    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Nested
    @DisplayName("requestNotification 메서드는")
    class Describe_requestNotification {

        @Nested
        @DisplayName("TTL 윈도우 내 중복된 멱등성 키가 존재하면")
        class Context_with_duplicated_idempotency_key {

            @Test
            @DisplayName("저장 프로세스를 수행하지 않고 기존 알림 ID를 반환한다.")
            void it_returns_existing_id() {
                // given
                NotificationRequestDto request = new NotificationRequestDto(
                    1L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "결제 완료", "결제가 완료되었습니다.", "event-123", null, null
                );
                String expectedKey = "PAYMENT_CONFIRMED:1:event-123";
                NotificationIdempotency activeIdempotency = NotificationIdempotency.of(expectedKey, 99L, LocalDateTime.now().plusDays(1));

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any()))
                    .thenReturn(Optional.of(activeIdempotency));

                // when
                Long result = notificationService.requestNotification(request);

                // then
                assertThat(result).isEqualTo(99L);
            }
        }

        @Nested
        @DisplayName("템플릿이 존재하지만 referenceData에 플레이스홀더 키가 누락된 경우")
        class Context_with_missing_placeholder_key {

            @Test
            @DisplayName("TEMPLATE_PLACEHOLDER_MISSING 예외를 발생시킨다.")
            void it_throws_placeholder_missing_exception() {
                // given
                NotificationRequestDto request = new NotificationRequestDto(
                    1L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    null, null, "event-wrong-key", null, Map.of("name", "박진형", "amount", 39000)
                );

                NotificationTemplate template = NotificationTemplate.builder()
                    .titleTemplate("{userName}님, 결제가 완료되었습니다!")
                    .contentTemplate("{amount}원 결제 완료")
                    .build();

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any())).thenReturn(Optional.empty());
                when(templateRepository.findByTypeAndChannel(any(), any())).thenReturn(Optional.of(template));

                // when & then
                assertThatThrownBy(() -> notificationService.requestNotification(request))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.TEMPLATE_PLACEHOLDER_MISSING.getMessage());
            }
        }

        @Nested
        @DisplayName("본문(content)이 없는 템플릿 사용 요청일 때 템플릿이 존재하면")
        class Context_with_template {

            @Test
            @DisplayName("템플릿 문구를 치환하여 알림을 성공적으로 저장한다.")
            void it_resolves_template_and_saves() {
                // given
                NotificationRequestDto request = new NotificationRequestDto(
                    1L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.IN_APP,
                    null, null, "event-template", null, Map.of("amount", 39000)
                );

                NotificationTemplate template = NotificationTemplate.builder()
                    .titleTemplate("결제 완료")
                    .contentTemplate("{amount}원 결제 성공")
                    .build();

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any())).thenReturn(Optional.empty());
                when(templateRepository.findByTypeAndChannel(any(), any())).thenReturn(Optional.of(template));
                when(outboxService.create(any(), any(), any(), any())).thenReturn(5L);

                // when
                notificationService.requestNotification(request);

                // then
                verify(notificationRepository).save(any(Notification.class));
                verify(eventPublisher).publishEvent(any(NotificationCreatedEvent.class));
            }
        }

        @Nested
        @DisplayName("본문도 없고 템플릿도 없으면")
        class Context_with_no_content_and_no_template {

            @Test
            @DisplayName("예외를 발생시킨다.")
            void it_throws_exception() {
                // given
                NotificationRequestDto request = new NotificationRequestDto(
                    1L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    null, null, "event-no-content", null, null
                );

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any())).thenReturn(Optional.empty());
                when(templateRepository.findByTypeAndChannel(any(), any())).thenReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> notificationService.requestNotification(request))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.NOTIFICATION_CONTENT_REQUIRED.getMessage());
            }
        }

        @Nested
        @DisplayName("동시 요청으로 DataIntegrityViolationException이 발생하면")
        class Context_with_concurrent_duplicate_request {

            @Test
            @DisplayName("멱등성 레코드를 재조회하여 기존 알림 ID를 반환한다.")
            void it_recovers_and_returns_existing_id() {
                // given
                NotificationRequestDto request = new NotificationRequestDto(
                    1L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "결제 완료", "결제가 완료되었습니다.", "event-concurrent", null, null
                );
                String key = "PAYMENT_CONFIRMED:1:event-concurrent";
                NotificationIdempotency recovered = NotificationIdempotency.of(key, 77L, LocalDateTime.now().plusHours(1));

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(recovered));
                when(templateRepository.findByTypeAndChannel(any(), any())).thenReturn(Optional.empty());
                doAnswer(inv -> { throw new org.springframework.dao.DataIntegrityViolationException("duplicate"); })
                    .when(idempotencyRepository).save(any());

                // when
                Long result = notificationService.requestNotification(request);

                // then
                assertThat(result).isEqualTo(77L);
            }
        }

        @Nested
        @DisplayName("scheduledAt이 현재 시각 이후이면")
        class Context_with_future_scheduled_at {

            @Test
            @DisplayName("알림 상태를 SCHEDULED로 저장한다.")
            void it_saves_notification_with_scheduled_status() {
                // given
                LocalDateTime futureTime = LocalDateTime.now().plusHours(2);
                NotificationRequestDto request = new NotificationRequestDto(
                    1L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "결제 완료", "결제가 완료되었습니다.", "event-scheduled", futureTime, null
                );

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any())).thenReturn(Optional.empty());
                when(templateRepository.findByTypeAndChannel(any(), any())).thenReturn(Optional.empty());
                when(outboxService.create(any(), any(), any(), any())).thenReturn(10L);

                doAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    ReflectionTestUtils.setField(n, "id", 55L);
                    return null;
                }).when(notificationRepository).save(any(Notification.class));

                // when
                notificationService.requestNotification(request);

                // then
                verify(notificationRepository).save(argThat(n -> n.getStatus() == com.liveklass.notification.domain.notification.NotificationStatus.SCHEDULED));
            }
        }
    }

    @Nested
    @DisplayName("requestNotificationsBulk 메서드는")
    class Describe_requestNotificationsBulk {

        @Nested
        @DisplayName("모든 수신자가 멱등성 필터를 통과하면")
        class Context_with_all_receivers_accepted {

            @Test
            @DisplayName("수신자 전원을 Outbox에 등록하고 notificationId를 반환한다.")
            void it_saves_all_receivers_and_returns_notification_id() {
                // given
                BulkNotificationRequestDto request = new BulkNotificationRequestDto(
                    List.of(1L, 2L, 3L),
                    NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "결제 완료", "결제가 완료되었습니다.",
                    "bulk-event-001", null, null
                );

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any()))
                    .thenReturn(Optional.empty());
                doAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    ReflectionTestUtils.setField(n, "id", 42L);
                    return null;
                }).when(notificationRepository).save(any(Notification.class));

                // when
                BulkNotificationResponse response = notificationService.requestNotificationsBulk(request);

                // then
                assertThat(response.notificationId()).isEqualTo(42L);
                assertThat(response.totalRequested()).isEqualTo(3);
                assertThat(response.accepted()).isEqualTo(3);
                assertThat(response.skipped()).isEqualTo(0);
                verify(notificationRepository).save(any(Notification.class));
            }
        }

        @Nested
        @DisplayName("일부 수신자가 멱등성 키 중복으로 필터링되면")
        class Context_with_partial_idempotency_filter {

            @Test
            @DisplayName("필터링된 수신자를 제외한 나머지만 Outbox에 등록한다.")
            void it_skips_duplicated_receivers_and_saves_rest() {
                // given
                BulkNotificationRequestDto request = new BulkNotificationRequestDto(
                    List.of(1L, 2L, 3L),
                    NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "결제 완료", "결제가 완료되었습니다.",
                    "bulk-event-002", null, null
                );

                NotificationIdempotency duplicate = NotificationIdempotency.of(
                    "PAYMENT_CONFIRMED:1:bulk-event-002", 10L, LocalDateTime.now().plusHours(1)
                );

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any()))
                    .thenAnswer(inv -> {
                        String key = inv.getArgument(0);
                        return key.contains(":1:") ? Optional.of(duplicate) : Optional.empty();
                    });

                // when
                BulkNotificationResponse response = notificationService.requestNotificationsBulk(request);

                // then
                assertThat(response.totalRequested()).isEqualTo(3);
                assertThat(response.accepted()).isEqualTo(2);
                assertThat(response.skipped()).isEqualTo(1);
            }
        }

        @Nested
        @DisplayName("모든 수신자가 멱등성 필터에 의해 제외되면")
        class Context_with_all_receivers_filtered {

            @Test
            @DisplayName("저장을 건너뛰고 notificationId가 null인 응답을 반환한다.")
            void it_skips_all_and_returns_null_notification_id() {
                // given
                BulkNotificationRequestDto request = new BulkNotificationRequestDto(
                    List.of(1L, 2L),
                    NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "결제 완료", "결제가 완료되었습니다.",
                    "bulk-event-003", null, null
                );

                when(idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any()))
                    .thenReturn(Optional.of(
                        NotificationIdempotency.of("any-key", 99L, LocalDateTime.now().plusHours(1))
                    ));

                // when
                BulkNotificationResponse response = notificationService.requestNotificationsBulk(request);

                // then
                assertThat(response.notificationId()).isNull();
                assertThat(response.totalRequested()).isEqualTo(2);
                assertThat(response.accepted()).isEqualTo(0);
                assertThat(response.skipped()).isEqualTo(2);
                verify(notificationRepository, never()).save(any());
            }
        }
    }

    @Nested
    @DisplayName("getStatus 메서드는")
    class Describe_getStatus {

        @Nested
        @DisplayName("존재하지 않는 notificationId가 주어지면")
        class Context_with_notification_not_found {

            @Test
            @DisplayName("NOTIFICATION_NOT_FOUND 예외를 발생시킨다.")
            void it_throws_notification_not_found() {
                // given
                Long notificationId = 999L;
                when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> notificationService.getStatus(notificationId))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.NOTIFICATION_NOT_FOUND.getMessage());
            }
        }

        @Nested
        @DisplayName("Notification은 존재하지만 Outbox가 없으면")
        class Context_with_outbox_not_found {

            @Test
            @DisplayName("OUTBOX_NOT_FOUND 예외를 발생시킨다.")
            void it_throws_outbox_not_found() {
                // given
                Long notificationId = 1L;
                Notification notification = Notification.builder()
                    .id(notificationId)
                    .type(NotificationType.PAYMENT_CONFIRMED)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
                when(outboxRepository.findAllByNotificationId(notificationId)).thenReturn(List.of());

                // when & then
                assertThatThrownBy(() -> notificationService.getStatus(notificationId))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.OUTBOX_NOT_FOUND.getMessage());
            }
        }

        @Nested
        @DisplayName("Notification과 Outbox가 모두 존재하면")
        class Context_with_valid_notification_and_outboxes {

            @Test
            @DisplayName("집계 상태 응답을 반환한다.")
            void it_returns_status_summary() {
                // given
                Long notificationId = 2L;
                Notification notification = Notification.builder()
                    .id(notificationId)
                    .type(NotificationType.PAYMENT_CONFIRMED)
                    .channel(NotificationChannel.EMAIL)
                    .build();

                NotificationOutbox completed = NotificationOutbox.builder()
                    .notificationId(notificationId).status(OutboxStatus.COMPLETED).build();
                NotificationOutbox failed = NotificationOutbox.builder()
                    .notificationId(notificationId).status(OutboxStatus.FAILED).build();

                when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
                when(outboxRepository.findAllByNotificationId(notificationId))
                    .thenReturn(List.of(completed, failed));

                // when
                NotificationStatusSummaryResponse response = notificationService.getStatus(notificationId);

                // then
                assertThat(response.notificationId()).isEqualTo(notificationId);
                assertThat(response.totalCount()).isEqualTo(2);
                assertThat(response.completedCount()).isEqualTo(1);
                assertThat(response.failedCount()).isEqualTo(1);
            }
        }
    }

    @Nested
    @DisplayName("getReceiverStatuses 메서드는")
    class Describe_getReceiverStatuses {

        @Nested
        @DisplayName("존재하지 않는 notificationId가 주어지면")
        class Context_with_notification_not_found {

            @Test
            @DisplayName("NOTIFICATION_NOT_FOUND 예외를 발생시킨다.")
            void it_throws_notification_not_found() {
                // given
                Long notificationId = 999L;
                when(notificationRepository.existsById(notificationId)).thenReturn(false);

                // when & then
                assertThatThrownBy(() -> notificationService.getReceiverStatuses(notificationId))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.NOTIFICATION_NOT_FOUND.getMessage());
            }
        }

        @Nested
        @DisplayName("유효한 notificationId가 주어지면")
        class Context_with_valid_notification {

            @Test
            @DisplayName("수신자별 상태 목록을 반환한다.")
            void it_returns_receiver_status_list() {
                // given
                Long notificationId = 3L;
                NotificationOutbox outbox1 = NotificationOutbox.builder()
                    .receiverId(10L).status(OutboxStatus.COMPLETED).isRead(true).retryCount(0).build();
                NotificationOutbox outbox2 = NotificationOutbox.builder()
                    .receiverId(20L).status(OutboxStatus.FAILED).isRead(false).retryCount(3).build();

                when(notificationRepository.existsById(notificationId)).thenReturn(true);
                when(outboxRepository.findAllByNotificationId(notificationId))
                    .thenReturn(List.of(outbox1, outbox2));

                // when
                List<ReceiverStatusResponse> result = notificationService.getReceiverStatuses(notificationId);

                // then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).receiverId()).isEqualTo(10L);
                assertThat(result.get(0).outboxStatus()).isEqualTo(OutboxStatus.COMPLETED);
                assertThat(result.get(1).receiverId()).isEqualTo(20L);
                assertThat(result.get(1).outboxStatus()).isEqualTo(OutboxStatus.FAILED);
            }
        }
    }

    @Nested
    @DisplayName("getNotifications 메서드는")
    class Describe_getNotifications {

        @Nested
        @DisplayName("수신자 ID와 읽음 필터가 주어지면")
        class Context_with_receiver_id_and_read_filter {

            @Test
            @DisplayName("queryRepository에 위임하여 알림 목록을 반환한다.")
            void it_delegates_to_query_repository() {
                // given
                Long receiverId = 42L;
                NotificationSummary summary = new NotificationSummary(
                    1L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "결제 완료", "내용", false, null, null
                );

                when(notificationQueryRepository.findByReceiver(receiverId, false))
                    .thenReturn(List.of(summary));

                // when
                List<NotificationSummary> result = notificationService.getNotifications(receiverId, false);

                // then
                assertThat(result).hasSize(1);
                assertThat(result.get(0).notificationId()).isEqualTo(1L);
            }
        }
    }

    @Nested
    @DisplayName("markAsRead 메서드는")
    class Describe_markAsRead {

        @Nested
        @DisplayName("존재하는 수신자의 Outbox가 주어지면")
        class Context_with_valid_outbox {

            @Test
            @DisplayName("해당 수신자의 isRead 상태를 true로 변경한다.")
            void it_marks_as_read() {
                // given
                Long notificationId = 10L;
                Long receiverId = 42L;
                NotificationOutbox outbox = NotificationOutbox.builder()
                    .id(1L)
                    .notificationId(notificationId)
                    .receiverId(receiverId)
                    .isRead(false)
                    .build();

                when(outboxRepository.findByNotificationIdAndReceiverId(notificationId, receiverId))
                    .thenReturn(Optional.of(outbox));

                // when
                notificationService.markAsRead(notificationId, receiverId);

                // then
                assertThat(outbox.getIsRead()).isTrue();
                assertThat(outbox.getReadAt()).isNotNull();
            }
        }
    }
}
