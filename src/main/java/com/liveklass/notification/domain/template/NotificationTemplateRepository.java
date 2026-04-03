package com.liveklass.notification.domain.template;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTypeAndChannel(NotificationType type, NotificationChannel channel);

    List<NotificationTemplate> findAllByOrderByTypeAscChannelAsc();

    boolean existsByTypeAndChannel(NotificationType type, NotificationChannel channel);
}
