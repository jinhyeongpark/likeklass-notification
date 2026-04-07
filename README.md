## 프로젝트 개요

LiveKlass 플랫폼의 알림 발송 백엔드 서비스입니다.

수강 신청 완료, 결제 확정, 강의 시작 D-1, 취소 처리 등의 비즈니스 이벤트 발생 시 사용자에게 이메일(EMAIL) 또는 인앱(IN_APP) 알림을 비동기로 발송합니다.

**핵심 설계 목표**

- **트랜잭션 분리**: 알림 처리 실패가 결제·수강신청 등 비즈니스 트랜잭션에 영향을 주지 않습니다.
- **유실 없는 발송**: Transactional Outbox 패턴으로 알림 저장과 발송 큐 등록을 하나의 트랜잭션에 묶어, 서버 재시작 후에도 미처리 알림이 재처리됩니다.
- **멱등성 보장**: `{type}:{receiverId}:{eventId}` 키로 24시간 내 동일 이벤트 중복 발송을 차단합니다.
- **분산 환경 대응**: `SELECT ... FOR UPDATE SKIP LOCKED`를 이용해 다중 인스턴스에서도 동일 알림의 중복 처리를 방지합니다.
- **회복 탄력성**: 타입별 재시도 정책(즉시 재시도 → 백오프)과 PROCESSING 교착 상태 자동 복구를 구현합니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| ORM | Spring Data JPA + Hibernate |
| Query | QueryDSL 5.0 |
| Database | H2 (File Mode, MySQL 호환) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Gradle |
| Test | JUnit 5, Mockito |
| Etc | Lombok |

## 실행 방법

**사전 조건**: JDK 17 이상

```bash
# 1. 프로젝트 클론
git clone <repo-url>
cd notification-service

# 2. 빌드 및 테스트
./gradlew build

# 3. 서버 기동 (기본 포트: 8080)
./gradlew bootRun
```

서버 기동 후 접속 가능한 엔드포인트:

| 주소 | 설명 |
|------|------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI (전체 API 명세 및 테스트) |
| `http://localhost:8080/h2-console` | H2 콘솔 (DB 직접 조회) |

H2 콘솔 접속 정보:
- JDBC URL: `jdbc:h2:file:./db/notification`
- Username: `sa`
- Password: (없음)

## API 목록 및 예시

### Notification — 알림 발송 요청 및 조회

#### `POST /api/v1/notifications` — 알림 발송 요청

동일 `{type}:{receiverId}:{eventId}` 조합은 24시간 내 중복 발송 차단. `scheduledAt` 지정 시 예약 발송.

**요청 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `receiverId` | Long | Y | 수신자 ID |
| `type` | String | Y | 알림 타입 (`ENROLLMENT_COMPLETED`, `PAYMENT_CONFIRMED`, `LECTURE_REMINDER_D1`, `CANCELLATION_PROCESSED`) |
| `channel` | String | Y | 발송 채널 (`EMAIL`, `IN_APP`) |
| `title` | String | N | 알림 제목 (생략 시 템플릿 사용) |
| `content` | String | N | 알림 본문 (생략 시 템플릿 사용) |
| `eventId` | String | Y | 이벤트 고유 식별자 (멱등성 키 구성 요소) |
| `scheduledAt` | String | N | 예약 발송 시각 (ISO 8601, null이면 즉시 발송) |
| `referenceData` | Object | N | 템플릿 플레이스홀더 치환용 데이터 |

```json
POST /api/v1/notifications
{
  "receiverId": 42,
  "type": "PAYMENT_CONFIRMED",
  "channel": "EMAIL",
  "eventId": "txn-20240403-abc123",
  "referenceData": {
    "lectureName": "Spring Boot 심화",
    "amount": 39000
  }
}
```

```json
HTTP 202
{
  "code": "NOTIFICATION202",
  "message": "알림 요청이 성공적으로 접수되었습니다.",
  "data": 1
}
```

---

#### `POST /api/v1/notifications/bulk` — 벌크 알림 발송 요청

공통 본문 1회 저장 후 수신자별 Outbox 일괄 등록. 수신자 최대 10,000명.

