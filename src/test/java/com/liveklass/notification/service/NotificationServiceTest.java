package com.liveklass.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liveklass.notification.api.dto.NotificationRequestDto;
import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.idempotency.NotificationIdempotency;
import com.liveklass.notification.domain.idempotency.NotificationIdempotencyRepository;
import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationQueryRepository;
import com.liveklass.notification.domain.notification.NotificationRepository;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.outbox.NotificationOutboxRepository;
import com.liveklass.notification.domain.template.NotificationTemplate;
import com.liveklass.notification.domain.template.NotificationTemplateRepository;
import java.time.LocalDateTime;
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
    }

    @Nested
    @DisplayName("markAsRead 메서드는")
    class Describe_markAsRead {

        @Nested
        @DisplayName("존재하는 알림 ID가 주어지면")
        class Context_with_valid_notification {

            @Test
            @DisplayName("알림의 isRead 상태를 true로 변경한다.")
            void it_marks_as_read() {
                // given
                Notification notification = Notification.builder()
                    .id(10L)
                    .isRead(false)
                    .build();

                when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

                // when
                notificationService.markAsRead(10L);

                // then
                assertThat(notification.getIsRead()).isTrue();
                assertThat(notification.getReadAt()).isNotNull();
            }
        }
    }
}
