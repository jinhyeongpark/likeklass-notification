package com.liveklass.notification.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Notification Service API")
                .description("""
                    알림 발송 요청, 상태 조회, 수신함 조회 API

                    - 알림 발송은 API 응답과 분리된 비동기 스레드에서 처리됩니다.
                    - Outbox 패턴 기반으로 최대 3회 재시도가 보장됩니다.
                    - idempotencyKey는 서버가 {type}:{receiverId}:{eventId} 형식으로 자동 생성합니다.
                    """)
                .version("v1")
            );
    }
}