```json
POST /api/v1/notifications/bulk
{
  "receiverIds": [1, 2, 3, 100],
  "type": "ENROLLMENT_COMPLETED",
  "channel": "IN_APP",
  "eventId": "campaign-2024-spring",
  "referenceData": {
    "lectureName": "Spring Boot 심화"
  }
}
```

```json
HTTP 202
{
  "code": "NOTIFICATION202",
  "message": "벌크 알림 요청이 성공적으로 접수되었습니다.",
  "data": {
    "notificationId": 2,
    "totalRequested": 4,
    "accepted": 4,
    "skippedDuplicate": 0
  }
}
```

---

#### `GET /api/v1/notifications?receiverId={id}&isRead={bool}` — 수신함 조회

발송 완료(`SENT`) 알림만 반환. `isRead` 생략 시 전체 반환.

```
GET /api/v1/notifications?receiverId=42&isRead=false
```

```json
HTTP 200
{
  "data": [
    {
      "notificationId": 1,
      "title": "Spring Boot 심화 결제가 확정되었습니다",
      "content": "39000원 결제가 완료되었습니다. 강의명: Spring Boot 심화",
      "type": "PAYMENT_CONFIRMED",
      "channel": "EMAIL",
      "isRead": false,
      "createdAt": "2024-04-03T09:00:00"
    }
  ]
}
```

---

#### `GET /api/v1/notifications/{notificationId}/status` — 발송 집계 상태 조회

```
GET /api/v1/notifications/1/status
```

```json
HTTP 200
{
  "data": {
    "notificationId": 1,
    "completed": 3,
    "failed": 0,
    "pending": 1,
    "expired": 0
  }
}
```

---

#### `GET /api/v1/notifications/{notificationId}/status/receivers` — 수신자별 상세 조회

```json
HTTP 200
{
  "data": [
    {
      "receiverId": 42,
      "outboxStatus": "COMPLETED",
      "isRead": false,
      "retryCount": 0,
      "lastError": null
    }
  ]
}
```

---

#### `PATCH /api/v1/notifications/{notificationId}/read?receiverId={id}` — 읽음 처리

멱등하게 처리됨 (중복 호출 안전).

```
PATCH /api/v1/notifications/1/read?receiverId=42
```

```json
HTTP 200
{ "data": null }
```

---

### Admin Notification — 관리자 모니터링 및 복구

#### `GET /api/v1/admin/notifications/failed?limit={n}` — 실패 알림 목록 조회

```
GET /api/v1/admin/notifications/failed?limit=10
```

```json
HTTP 200
{
  "data": [
    {
      "outboxId": 5,
      "notificationId": 3,
      "receiverId": 99,
      "type": "PAYMENT_CONFIRMED",
      "retryCount": 3,
      "lastError": "Connection refused",
      "createdAt": "2024-04-03T08:50:00"
    }
  ]
}
```

---

#### `POST /api/v1/admin/notifications/{notificationId}/retry` — 수동 재전송

`FAILED` 상태 알림의 `retryCount`를 0으로 초기화하고 재시도 큐에 진입.

```
POST /api/v1/admin/notifications/3/retry
```

```json
HTTP 200
{ "data": null }
```

---

### Notification Template — 알림 템플릿 관리

#### `POST /api/v1/notification-templates` — 템플릿 등록

동일 `type + channel` 조합은 1개만 등록 가능. 제목/본문에 `{key}` 플레이스홀더 사용 가능.

```json
POST /api/v1/notification-templates
{
  "type": "PAYMENT_CONFIRMED",
  "channel": "EMAIL",
  "titleTemplate": "{lectureName} 결제가 확정되었습니다",
  "contentTemplate": "{amount}원 결제가 완료되었습니다. 강의명: {lectureName}"
}
```

```json
HTTP 201
{
  "data": {
    "id": 1,
    "type": "PAYMENT_CONFIRMED",
    "channel": "EMAIL",
    "titleTemplate": "{lectureName} 결제가 확정되었습니다",
    "contentTemplate": "{amount}원 결제가 완료되었습니다. 강의명: {lectureName}"
  }
}
```

