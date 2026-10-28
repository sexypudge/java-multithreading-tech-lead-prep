package com.example.async;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SCHEDULED TASKS
 * 
 * @Scheduled patterns:
 * - fixedRate: Fixed interval từ start time
 * - fixedDelay: Fixed interval từ completion time
 * - cron: Cron expression
 * 
 * Interview Question: "fixedRate vs fixedDelay?"
 * Answer: fixedRate - interval từ start, có thể overlap
 *         fixedDelay - interval từ end, không overlap
 */
@Component
public class ScheduledTasks {

    private static final DateTimeFormatter formatter = 
        DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Fixed rate - chạy mỗi 10 giây
     * Interval tính từ start time
     */
    @Scheduled(fixedRate = 10000)
    public void fixedRateTask() {
        System.out.println("[Fixed Rate] " + getCurrentTime() + 
            " - Thread: " + Thread.currentThread().getName());
    }

    /**
     * Fixed delay - chạy 5 giây sau khi task trước complete
     * Interval tính từ completion time
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 2000)
    public void fixedDelayTask() {
        System.out.println("[Fixed Delay] " + getCurrentTime() + 
            " - Thread: " + Thread.currentThread().getName());
    }

    /**
     * Cron expression - chạy mỗi phút
     * Format: second minute hour day month weekday
     */
    @Scheduled(cron = "0 * * * * *")
    public void cronTask() {
        System.out.println("[Cron] " + getCurrentTime() + 
            " - Running every minute");
    }

    /**
     * Cleanup task - chạy mỗi 30 giây
     */
    @Scheduled(fixedRate = 30000)
    public void cleanupTask() {
        System.out.println("[Cleanup] " + getCurrentTime() + 
            " - Performing cleanup...");
        // Simulate cleanup work
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Cleanup] " + getCurrentTime() + " - Done");
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(formatter);
    }
}
