package com.liveklass.notification.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final Boolean isSuccess;
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T result;

    // --- 성공 케이스 ---
    public static <T> ApiResponse<T> onSuccess(HttpStatus status, String code, String message, T result) {
        return new ApiResponse<>(true, code, message, result);
    }


    public static <T> ApiResponse<T> onSuccess(T result) {
        return new ApiResponse<>(true, "COMMON200", "요청에 성공했습니다.", result);
    }

    public static <T> ApiResponse<T> onCreated(T result) {
        return new ApiResponse<>(true, "COMMON201", "성공적으로 생성되었습니다.", result);
    }

    public static <T> ApiResponse<T> onSuccess() {
        return new ApiResponse<>(true, "COMMON200", "성공적으로 처리되었습니다.", null);
    }

    // --- 실패 케이스 ---

    public static <T> ApiResponse<T> onFailure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public static <T> ApiResponse<T> onFailure(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }
}
