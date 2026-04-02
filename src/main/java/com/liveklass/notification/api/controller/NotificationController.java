package com.liveklass.notification.api.controller;

import com.liveklass.notification.api.dto.NotificationRequestDto;
import com.liveklass.notification.common.util.ApiResponse;
import com.liveklass.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

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

    // TODO: [필수 구현 1] 특정 알림 요청의 현재 상태 조회 (Phase 3-4 예정)
    // @GetMapping("/{notificationId}/status")

    // TODO: [필수 구현 1] 수신자 기준 알림 목록 조회 (Phase 2 후반 예정)
    // @GetMapping

}
