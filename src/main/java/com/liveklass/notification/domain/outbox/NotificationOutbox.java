package com.liveklass.notification.domain.outbox;

import com.liveklass.notification.domain.notification.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "notification_outbox",
    indexes = {
        @Index(name = "idx_outbox_status_expired_retry", columnList = "status, expiredAt, nextRetryAt")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_outbox_notification_receiver", columnNames = {"notificationId", "receiverId"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long notificationId;

    @Column(nullable = false)
    private Long receiverId;

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

    private LocalDateTime expiredAt;

    private LocalDateTime lockedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    private LocalDateTime readAt;

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

        // [expiredAt 기반 폐기 정책] 명시적 만료 시각을 초과한 경우 폐기 처리 (EXPIRED)
        if (this.expiredAt != null && this.expiredAt.isBefore(LocalDateTime.now())) {
            this.status = OutboxStatus.EXPIRED;
            this.lastError = "[EXPIRED] 만료 시각 초과로 폐기됨: " + this.lastError;
            return;
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

        // 공통: 1차 실패는 네트워크 일시 장애일 확률이 높으므로 3초 뒤 즉시 재시도
        if (this.retryCount == 1) {
            return now.plusSeconds(3);
        }

        // D-1 알림: 2차 이후 지수 백오프 (2분, 4분, 8분...)
        if (this.type == NotificationType.LECTURE_REMINDER_D1) {
            return now.plusMinutes((long) Math.pow(2, this.retryCount - 1));
        }

        // 일반 알림: 2~3차 10초 간격, 3차 실패 시 10분 대기 후 최종 시도
        if (this.retryCount == 2) return now.plusSeconds(10);
        return now.plusMinutes(10);
    }

    public void markAsRead() {
        if (!Boolean.TRUE.equals(this.isRead)) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }

    public void manualRetry() {
        this.status = OutboxStatus.INIT;
        this.retryCount = 0;
        this.lastError = null;
        this.nextRetryAt = LocalDateTime.now();
    }
}
