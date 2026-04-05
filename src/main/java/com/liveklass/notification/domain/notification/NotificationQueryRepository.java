package com.liveklass.notification.domain.notification;

import static com.liveklass.notification.domain.notification.QNotification.notification;
import static com.liveklass.notification.domain.outbox.QNotificationOutbox.notificationOutbox;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Notification> findByReceiver(Long receiverId, Boolean isRead) {
        return queryFactory
            .selectFrom(notification)
            .join(notificationOutbox).on(notificationOutbox.notificationId.eq(notification.id))
            .where(
                notificationOutbox.receiverId.eq(receiverId),
                statusEq(NotificationStatus.SENT),
                isReadEq(isRead)
            )
            .orderBy(notification.id.desc())
            .fetch();
    }

    private BooleanExpression statusEq(NotificationStatus status) {
        return notification.status.eq(status);
    }

    private BooleanExpression isReadEq(Boolean isRead) {
        return isRead != null ? notification.isRead.eq(isRead) : null;
    }
}
