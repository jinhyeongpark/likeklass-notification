package com.liveklass.notification.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(nullable = false)
    private Long notificationId; // 연관된 Notification의 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.INIT;

    @Builder.Default
    private Integer retryCount = 0; // 현재까지의 재시도 횟수

    @Column(columnDefinition = "TEXT")
    private String lastError; // 실패 사유 기록

    private LocalDateTime nextRetryAt; // 다음 재시도 가능 시간 (지수 백오프용)

    private LocalDateTime lockedAt; // 처리 시작 시간 (타임아웃 복구용)

    public void startProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.lockedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = OutboxStatus.COMPLETED;
        this.lockedAt = null; // 락 해제
    }

    public void fail(String errorMessage, int maxRetryCount) {
        this.lastError = errorMessage;
        this.lockedAt = null;

        if (this.retryCount < maxRetryCount) {
            this.retryCount++;
            this.status = OutboxStatus.INIT; // 다시 INIT으로 돌려 스케줄러가 잡게 함
            this.nextRetryAt = LocalDateTime.now().plusMinutes((long) Math.pow(2, retryCount));
        } else {
            this.status = OutboxStatus.FAILED; // 최대 재시도 횟수 초과 시 최종 실패
        }
    }
}
