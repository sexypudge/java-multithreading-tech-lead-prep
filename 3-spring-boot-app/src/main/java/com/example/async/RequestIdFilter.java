package com.example.async;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gắn requestId vào MDC cho mỗi request (giống traceId trong hệ thống thật).
 * Log pattern in %X{requestId} → nhìn log là biết dòng nào thuộc request nào, KỂ CẢ log từ thread của pool
 * (nhờ MdcTaskDecorator trong AsyncConfig). Thử bỏ setTaskDecorator(...) để thấy log async mất requestId.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    static final String KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(KEY, requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(KEY);   // thread của Tomcat được tái sử dụng → không dọn là rò sang request sau
        }
    }
}
