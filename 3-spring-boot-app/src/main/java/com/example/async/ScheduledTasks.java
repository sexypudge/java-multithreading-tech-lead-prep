package com.example.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * SCHEDULED TASKS
 *
 *   fixedRate  : chu kỳ tính từ lúc BẮT ĐẦU lần trước. Task chạy lâu hơn chu kỳ → lần sau chạy NGAY khi lần trước
 *                xong. Mặc định KHÔNG chạy chồng lấn (cùng 1 method không bao giờ chạy song song với chính nó).
 *                (Chỉ chồng lấn nếu thêm @Async lên method scheduled.)
 *   fixedDelay : chờ N ms SAU KHI lần trước KẾT THÚC.
 *   cron       : theo lịch "giây phút giờ ngày tháng thứ".
 *
 * Bẫy production (câu hỏi Tech Lead):
 *   1. Scheduler mặc định của Spring Boot chỉ có 1 THREAD → một task chậm làm trễ MỌI task khác.
 *      → spring.task.scheduling.pool.size (application.properties đặt = 2), thread-name-prefix "sched-".
 *   2. Chạy N instance (Kubernetes replicas) → mỗi instance đều chạy job → job chạy N lần.
 *      → Distributed lock (ShedLock, DB row lock) + job phải idempotent.
 *   3. Exception trong @Scheduled: Spring log lỗi và lần sau VẪN chạy (khác ScheduledExecutorService thuần:
 *      exception làm lịch bị huỷ âm thầm - xem 2-advanced-concurrency/ExecutorServiceDemo Demo 8).
 *   4. Job dài + shutdown: cần xử lý interrupt và dừng êm.
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private final AtomicInteger heartbeat = new AtomicInteger();

    /** Mỗi 10s tính từ lúc bắt đầu. */
    @Scheduled(fixedRate = 10_000)
    public void heartbeat() {
        log.info("[fixedRate] heartbeat #{}", heartbeat.incrementAndGet());
    }

    /** Task chậm (3s) với fixedDelay 15s: khoảng cách giữa 2 lần bắt đầu ≈ 18s. */
    @Scheduled(fixedDelay = 15_000, initialDelay = 2_000)
    public void cleanup() {
        log.info("[fixedDelay] cleanup started");
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // shutdown đang interrupt → dừng, không nuốt
            return;
        }
        log.info("[fixedDelay] cleanup done");
    }

    /** Đầu mỗi phút. Trên nhiều instance cần ShedLock: @SchedulerLock(name = "report", lockAtMostFor = "5m"). */
    @Scheduled(cron = "0 * * * * *")
    public void minutelyReport() {
        log.info("[cron] minutely report");
    }

    /** Tắt scheduling bằng demo.scheduling.enabled=false (test dùng để log đỡ ồn). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "demo.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
