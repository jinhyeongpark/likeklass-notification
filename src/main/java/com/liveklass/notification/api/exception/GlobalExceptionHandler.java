package com.liveklass.notification.api.exception;

import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.common.util.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. DTO 유효성 검사 실패 (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
        MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorCode ec = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ApiResponse.onFailure(ec.getCode(), ec.getMessage(), errors));
    }

    // 2. 비즈니스 예외 (CustomException) 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
        ErrorCode ec = ex.getErrorCode();

        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ApiResponse.onFailure(ec.getCode(), ec.getMessage()));
    }

    // 3. DB 제약 조건 위반 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
        DataIntegrityViolationException ex) {
        log.error("[DataIntegrityViolationException] : {}", ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.onFailure("COMMON-409", "데이터 무결성 위반이 발생했습니다."));
    }

    // 4. 그 외 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllException(Exception ex) {
        log.error("[Internal Server Error] : ", ex);

        ErrorCode ec = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ApiResponse.onFailure(ec.getCode(), ec.getMessage()));
    }
}