---

#### `GET /api/v1/notification-templates` — 전체 조회

#### `GET /api/v1/notification-templates/{templateId}` — 단건 조회

#### `PUT /api/v1/notification-templates/{templateId}` — 수정

타입·채널은 변경 불가, 제목/본문 템플릿만 수정 가능.

#### `DELETE /api/v1/notification-templates/{templateId}` — 삭제

---

### 공통 에러 응답

| HTTP | 상황 |
|------|------|
| 400 | 요청 값 검증 실패 (`@Valid`) |
| 404 | 알림/템플릿/Outbox 없음 |
| 409 | 멱등성 키 중복 또는 유니크 제약 위반 |
| 422 | 템플릿 없고 본문도 없음 / 플레이스홀더 키 누락 |

## 데이터 모델 설명

### ERD 관계도

```
notification_templates
  id PK
  type          ENUM(ENROLLMENT_COMPLETED, PAYMENT_CONFIRMED,
                     LECTURE_REMINDER_D1, CANCELLATION_PROCESSED)
  channel       ENUM(EMAIL, IN_APP)
  titleTemplate VARCHAR(200)
  contentTemplate TEXT
  UK(type, channel)

notifications
  id PK
  title         VARCHAR(100)
  content       TEXT
  type          ENUM(...)
  channel       ENUM(EMAIL, IN_APP)
  status        ENUM(SCHEDULED, SENT, FAILED)
  idempotencyKey VARCHAR(100)
  referenceData TEXT (JSON)
  scheduledAt   DATETIME NULL
  IDX(idempotencyKey)

notification_idempotency
  id PK
  idempotencyKey VARCHAR(100) UK   ← {type}:{receiverId}:{eventId}
  notificationId BIGINT
  expiresAt      DATETIME          ← createdAt + 24h

notification_outbox
  id PK
  notificationId BIGINT            ── FK → notifications.id (논리적)
  receiverId     BIGINT
  type           ENUM(...)
  status         ENUM(INIT, PROCESSING, COMPLETED, FAILED, EXPIRED)
  retryCount     INT
  lastError      TEXT
  nextRetryAt    DATETIME
  expiredAt      DATETIME
  lockedAt       DATETIME
  isRead         BOOLEAN
  readAt         DATETIME
  createdAt      DATETIME
  UK(notificationId, receiverId)
  IDX(status, expiredAt, nextRetryAt)
```

### 테이블 설명

**`notifications`** — 알림 원본 레코드. 단건·벌크 구분 없이 본문 1개 저장. `status`는 인박스 노출 여부 제어용(`SENT`만 노출).

**`notification_outbox`** — 수신자당 1행. 실제 발송 상태 및 재시도 정보를 관리하는 메시지 큐 역할. 동일 `notificationId + receiverId` 조합은 유니크 제약으로 중복 삽입 방지.

**`notification_idempotency`** — 24시간 TTL의 멱등성 레코드. `{type}:{receiverId}:{eventId}` 키로 동일 이벤트 재발송 차단. 만료 레코드는 1시간마다 자동 정리.

**`notification_templates`** — `type + channel` 조합 1개의 제목/본문 템플릿 저장. `{key}` 플레이스홀더를 요청의 `referenceData`로 치환하여 본문 자동 생성.

### Outbox 상태 전이

```
INIT ──► PROCESSING ──► COMPLETED
              │
              ├──► INIT (재시도, retryCount < max)
              ├──► FAILED (retryCount == max)
              └──► EXPIRED (expiredAt 초과)
```

재시도 간격:
- 1차 실패: 3초 후 즉시 재시도 (공통)
- `LECTURE_REMINDER_D1`: 2차 이후 지수 백오프 (2분 → 4분 → 8분…)
- 그 외: 2·3차 10초 간격 → 4차(최종) 10분 대기 후 시도

## 요구사항 해석 및 가정

### 필수 구현

#### 1. 알림 발송 요청 API

**응답이 202 Accepted인 이유**

