package com.liveklass.notification.infrastructure.sender;

import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationSender implements NotificationSender {

    @Override
    public void send(Notification notification, Long receiverId) {
        // 실제 운영 환경에서는 SMTP / SES 등 이메일 발송 로직으로 교체
        log.info("[EMAIL] 이메일 알림 발송");
        log.info("   > 수신자 ID: {}", receiverId);
        log.info("   > 제목: {}", notification.getTitle());
        log.info("   > 내용: {}", notification.getContent());
        log.info("   > 데이터: {}", notification.getReferenceData());
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }
}
