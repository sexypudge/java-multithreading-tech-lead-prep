package com.example.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

/**
 * ASYNC CONFIGURATION - THREAD POOL TUNING
 * 
 * Interview Question: "Làm sao tune thread pool cho production?"
 * Answer:
 * - Core size: Minimum threads always alive
 * - Max size: Maximum threads under load
 * - Queue capacity: Pending tasks buffer
 * - Monitor metrics và adjust
 */
@Configuration
public class AsyncConfig {

    /**
     * Custom thread pool cho @Async methods
     * 
     * Tuning guidelines:
     * - CPU-bound: cores + 1
     * - I/O-bound: cores * (1 + wait/compute)
     * - Start conservative, monitor, adjust
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size - minimum threads
        executor.setCorePoolSize(5);
        
        // Max pool size - maximum threads
        executor.setMaxPoolSize(10);
        
        // Queue capacity - pending tasks
        executor.setQueueCapacity(100);
        
        // Thread name prefix
        executor.setThreadNamePrefix("Async-");
        
        // Rejection policy
        // CallerRunsPolicy - run in caller thread if pool full
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        System.out.println("Thread Pool configured:");
        System.out.println("  Core: " + executor.getCorePoolSize());
        System.out.println("  Max: " + executor.getMaxPoolSize());
        System.out.println("  Queue: " + executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * Separate executor cho scheduled tasks
     */
    @Bean(name = "scheduledExecutor")
    public Executor scheduledExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Scheduled-");
        executor.initialize();
        return executor;
    }
}
