package com.example.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ASYNC CONFIGURATION
 *
 * Nguyên tắc production (mỗi dòng đều là một câu hỏi phỏng vấn):
 *   1. Tự khai báo executor CÓ TÊN - không dựa vào mặc định (Spring Boot mặc định: queue KHÔNG giới hạn).
 *   2. Queue CÓ GIỚI HẠN + rejection policy rõ ràng → có back-pressure thay vì OOM âm thầm.
 *   3. Tách pool theo loại việc (BULKHEAD): việc nền chậm không được ăn hết thread của lời gọi downstream.
 *   4. Thread name prefix có nghĩa → đọc thread dump / log biết ngay pool nào.
 *   5. TaskDecorator copy MDC (traceId/requestId) sang thread của pool, và DỌN trong finally.
 *   6. AsyncUncaughtExceptionHandler cho method @Async trả void (caller không có cách nào thấy lỗi).
 *   7. Shutdown êm: chờ task đang chạy xong (kết hợp server.shutdown=graceful).
 *
 * Mọi ThreadPoolTaskExecutor bean được Spring Boot Actuator tự gắn metric:
 *   /actuator/metrics/executor.active?tag=name:ioExecutor   (executor.queued, executor.pool.size, executor.completed...)
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Pool cho lời gọi I/O tới downstream (HTTP/DB). Kích thước phải khớp với khả năng của downstream
     * (vd: DB connection pool 10 → hơn 10 thread cùng gọi DB chỉ xếp hàng chờ connection).
     */
    @Bean(name = "ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);                 // CÓ GIỚI HẠN: core → queue → max → reject
        executor.setThreadNamePrefix("io-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Abort: gọi downstream mà pool đầy thì fail-fast để caller trả lỗi/fallback, đừng chờ vô hạn
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;                                // Spring tự gọi initialize() (InitializingBean)
    }

    /**
     * Pool nhỏ, tách riêng cho việc nền (gửi notification, ghi audit...). CallerRuns: khi đầy, thread GỌI
     * tự chạy task → caller chậm lại → back-pressure tự nhiên (xem GET /api/saturate).
     */
    @Bean(name = "backgroundExecutor")
    public ThreadPoolTaskExecutor backgroundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("bg-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * Executor cho @Async KHÔNG chỉ định tên. Gọi method @Bean trong class @Configuration trả về đúng
     * singleton bean (class được CGLIB proxy), không tạo pool mới.
     */
    @Override
    public Executor getAsyncExecutor() {
        return ioExecutor();
    }

    /** Chỉ áp dụng cho method @Async trả về void. Method trả CompletableFuture thì lỗi nằm trong future. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("@Async void method {}({}) failed - caller never sees this; "
                + "alert / retry / dead-letter here", method.getName(), Arrays.toString(params), ex);
    }

    /**
     * MDC dựa trên ThreadLocal → KHÔNG tự đi theo sang thread khác.
     * Chụp ở thread GỌI (lúc submit), gắn lại ở thread CHẠY, dọn sau khi xong (thread được tái sử dụng!).
     * Cùng kỹ thuật cho SecurityContext (hoặc dùng DelegatingSecurityContextAsyncTaskExecutor của Spring Security).
     */
    static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable task) {
            Map<String, String> captured = MDC.getCopyOfContextMap();      // chạy trên thread gọi
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();  // CallerRuns: có thể chính là thread gọi
                try {
                    if (captured != null) MDC.setContextMap(captured); else MDC.clear();
                    task.run();
                } finally {
                    if (previous != null) MDC.setContextMap(previous); else MDC.clear();
                }
            };
        }
    }
}
