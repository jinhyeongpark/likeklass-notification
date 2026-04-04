package com.liveklass.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liveklass.notification.api.dto.NotificationTemplateRequest;
import com.liveklass.notification.api.dto.NotificationTemplateResponse;
import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import com.liveklass.notification.domain.template.NotificationTemplate;
import com.liveklass.notification.domain.template.NotificationTemplateRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationTemplateService 단위 테스트")
class NotificationTemplateServiceTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private NotificationTemplateService templateService;

    @Nested
    @DisplayName("create 메서드는")
    class Describe_create {

        @Nested
        @DisplayName("이미 동일한 타입+채널 조합이 존재하면")
        class Context_already_exists {

            @Test
            @DisplayName("TEMPLATE_ALREADY_EXISTS 예외를 발생시킨다.")
            void it_throws_exception() {
                // given
                NotificationTemplateRequest request = new NotificationTemplateRequest(
                    NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "제목", "본문"
                );

                when(templateRepository.existsByTypeAndChannel(request.type(), request.channel())).thenReturn(true);

                // when & then
                assertThatThrownBy(() -> templateService.create(request))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.TEMPLATE_ALREADY_EXISTS.getMessage());
            }
        }

        @Nested
        @DisplayName("새로운 타입+채널 조합이면")
        class Context_valid_new_template {

            @Test
            @DisplayName("성공적으로 저장하고 응답을 반환한다.")
            void it_saves_and_returns() {
                // given
                NotificationTemplateRequest request = new NotificationTemplateRequest(
                    NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL,
                    "제목 템플릿", "본문 템플릿"
                );

                when(templateRepository.existsByTypeAndChannel(request.type(), request.channel())).thenReturn(false);

                // when
                NotificationTemplateResponse response = templateService.create(request);

                // then
                verify(templateRepository).save(any(NotificationTemplate.class));
                assertThat(response.titleTemplate()).isEqualTo("제목 템플릿");
                assertThat(response.contentTemplate()).isEqualTo("본문 템플릿");
                assertThat(response.type()).isEqualTo(NotificationType.PAYMENT_CONFIRMED);
                assertThat(response.channel()).isEqualTo(NotificationChannel.EMAIL);
            }
        }
    }

    @Nested
    @DisplayName("update 메서드는")
    class Describe_update {

        @Nested
        @DisplayName("존재하지 않는 템플릿 ID가 주어지면")
        class Context_not_found {

            @Test
            @DisplayName("TEMPLATE_NOT_FOUND 예외를 발생시킨다.")
            void it_throws_exception() {
                // given
                Long templateId = 999L;
                NotificationTemplateRequest request = new NotificationTemplateRequest(
                    NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL, "수정된 제목", "수정된 본문"
                );

                when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> templateService.update(templateId, request))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(ErrorCode.TEMPLATE_NOT_FOUND.getMessage());
            }
        }

        @Nested
        @DisplayName("존재하는 템플릿 ID가 주어지면")
        class Context_valid_update {

            @Test
            @DisplayName("템플릿 내용을 업데이트한다.")
            void it_updates_template() {
                // given
                Long templateId = 1L;
                NotificationTemplate template = NotificationTemplate.builder()
                    .titleTemplate("기존 제목")
                    .contentTemplate("기존 본문")
                    .build();

                NotificationTemplateRequest request = new NotificationTemplateRequest(
                    NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL, "수정된 제목", "수정된 본문"
                );

                when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

                // when
                NotificationTemplateResponse response = templateService.update(templateId, request);

                // then
                assertThat(template.getTitleTemplate()).isEqualTo("수정된 제목");
                assertThat(template.getContentTemplate()).isEqualTo("수정된 본문");
                assertThat(response.titleTemplate()).isEqualTo("수정된 제목");
            }
        }
    }
}
