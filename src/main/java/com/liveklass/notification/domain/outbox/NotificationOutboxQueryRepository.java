package com.liveklass.notification.domain.outbox;

import static com.liveklass.notification.domain.outbox.QNotificationOutbox.notificationOutbox;

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

}
