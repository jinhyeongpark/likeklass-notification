package com.liveklass.notification.api.controller;

import com.liveklass.notification.api.dto.FailedNotificationResponse;
import com.liveklass.notification.common.util.ApiResponse;
import com.liveklass.notification.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Notification", description = "관리자용 알림 모니터링 및 복구 API")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminService;

    @Operation(summary = "실패 알림 목록 조회", description = "최종 실패(FAILED) 상태인 알림 목록을 최근 실패순으로 조회합니다.")
    @GetMapping("/failed")
    public ApiResponse<List<FailedNotificationResponse>> getFailedNotifications(
        @Parameter(description = "조회 개수", example = "50")
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.onSuccess(adminService.getFailedNotifications(limit));
    }

    @Operation(
        summary = "알림 수동 재전송",
        description = """
            시스템 자동 재시도를 모두 초과하여 최종 대기 중인 FAILED 알림을
            수동으로 1회차부터 다시 발송 시도합니다.
            호출 시 해당 알림의 Outbox의 retryCount는 0으로 초기화됩니다.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수동 재전송 큐 진입 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미 FAILED가 아닌 알림")
    })
    @PostMapping("/{notificationId}/retry")
    public ApiResponse<Void> retryFailedNotification(
        @Parameter(description = "재시도할 알림 ID", example = "10")
        @PathVariable Long notificationId
    ) {
        adminService.retryFailedNotification(notificationId);
        return ApiResponse.onSuccess();
    }
}
