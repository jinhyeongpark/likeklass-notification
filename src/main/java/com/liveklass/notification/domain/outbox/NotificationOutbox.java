package com.liveklass.notification.domain.outbox;

import com.liveklass.notification.domain.notification.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "notification_outbox", indexes = {
    @Index(name = "idx_outbox_status_retry", columnList = "status, nextRetryAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // 사용자의 중복 생성을 방지
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.INIT;

    @Builder.Default
    private Integer retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    private LocalDateTime nextRetryAt;

    private LocalDateTime lockedAt;

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void startProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.lockedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = OutboxStatus.COMPLETED;
        this.lockedAt = null;
    }

    public void fail(String errorMessage, int maxRetryCount) {
        this.lastError = (errorMessage != null && errorMessage.length() > 500)
            ? errorMessage.substring(0, 497) + "..."
            : errorMessage;
        this.lockedAt = null;

        // [TTL 기반 폐기 정책] NotificationType에 정의된 TTL을 초과한 경우 폐기 처리 (EXPIRED)
        if (this.type != null && this.type.getTtl() != null) {
            if (this.createdAt != null && this.createdAt.plus(this.type.getTtl()).isBefore(LocalDateTime.now())) {
                this.status = OutboxStatus.EXPIRED;
                this.lastError = "[EXPIRED] TTL 초과로 폐기됨: " + this.lastError;
                return;
            }
        }

        if (this.retryCount < maxRetryCount) {
            this.retryCount++;
            this.status = OutboxStatus.INIT;

            this.nextRetryAt = calculateNextRetryTime();
        } else {
            this.status = OutboxStatus.FAILED;
        }
    }

    private LocalDateTime calculateNextRetryTime() {
        LocalDateTime now = LocalDateTime.now();

        // 첫 번째 실패는 네트워크 일시 장애일 확률이 높으므로 10초 뒤 즉시 재시도
        if (this.retryCount == 1) {
            return now.plusSeconds(10);
        }

        // 결제 완료 알림은 30초 단위 선형 증가 (공격적 재시도)
        if (this.type == NotificationType.PAYMENT_CONFIRMED) {
            if (this.retryCount == 2) return now.plusSeconds(30); // 2차: 30초
            return now.plusSeconds(60); // 3차 이상: 60초
        }

        // 일반 알림은 기존 지수 백오프 (2분, 4분, 8분...)
        return now.plusMinutes((long) Math.pow(2, this.retryCount));
    }

    public void manualRetry() {
        this.status = OutboxStatus.INIT;
        this.retryCount = 0;
        this.lastError = null;
        this.nextRetryAt = LocalDateTime.now();
    }
}
