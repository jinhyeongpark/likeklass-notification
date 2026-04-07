package com.liveklass.notification.domain.idempotency;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationIdempotencyRepository extends JpaRepository<NotificationIdempotency, Long> {

    // TTL이 아직 남아있는 활성 레코드 조회
    Optional<NotificationIdempotency> findByIdempotencyKeyAndExpiresAtAfter(String idempotencyKey, LocalDateTime now);

    // 벌크 요청용: 여러 키를 IN 절로 한 번에 조회
    List<NotificationIdempotency> findAllByIdempotencyKeyInAndExpiresAtAfter(Collection<String> keys, LocalDateTime now);

    // 만료된 레코드를 삭제해 동일 키 재사용 허용
    void deleteByIdempotencyKey(String idempotencyKey);

    // 주기적 만료 레코드 일괄 정리
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