요구사항의 "응답: 요청 접수 완료 (즉시 발송 아님)"을 API 응답 시점에 발송 완료를 보장하지 않는 것으로 해석했습니다. 발송은 Outbox를 통해 비동기 처리되므로, 접수 성공(notificationId 반환)과 발송 성공은 별개의 시점입니다.

**알림 상태 조회를 두 단계로 분리한 이유**

`GET /notifications/{id}/status`(집계)와 `GET /notifications/{id}/status/receivers`(수신자별 상세)로 분리했습니다. 벌크 알림처럼 수신자가 수천 명인 경우 집계 뷰만 필요한 상황에서 수신자 전체 목록을 조회하는 비용을 피하기 위해서입니다.

**수신함 조회(`GET /notifications`)에서 SENT 상태만 반환하는 이유**

SCHEDULED 상태는 아직 발송이 이루어지지 않은 알림입니다. 사용자 수신함에 노출하면 발송 전 알림이 목록에 나타나 혼란을 줄 수 있어 SENT 상태만 반환하도록 했습니다.

---

#### 2. 알림 처리 상태 관리

**상태를 두 테이블로 분리한 이유**

`notifications.status`(SCHEDULED / SENT / FAILED)와 `notification_outbox.status`(INIT / PROCESSING / COMPLETED / FAILED / EXPIRED)를 분리했습니다.

- `notifications.status`는 사용자 수신함 노출 여부 제어 전용입니다.
- `notification_outbox.status`는 실제 발송 처리 흐름(재시도 횟수, 에러 원인, 다음 재시도 시각 등)을 추적합니다.

두 관심사를 같은 테이블에 담으면 변경 빈도가 높은 Outbox 필드의 UPDATE가 Notification 레코드 전체에 영향을 미치게 됩니다.

**재시도 최대 횟수를 4회로 설정한 이유**

요구사항에 횟수가 명시되지 않아 직접 결정했습니다. 1차 즉시 재시도 후 점진적 대기를 거쳐 총 4회 시도하면 일시적 장애는 충분히 커버되고, 그 이상의 재시도는 외부 서비스 자체 장애일 가능성이 높다고 판단했습니다.

**실패 사유 기록**

`outbox.lastError`에 최대 500자 저장하며, 초과 시 말줄임 처리합니다. 관리자가 원인을 파악하는 데 충분한 길이로 판단했습니다.

---

#### 3. 중복 발송 방지

요구사항의 "동시에 같은 요청이 여러 번 들어오는 경우도 고려"를 단일 수단으로는 해결할 수 없다고 해석했습니다. 요청 접수, Outbox 폴링, 개별 처리 세 단계에서 서로 다른 경쟁 조건이 발생하므로 각 계층에 맞는 수단을 조합했습니다.

| 단계 | 수단 |
|------|------|
| 요청 접수 | 멱등성 키 DB Unique Key — 동시 요청 시 `DataIntegrityViolationException` catch 후 재조회 fallback |
| Outbox 폴링 | `SKIP LOCKED` — 다중 인스턴스 간 동일 레코드 중복 픽업 방지 |
| 개별 처리 | `FOR UPDATE` + `INIT` 재검증 — 즉시 경로 vs 폴링 경로 Race Condition 방지 |

멱등성 TTL은 요구사항에 명시되지 않아 24시간으로 가정했습니다.

---

#### 4. 비동기 처리 구조

**API 요청 스레드와 분리**

`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`를 조합해, 트랜잭션 커밋 직후 별도 스레드에서 발송을 시작합니다. 이벤트 리스너 예외가 원본 트랜잭션을 롤백하지 않으므로 비즈니스 흐름이 보호됩니다.

**실제 메시지 브로커 없이, 전환 가능한 구조**

두 가지 방향으로 추상화했습니다.

- `NotificationSender` 인터페이스로 발송 채널을 추상화 — 구현체만 교체하면 SMTP, AWS SES, FCM 등 실제 서비스 연동이 가능합니다.
- DB 기반 Outbox 폴링 구조를 유지하되, `OutboxService`의 발송 경로 부분만 교체하면 RabbitMQ나 Kafka 등 실제 브로커로 전환할 수 있습니다. Service / Controller 레이어는 변경이 없습니다.

