package com.xupu.smartdose.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        // SSE 长连接不支持 JSON 响应，直接忽略
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return null;
        }
        // SSE 序列化错误本身也忽略
        if (e instanceof HttpMessageNotWritableException) {
            return null;
        }
        log.error("接口异常: {}", e.getMessage(), e);
        return Result.fail(e.getMessage());
    }
}
