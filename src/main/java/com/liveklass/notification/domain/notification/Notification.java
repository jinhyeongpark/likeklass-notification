package com.liveklass.notification.domain.notification;

import com.liveklass.notification.common.converter.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long receiverId; // 알림을 받는 수강생 ID

    @Column(nullable = false, length = 100)
    private String title; // 알림 제목

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // 알림 본문

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type; // 알림 타입

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> referenceData;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false; // 읽음 여부

    private LocalDateTime readAt; // 읽은 시간

    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }
}