---

#### 5. 운영 시나리오 대응

| 시나리오 | 대응 방식 |
|----------|-----------|
| 처리 중 상태 지속 | `PROCESSING` 5분 초과 레코드를 `INIT`으로 자동 복구 (1분 주기 스캔) |
| 서버 재시작 후 유실 | `INIT` 레코드가 DB에 영속되므로 재시작 후 폴링이 자동 재처리 |
| 다중 인스턴스 중복 처리 | `SKIP LOCKED`으로 서버 간 동일 레코드 중복 픽업 방지 |

---

### 선택 구현

#### 발송 스케줄링

`scheduledAt`을 `outbox.nextRetryAt` 초기값으로 설정했습니다. 폴링 쿼리의 `nextRetryAt <= now` 조건을 통해 발송 시각이 되기 전에는 자동으로 건너뛰므로, 별도의 예약 처리 경로를 추가하지 않고 기존 Outbox 흐름에 자연스럽게 통합됩니다. 즉시 발송 경로에서도 `claimTask`가 `nextRetryAt` 미도래 여부를 재검증하므로 예약 알림이 조기 발송되지 않습니다.

#### 알림 템플릿 관리

`type + channel` 조합에 템플릿 1개만 등록하도록 유니크 제약을 두었습니다. 동일 조합에 여러 템플릿이 존재하면 어떤 것을 적용할지 결정할 기준이 없기 때문입니다. 요청에 `title`/`content`를 직접 넘기면 템플릿 치환을 건너뛰므로, 템플릿이 없는 타입도 단건 발송이 가능합니다.

#### 읽음 처리 (동시 요청)

`outbox.markAsRead()`에서 `isRead`가 이미 `true`이면 `readAt`을 갱신하지 않습니다. 여러 기기에서 동시에 읽음 요청이 와도 최초 `readAt`만 기록되며, 중복 호출은 안전하게 무시됩니다.

#### 수동 재시도 — 재시도 횟수 초기화 정책

`manualRetry()` 호출 시 `retryCount`를 0으로 초기화합니다. 관리자가 수동으로 트리거하는 재시도는 시스템 자동 재시도와 별개의 맥락이라고 판단했습니다. 횟수를 누적시키면 이미 4회를 소진한 알림은 수동 재시도 1회만에 다시 `FAILED`로 종료되어 사실상 재시도가 불가능해집니다.

---

### 과제 제약사항 대응

**실제 이메일 발송 불필요 (Mock / 로그 출력)**

`EmailNotificationSender`와 `InAppNotificationSender`를 `log.info` 기반 stub으로 구현했습니다. `NotificationSender` 인터페이스를 유지하므로 실제 연동 시 구현체만 교체하면 됩니다.

**메시지 브로커 설치 불필요, 전환 가능한 구조**

DB 기반 Outbox 폴링으로 브로커 역할을 대체했습니다. 브로커 도입 시 `OutboxService`의 발송 경로 부분만 교체하면 되며, `NotificationService`와 Controller 레이어는 변경이 없습니다.

## 설계 결정과 이유

### 1. Transactional Outbox 패턴 채택

알림 발송 실패가 비즈니스 트랜잭션(결제, 수강신청 등)에 영향을 주지 않으면서도, 발송이 유실 없이 보장되어야 한다는 두 가지 상충되는 요건을 동시에 충족하기 위해 Transactional Outbox 패턴을 채택했습니다.

`Notification` 저장과 `NotificationOutbox` 등록을 **하나의 트랜잭션**에 묶습니다. 비즈니스 흐름은 Outbox 등록까지만 책임지고, 실제 발송은 별도 경로에서 비동기로 처리합니다. 서버 재시작 후에도 `INIT` 상태 레코드가 DB에 남아 있으므로 미처리 알림은 자동 재처리됩니다.

**`Notification`과 `NotificationOutbox`를 별도 테이블로 분리한 이유**

