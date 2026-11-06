package com.example.async;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SPRING BOOT ASYNC DEMO
 *
 *   AsyncConfig      - 2 executor (bulkhead), bounded queue, rejection policy, MDC TaskDecorator, uncaught handler
 *   AsyncService     - @Async methods, self-invocation pitfall, exception theo kiểu trả về
 *   AsyncController  - sync vs parallel, timeout/fallback, back-pressure, pool stats
 *   RequestIdFilter  - requestId trong MDC để theo dõi log xuyên thread
 *   ScheduledTasks   - fixedRate / fixedDelay / cron và các bẫy production
 *
 * Chạy: mvn spring-boot:run   → xem README.md cho danh sách curl.
 */
@SpringBootApplication
public class AsyncDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncDemoApplication.class, args);
    }
}
