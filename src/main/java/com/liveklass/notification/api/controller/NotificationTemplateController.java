package com.liveklass.notification.api.controller;

import com.liveklass.notification.api.dto.NotificationTemplateRequest;
import com.liveklass.notification.api.dto.NotificationTemplateResponse;
import com.liveklass.notification.common.util.ApiResponse;
import com.liveklass.notification.service.NotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification Template", description = "알림 템플릿 관리 API")
@RestController
@RequestMapping("/api/v1/notification-templates")
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    @Operation(
        summary = "템플릿 등록",
        description = """
            알림 타입 + 채널 조합으로 메시지 템플릿을 등록합니다.
            제목/본문에 {key} 형식의 플레이스홀더를 사용하면 발송 시 referenceData로 자동 치환됩니다.
            동일 타입+채널 조합은 1개만 등록 가능합니다.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "템플릿 등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동일 타입+채널 템플릿 이미 존재")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NotificationTemplateResponse> create(
        @RequestBody @Valid NotificationTemplateRequest request
    ) {
        return ApiResponse.onCreated(templateService.create(request));
    }

    @Operation(summary = "템플릿 전체 조회", description = "등록된 모든 알림 템플릿을 타입·채널 순으로 조회합니다.")
    @GetMapping
    public ApiResponse<List<NotificationTemplateResponse>> findAll() {
        return ApiResponse.onSuccess(templateService.findAll());
    }

    @Operation(summary = "템플릿 단건 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "템플릿 없음")
    })
    @GetMapping("/{templateId}")
    public ApiResponse<NotificationTemplateResponse> findById(
        @Parameter(description = "템플릿 ID", example = "1")
        @PathVariable Long templateId
    ) {
        return ApiResponse.onSuccess(templateService.findById(templateId));
    }

    @Operation(
        summary = "템플릿 수정",
        description = "제목/본문 템플릿 내용을 수정합니다. 타입·채널은 변경할 수 없습니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "템플릿 없음")
    })
    @PutMapping("/{templateId}")
    public ApiResponse<NotificationTemplateResponse> update(
        @Parameter(description = "템플릿 ID", example = "1")
        @PathVariable Long templateId,
        @RequestBody @Valid NotificationTemplateRequest request
    ) {
        return ApiResponse.onSuccess(templateService.update(templateId, request));
    }

    @Operation(summary = "템플릿 삭제")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "템플릿 없음")
    })
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(
        @Parameter(description = "템플릿 ID", example = "1")
        @PathVariable Long templateId
    ) {
        templateService.delete(templateId);
        return ApiResponse.onSuccess();
    }
}
