package com.liveklass.notification.domain.outbox;

import static com.liveklass.notification.domain.outbox.QNotificationOutbox.notificationOutbox;

import com.liveklass.notification.api.dto.FailedNotificationResponse;
import com.liveklass.notification.domain.notification.QNotification;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationOutboxQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<NotificationOutbox> findPendingTasks(int limit) {
        return queryFactory
            .selectFrom(notificationOutbox)
            .where(
                statusEq(OutboxStatus.INIT),      // 대기 상태인 것만
                isTimeToSend()                 // 지금 보내야 할 시간인 것만
            )
            .orderBy(notificationOutbox.nextRetryAt.asc())
            .limit(limit)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint("jakarta.persistence.lock.timeout", -2) // SKIP LOCKED
            .fetch();
    }

    private BooleanExpression statusEq(OutboxStatus status) {
        return status != null ? notificationOutbox.status.eq(status) : null;
    }

    private BooleanExpression isTimeToSend() {
        return notificationOutbox.nextRetryAt.loe(DateTimeExpression.currentTimestamp(LocalDateTime.class));
    }

    public List<FailedNotificationResponse> findFailedNotifications(int limit) {
        QNotification notification =
            QNotification.notification;

        return queryFactory
            .select(Projections.constructor(
                FailedNotificationResponse.class,
                notification.id,
                notificationOutbox.receiverId,
                notification.type,
                notification.channel,
                notification.title,
                notificationOutbox.lastError,
                notificationOutbox.nextRetryAt // failed 시점 기록용 필드가 따로 없으므로 대체 사용
            ))
            .from(notificationOutbox)
            .join(notification).on(notificationOutbox.notificationId.eq(notification.id))
            .where(statusEq(OutboxStatus.FAILED))
            .orderBy(notificationOutbox.nextRetryAt.desc())
            .limit(limit)
            .fetch();
    }
}
