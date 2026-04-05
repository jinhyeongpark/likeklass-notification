package com.liveklass.notification.api.controller;

import com.liveklass.notification.api.dto.BulkNotificationRequestDto;
import com.liveklass.notification.api.dto.BulkNotificationResponse;
import com.liveklass.notification.api.dto.NotificationRequestDto;
import com.liveklass.notification.api.dto.NotificationStatusSummaryResponse;
import com.liveklass.notification.api.dto.NotificationSummary;
import com.liveklass.notification.api.dto.ReceiverStatusResponse;
import com.liveklass.notification.common.util.ApiResponse;
import com.liveklass.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 발송 요청 및 조회 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
        summary = "알림 발송 요청",
        description = """
            알림 발송을 요청합니다. 요청은 즉시 접수되며, 실제 발송은 비동기로 처리됩니다.
            동일한 eventId + type + receiverId 조합은 24시간 내 중복 발송이 차단됩니다.
            scheduledAt을 지정하면 해당 시각에 발송됩니다.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "알림 요청 접수 완료 — notificationId 반환"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동시 중복 요청으로 인한 DB 제약 위반")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Long> requestNotification(
        @RequestBody @Valid NotificationRequestDto requestDto
    ) {
        Long notificationId = notificationService.requestNotification(requestDto);
        return ApiResponse.onSuccess(
            HttpStatus.ACCEPTED,
            "NOTIFICATION202",
            "알림 요청이 성공적으로 접수되었습니다.",
            notificationId
        );
    }

    @Operation(
        summary = "벌크 알림 발송 요청",
        description = """
            공통 알림 본문을 1회 저장하고, 다수의 수신자에게 Outbox를 일괄 등록합니다.
            수신자별로 {type}:{receiverId}:{eventId} 조합의 멱등성 키를 검사하여 중복 수신자를 필터링합니다.
            발송은 스케줄러가 처리합니다.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "벌크 알림 요청 접수 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "알림 본문 또는 템플릿 없음")
    })
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<BulkNotificationResponse> requestBulkNotification(
        @RequestBody @Valid BulkNotificationRequestDto requestDto
    ) {
        BulkNotificationResponse response = notificationService.requestNotificationsBulk(requestDto);
        return ApiResponse.onSuccess(
            HttpStatus.ACCEPTED,
            "NOTIFICATION202",
            "벌크 알림 요청이 성공적으로 접수되었습니다.",
            response
        );
    }

    @Operation(
        summary = "알림 발송 집계 상태 조회",
        description = "특정 알림의 전체 수신자 발송 현황을 집계하여 반환합니다. 완료/실패/대기/폐기 건수를 포함합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림 또는 Outbox를 찾을 수 없음")
    })
    @GetMapping("/{notificationId}/status")
    public ApiResponse<NotificationStatusSummaryResponse> getStatus(
        @Parameter(description = "알림 ID", example = "10")
        @PathVariable Long notificationId
    ) {
        return ApiResponse.onSuccess(notificationService.getStatus(notificationId));
    }

    @Operation(
        summary = "수신자별 발송 상태 상세 조회",
        description = "특정 알림의 수신자별 Outbox 상태, 읽음 여부, 재시도 횟수, 실패 사유를 반환합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음")
    })
    @GetMapping("/{notificationId}/status/receivers")
    public ApiResponse<List<ReceiverStatusResponse>> getReceiverStatuses(
        @Parameter(description = "알림 ID", example = "10")
        @PathVariable Long notificationId
    ) {
        return ApiResponse.onSuccess(notificationService.getReceiverStatuses(notificationId));
    }

    @Operation(
        summary = "수신함 알림 목록 조회",
        description = """
            수신자 기준으로 발송 완료된 알림 목록을 조회합니다.
            SCHEDULED(예약 대기), FAILED(최종 실패) 상태 알림은 결과에 포함되지 않습니다.
            isRead 파라미터를 생략하면 읽음/안읽음 구분 없이 전체를 반환합니다.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ApiResponse<List<NotificationSummary>> getNotifications(
        @Parameter(description = "수신자 ID", example = "42", required = true)
        @RequestParam Long receiverId,
        @Parameter(description = "읽음 여부 필터. true=읽음만, false=안읽음만, 생략=전체", example = "false")
        @RequestParam(required = false) Boolean isRead
    ) {
        return ApiResponse.onSuccess(notificationService.getNotifications(receiverId, isRead));
    }

    @Operation(
        summary = "알림 읽음 처리",
        description = """
            특정 수신자의 알림을 읽음 상태로 전환합니다.
            이미 읽은 알림에 대해 재호출해도 멱등하게 처리되므로,
            여러 기기에서 동시에 읽음 요청이 들어와도 안전합니다.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Outbox를 찾을 수 없음")
    })
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
        @Parameter(description = "알림 ID", example = "10")
        @PathVariable Long notificationId,
        @Parameter(description = "수신자 ID", example = "42", required = true)
        @RequestParam Long receiverId
    ) {
        notificationService.markAsRead(notificationId, receiverId);
        return ApiResponse.onSuccess();
    }
}