| 관점 | Notification | NotificationOutbox |
|------|-------------|-------------------|
| 역할 | 사용자에게 보여줄 영구 보관 결과물 | 발송을 완료하기 위한 작업 명세서 |
| 변경 빈도 | 생성 후 거의 불변 (읽음 처리 외) | 재시도마다 상태·횟수·에러 빈번히 UPDATE |
| 보관 기간 | 장기 보관 | 처리 완료 후 정책에 따라 삭제 가능 |
| 확장성 | RDB 장기 보관 | 필요 시 Redis·전용 MQ 등으로 저장소 교체 가능 |

---

### 2. 이중 발송 경로 — 즉시 경로 + 폴링 경로

```
[즉시 경로]  AFTER_COMMIT 이벤트 → @Async → OutboxService.processTask()
[폴링 경로]  NotificationProcessor.process()  3초마다 INIT 레코드 폴링 → OutboxService.processTask()
```

두 경로 모두 `OutboxService.processTask()`로 수렴합니다.

- **즉시 경로**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 트랜잭션 커밋 직후 별도 스레드에서 발송을 시도합니다. 이벤트 리스너 예외가 원본 트랜잭션을 롤백하지 않습니다.
- **폴링 경로**: 즉시 경로가 실패하거나 예약 발송(`scheduledAt`)처럼 즉시 처리할 수 없는 경우를 3초 주기로 보완합니다. 단, 벌크 알림은 즉시 경로를 사용하지 않고 폴링 경로에만 의존합니다.

---

### 3. 3단계 중복 발송 방지

다중 서버 환경에서 중복 발송은 단일 수단으로 해결되지 않습니다. 요청 접수, Outbox 폴링, 개별 처리 세 단계마다 독립된 방어 수단을 조합했습니다.

| 단계 | 수단 | 방어 대상 |
|------|------|-----------|
| 요청 접수 | 멱등성 키 (DB Unique Key + 24h TTL) | 동일 이벤트 중복 발송 요청 / 동시 요청 UK 충돌 시 fallback 재조회 |
| Outbox 폴링 | `STATUS = INIT` 필터 + `SKIP LOCKED` | 다중 서버 간 동일 레코드 중복 픽업 |
| 개별 처리 | `FOR UPDATE` + `INIT` 재검증 (3초 타임아웃) | 즉시 경로 vs 폴링 경로 Race Condition |

`FOR UPDATE` 락 획득 성공 후에도 `status != INIT`이면 즉시 스킵하는 이중 검증으로, 어느 경로가 먼저 진입하든 발송은 한 번만 실행됩니다.

---

### 4. Claim-Send-Record 트랜잭션 분리

발송 처리를 세 개의 독립 트랜잭션으로 분리했습니다.

```
[TX] claimTask (PROCESSING 마킹, 락 해제)
     → send() (트랜잭션 없음, 네트워크 호출)
     → [TX] recordSuccess / recordFailure
```

발송(네트워크 호출) 중 DB 커넥션·락을 점유하지 않으므로 처리량이 외부 서비스 응답속도에 종속되지 않습니다.

단, 서버 크래시가 `send()` 성공 직후에 발생하면 `recordSuccess`가 실행되지 않아 중복 발송이 생길 수 있습니다(at-least-once). 이를 완전히 해소하려면 외부 발송 서비스의 멱등성 키(`X-Idempotency-Key` 등)를 함께 적용해야 하며, 실제 발송 채널 연동 시 `outboxId` 기반 멱등성 키를 함께 전달하는 것을 권장 방향으로 설정했습니다.

---

### 5. 재시도 정책

재시도 간격은 알림 타입에 따라 차등 적용합니다. 1차 실패는 네트워크 일시 장애일 가능성이 높으므로 전 타입 공통으로 3초 후 즉시 재시도합니다.

| 재시도 횟수 | LECTURE_REMINDER_D1 | 그 외 타입 |
|------------|---------------------|-----------|
| 1차 실패 (retryCount=1) | 3초 후 | 3초 후 |
| 2차 실패 (retryCount=2) | 2분 후 (지수 백오프) | 10초 후 |
| 3차 실패 (retryCount=3) | 4분 후 | 10초 후 |
| 4차 실패 (retryCount=4) | 8분 후 | 10분 후 |
| 최대 횟수 초과 | `FAILED` | `FAILED` |

