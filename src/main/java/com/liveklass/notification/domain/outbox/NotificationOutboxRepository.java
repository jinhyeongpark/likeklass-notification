package com.liveklass.notification.domain.outbox;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    Optional<NotificationOutbox> findByNotificationId(Long notificationId);

    @Modifying
    @Query("""
        UPDATE NotificationOutbox o
        SET o.status = 'INIT', o.lockedAt = null
        WHERE o.status = 'PROCESSING' AND o.lockedAt < :threshold
        """)
    int resetStuckProcessing(@Param("threshold") LocalDateTime threshold);
}
