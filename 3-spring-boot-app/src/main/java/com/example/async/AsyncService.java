package com.example.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

/**
 * ASYNC SERVICE - @ASYNC METHODS
 * 
 * @Async rules:
 * 1. Phải là public method
 * 2. Không gọi từ cùng class (proxy issue)
 * 3. Return void hoặc Future/CompletableFuture
 * 
 * Interview Question: "@Async không work, tại sao?"
 * Answer: Self-invocation (gọi từ cùng class), method không public,
 *         quên @EnableAsync, hoặc không có thread pool config
 */
@Service
public class AsyncService {

    /**
     * Async method - fire and forget
     */
    @Async("taskExecutor")
    public void processAsync(String data) {
        System.out.println("[" + Thread.currentThread().getName() + "] Processing: " + data);
        sleep(2000);
        System.out.println("[" + Thread.currentThread().getName() + "] Done: " + data);
    }

    /**
     * Async method với return value
     */
    @Async("taskExecutor")
    public CompletableFuture<String> fetchDataAsync(String id) {
        System.out.println("[" + Thread.currentThread().getName() + "] Fetching: " + id);
        sleep(1000);
        String result = "Data-" + id;
        System.out.println("[" + Thread.currentThread().getName() + "] Fetched: " + result);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Simulate external API call
     */
    @Async("taskExecutor")
    public CompletableFuture<String> callExternalApi(String endpoint) {
        System.out.println("[" + Thread.currentThread().getName() + "] Calling API: " + endpoint);
        sleep(1500);
        return CompletableFuture.completedFuture("Response from " + endpoint);
    }

    /**
     * Async method với exception handling
     */
    @Async("taskExecutor")
    public CompletableFuture<String> processWithError(String data) {
        try {
            System.out.println("[" + Thread.currentThread().getName() + "] Processing: " + data);
            
            if (data.contains("error")) {
                throw new RuntimeException("Simulated error!");
            }
            
            sleep(1000);
            return CompletableFuture.completedFuture("Success: " + data);
        } catch (Exception e) {
            System.err.println("[" + Thread.currentThread().getName() + "] Error: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Sync method for comparison
     */
    public String processSync(String data) {
        System.out.println("[" + Thread.currentThread().getName() + "] Sync processing: " + data);
        sleep(2000);
        return "Sync result: " + data;
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