`LECTURE_REMINDER_D1`은 강의 당일 0시까지 TTL이 설정되어, TTL 초과 시 재시도 없이 `EXPIRED` 처리됩니다.

---

### 6. 교착 상태 자동 복구

서버 크래시 등으로 `PROCESSING` 상태가 5분 이상 지속되는 레코드는 `NotificationProcessor.recoverStuckProcessing()`이 1분마다 `INIT`으로 복구합니다. 이 조치로 크래시 후 재시작 없이도 미처리 알림이 자동으로 재처리됩니다.
## 테스트 실행 방법

모든 테스트는 외부 인프라 없이 Mockito 기반 단위 테스트로 구성되어 있어 별도 환경 설정 없이 실행 가능합니다.

```bash
# 전체 테스트 실행
./gradlew test

# 특정 클래스만 실행
./gradlew test --tests "com.liveklass.notification.service.NotificationServiceTest"

# 특정 메서드(Describe 단위)만 실행
./gradlew test --tests "com.liveklass.notification.service.NotificationServiceTest.Describe_requestNotification*"
```

### 테스트 대상

| 테스트 클래스 | 대상 서비스 | 주요 검증 항목 |
|---|---|---|
| `NotificationServiceTest` | `NotificationService` | 멱등성 중복 차단, 템플릿 치환, 예약 발송 상태 저장, 동시성 처리 |
| `OutboxServiceTest` | `OutboxService` | 예약 알림 스킵, 락 타임아웃 처리, 발송 완료/실패 상태 전이 |
| `AdminNotificationServiceTest` | `AdminNotificationService` | 실패 목록 조회, 수동 재전송(retryCount 초기화) |
| `NotificationTemplateServiceTest` | `NotificationTemplateService` | 템플릿 CRUD, 중복 등록 방지, 존재하지 않는 템플릿 예외 |

### 테스트 구조 컨벤션

3단계 중첩 네이밍을 사용합니다.

```
Describe_{메서드명}
  └─ Context_with_{조건}
       └─ it_{기대 결과}
```

```java
@DisplayName("requestNotification 메서드는")
class Describe_requestNotification {

    @DisplayName("TTL 윈도우 내 중복된 멱등성 키가 존재하면")
    class Context_with_duplicated_idempotency_key {

        @Test
        @DisplayName("저장 프로세스를 수행하지 않고 기존 알림 ID를 반환한다.")
        void it_returns_existing_notification_id() { ... }
    }
}
```

## 미구현 / 제약사항

### 기술적 제약사항

**재시도 시각 오차 (스케줄러 주기 의존)**

`nextRetryAt`은 정밀한 예약 시각이 아닌 "이 시각 이후에 처리 가능"을 의미하는 하한선입니다. 실제 발송은 `NotificationProcessor`가 3초마다 폴링하는 시점에 맞물리므로, 지정된 재시도 간격보다 최대 3초 지연될 수 있습니다. 재시도 간격이 3초~10초인 경우 상대 오차가 크며, 이는 현재 폴링 방식의 구조적 한계입니다. 허용 오차가 더 작아야 한다면 딜레이 큐(예: Redis ZSET + 전용 Worker)나 메시지 브로커 지연 메시지 기능을 고려해야 합니다.

**at-least-once 발송 (크래시 시 중복 가능)**

`send()` 성공 직후 서버 크래시가 발생하면 `recordSuccess`가 실행되지 않아 동일 Outbox가 재처리되어 중복 발송이 생길 수 있습니다. 현재는 처리량 우선 설계(Claim-Send-Record 분리)를 채택했기 때문에 발생하는 trade-off입니다. 실제 발송 채널 연동 시 외부 서비스의 멱등성 키(`outboxId` 기반)를 함께 전달하는 방식으로 보완할 수 있습니다.

**벌크 알림의 즉시 발송 경로 미지원**

