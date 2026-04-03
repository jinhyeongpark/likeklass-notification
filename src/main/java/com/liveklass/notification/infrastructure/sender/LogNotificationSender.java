package com.liveklass.notification.infrastructure.sender;

import com.liveklass.notification.domain.notification.Notification;
import com.liveklass.notification.domain.notification.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

    @Override
    public void send(Notification notification) {
        log.info("[REAL-TIME LOG] 알림 발송 성공");
        log.info("   > 수신자 ID: {}", notification.getReceiverId());
        log.info("   > 제목: {}", notification.getTitle());
        log.info("   > 내용: {}", notification.getContent());
        log.info("   > 데이터: {}", notification.getReferenceData());
    }
}
