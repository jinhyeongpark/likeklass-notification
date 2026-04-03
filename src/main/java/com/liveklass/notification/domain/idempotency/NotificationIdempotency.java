package com.liveklass.notification.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_idempotency", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long notificationId;

    // 이 시각 이후엔 동일 키로 새 알림 생성 허용
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public static NotificationIdempotency of(String idempotencyKey, Long notificationId, LocalDateTime expiresAt) {
        NotificationIdempotency record = new NotificationIdempotency();
        record.idempotencyKey = idempotencyKey;
        record.notificationId = notificationId;
        record.expiresAt = expiresAt;
        return record;
    }
}