벌크 알림은 `AFTER_COMMIT` 이벤트 즉시 경로를 사용하지 않고 3초 주기 폴링에만 의존합니다. Notification 1건에 수신자가 여럿이므로 어느 수신자 기준으로 이벤트를 발행할지 특정할 수 없기 때문입니다.

---

### 미구현 항목 (비즈니스 정책 논의 후 적용 가능)

| 항목 | 설명 |
|------|------|
| **관리자 수동 재전송 단위** | 현재 `POST /api/v1/admin/notifications/{notificationId}/retry`는 알림(notificationId) 단위로만 재전송을 지원합니다. 특정 수신자(outboxId) 1건만 선택적으로 재시도하는 개별 단위 API는 미구현 상태입니다. 수신자 규모·SLA 정책에 따라 granularity를 결정한 뒤 추가할 수 있습니다. |
| **Outbox 만료 레코드 자동 제거** | `FAILED` / `EXPIRED` 상태로 전이된 Outbox 레코드는 현재 영구 보관됩니다. 데이터 비대화 방지를 위해 보존 기간(예: 30일)을 정의하고 배치 삭제하는 정책이 필요하나, 감사 로그·CS 대응 목적으로 얼마나 오래 보관할지는 비즈니스 정책 결정 사항입니다. |
| **읽음 처리된 알림 제거 정책** | `isRead = true`로 전환된 Outbox 레코드를 일정 기간 후 삭제하거나 아카이빙하는 정책이 미구현입니다. 사용자 수신함 보존 기간, 재조회 필요성 등 서비스 정책에 따라 TTL 및 삭제 시점을 결정한 뒤 적용할 수 있습니다. |
## AI 활용 범위

### 초기 설계 조언 (Gemini)

이전 프로젝트에서 FCM 알림을 구현한 경험을 바탕으로 과제를 선택했으나, 당시 설계는 단일 인스턴스만 고려했고 서버 다운에 대한 대비가 없었습니다. 메시지 브로커 사용에 제약이 있다는 점을 확인한 후, Gemini와 대화하며 Transactional Outbox 패턴을 알게 되었습니다. DB 폴링 주기, 부하 감소를 위한 인덱싱·FetchSize 등 초기 설계 방향도 이 단계에서 논의했습니다. 이후 AI의 도움으로 기본 환경을 빠르게 구축한 뒤 도메인 로직 구현을 시작했습니다.

### 자동 테스트 환경 구축 (Claude Code)

기능 구현 이후 API 테스트를 자동화했습니다. 기존에는 테스트마다 서버 기동, DB 사전 데이터 적재, Postman 실행이 반복되어 병목이 생겼습니다. 이 흐름 전체를 하나의 에이전트로 묶어, 서버 기동부터 HTTP 요청 전송·응답 해석까지 명령 하나로 실행할 수 있도록 자동화했습니다.

### 리팩터링 (Claude Code)

코드 수정 시 연관 메서드와 테스트 코드를 함께 변경해야 하는 경우가 많아, 이 부분에 AI를 활용해 빠르고 누락 없이 수정했습니다. 개선 가능한 부분과 잠재적 문제를 분석하게 하고 트레이드오프를 함께 논의한 뒤 적용했습니다. 예를 들어 조회·전송·상태 기록이 하나의 트랜잭션에 묶여 있던 Outbox 처리 흐름을 `claimTask → send() → recordResult` 세 개의 독립 트랜잭션으로 분리해 처리량을 개선했습니다. 코드 구현을 위임한 만큼 변경사항은 직접 확인하고 커밋 메시지도 직접 작성했습니다. PR 생성은 GitHub MCP 연동으로 자동화해 작업 시간을 줄였습니다.

### 문서화 (Claude Code)

리팩터링·성능 개선 단계부터는 에이전트를 활용했기 때문에, 작업 로그를 마크다운 파일로 남겨두었습니다. README의 "요구사항 해석 및 가정", "미구현", "설계 결정과 이유" 항목은 작업 중 그때그때 작성해 둔 메모를 참고해 에이전트가 초안을 작성하도록 했고, 검토 후 최종 반영했습니다.
