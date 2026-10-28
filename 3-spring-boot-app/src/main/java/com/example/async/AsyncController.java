package com.example.async;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST CONTROLLER - ASYNC ENDPOINTS
 * 
 * Demo các patterns:
 * 1. Sync vs Async comparison
 * 2. Parallel API calls
 * 3. Fire-and-forget
 * 4. Exception handling
 */
@RestController
@RequestMapping("/api")
public class AsyncController {

    @Autowired
    private AsyncService asyncService;

    /**
     * Sync endpoint - blocking
     * Response time = sum of all operations
     */
    @GetMapping("/sync")
    public String syncEndpoint() {
        long start = System.currentTimeMillis();
        
        String result1 = asyncService.processSync("Task1");
        String result2 = asyncService.processSync("Task2");
        String result3 = asyncService.processSync("Task3");
        
        long duration = System.currentTimeMillis() - start;
        
        return String.format("Sync completed in %dms\n%s\n%s\n%s", 
            duration, result1, result2, result3);
    }

    /**
     * Async endpoint - non-blocking
     * Response time = max of all operations (parallel)
     */
    @GetMapping("/async")
    public CompletableFuture<String> asyncEndpoint() {
        long start = System.currentTimeMillis();
        
        CompletableFuture<String> future1 = asyncService.fetchDataAsync("1");
        CompletableFuture<String> future2 = asyncService.fetchDataAsync("2");
        CompletableFuture<String> future3 = asyncService.fetchDataAsync("3");
        
        return CompletableFuture.allOf(future1, future2, future3)
            .thenApply(v -> {
                try {
                    long duration = System.currentTimeMillis() - start;
                    return String.format("Async completed in %dms\n%s\n%s\n%s",
                        duration, future1.get(), future2.get(), future3.get());
                } catch (Exception e) {
                    return "Error: " + e.getMessage();
                }
            });
    }

    /**
     * Parallel API calls
     * Real-world example: Aggregate data from multiple services
     */
    @GetMapping("/parallel")
    public CompletableFuture<String> parallelApiCalls() {
        long start = System.currentTimeMillis();
        
        CompletableFuture<String> userApi = asyncService.callExternalApi("user-service");
        CompletableFuture<String> orderApi = asyncService.callExternalApi("order-service");
        CompletableFuture<String> paymentApi = asyncService.callExternalApi("payment-service");
        
        return CompletableFuture.allOf(userApi, orderApi, paymentApi)
            .thenApply(v -> {
                try {
                    long duration = System.currentTimeMillis() - start;
                    return String.format(
                        "Parallel API calls completed in %dms\n" +
                        "User: %s\n" +
                        "Order: %s\n" +
                        "Payment: %s",
                        duration, userApi.get(), orderApi.get(), paymentApi.get()
                    );
                } catch (Exception e) {
                    return "Error: " + e.getMessage();
                }
            });
    }

    /**
     * Fire-and-forget pattern
     * Return immediately, process in background
     */
    @PostMapping("/process")
    public String fireAndForget(@RequestBody List<String> items) {
        System.out.println("Received " + items.size() + " items");
        
        // Process async, không wait
        items.forEach(item -> asyncService.processAsync(item));
        
        return "Processing " + items.size() + " items in background";
    }

    /**
     * Exception handling demo
     */
    @GetMapping("/error")
    public CompletableFuture<String> errorHandling() {
        return asyncService.processWithError("error-data")
            .exceptionally(ex -> "Handled error: " + ex.getMessage());
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public String health() {
        return "OK - Thread: " + Thread.currentThread().getName();
    }
}
