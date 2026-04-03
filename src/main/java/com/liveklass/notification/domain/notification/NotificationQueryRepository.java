package com.liveklass.notification.domain.notification;

import static com.liveklass.notification.domain.notification.QNotification.notification;

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
            .where(
                receiverIdEq(receiverId),
                statusEq(NotificationStatus.SENT),
                isReadEq(isRead)
            )
            .orderBy(notification.id.desc())
            .fetch();
    }

    private BooleanExpression receiverIdEq(Long receiverId) {
        return notification.receiverId.eq(receiverId);
    }

    private BooleanExpression statusEq(NotificationStatus status) {
        return notification.status.eq(status);
    }

    private BooleanExpression isReadEq(Boolean isRead) {
        return isRead != null ? notification.isRead.eq(isRead) : null;
    }
}
