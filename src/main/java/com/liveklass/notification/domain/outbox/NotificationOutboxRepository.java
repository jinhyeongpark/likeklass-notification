package com.liveklass.notification.domain.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    Optional<NotificationOutbox> findByNotificationId(Long notificationId);

    // 이벤트 리스너와 스케줄러의 Race Condition 방지용 비관적 락 조회
    // 3초 이내에 락을 획득하지 못하면 LockTimeoutException → 스케줄러에게 자연스럽게 양보
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT o FROM NotificationOutbox o WHERE o.id = :id")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
        UPDATE NotificationOutbox o
        SET o.status = 'INIT', o.lockedAt = null
        WHERE o.status = 'PROCESSING' AND o.lockedAt < :threshold
        """)
    int resetStuckProcessing(@Param("threshold") LocalDateTime threshold);
}
