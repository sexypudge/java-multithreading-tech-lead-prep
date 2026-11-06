package com.example.async;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Mỗi endpoint minh hoạ một câu hỏi phỏng vấn. Xem README.md để biết lệnh curl và kết quả mong đợi.
 */
@RestController
@RequestMapping("/api")
public class AsyncController {

    private final AsyncService service;
    private final ThreadPoolTaskExecutor ioExecutor;
    private final ThreadPoolTaskExecutor backgroundExecutor;

    public AsyncController(AsyncService service,
                           @Qualifier("ioExecutor") ThreadPoolTaskExecutor ioExecutor,
                           @Qualifier("backgroundExecutor") ThreadPoolTaskExecutor backgroundExecutor) {
        this.service = service;
        this.ioExecutor = ioExecutor;
        this.backgroundExecutor = backgroundExecutor;
    }

    /** Tuần tự: tổng latency = cộng dồn (~600 + 800 + 400 = 1800ms). */
    @GetMapping("/sync")
    public Map<String, Object> sync() {
        long start = System.nanoTime();
        List<String> results = List.of(
                service.callDownstreamSync("user-service", 600),
                service.callDownstreamSync("order-service", 800),
                service.callDownstreamSync("payment-service", 400));
        return timed(start, results);
    }

    /** Song song: tổng latency ≈ lời gọi chậm nhất (~800ms). Fan-out / fan-in bằng CompletableFuture. */
    @GetMapping("/parallel")
    public CompletableFuture<Map<String, Object>> parallel() {
        long start = System.nanoTime();
        CompletableFuture<String> user = service.callDownstream("user-service", 600);
        CompletableFuture<String> order = service.callDownstream("order-service", 800);
        CompletableFuture<String> payment = service.callDownstream("payment-service", 400);
        // Trả CompletableFuture cho Spring MVC: thread Tomcat được nhả ngay, response gửi khi future xong
        return CompletableFuture.allOf(user, order, payment)
                .thenApply(v -> timed(start, List.of(user.join(), order.join(), payment.join())));
    }

    /**
     * Timeout + fallback cho downstream chậm. ⚠ completeOnTimeout KHÔNG huỷ task đang chạy trong pool:
     * thread io- vẫn bận tới khi lời gọi thật xong → luôn đặt timeout ở chính HTTP/DB client nữa.
     */
    @GetMapping("/timeout")
    public CompletableFuture<Map<String, Object>> timeout() {
        long start = System.nanoTime();
        return service.callDownstream("slow-recommendation-service", 3000)
                .completeOnTimeout("fallback: default recommendations", 1, TimeUnit.SECONDS)
                .thenApply(result -> timed(start, List.of(result)));
    }

    /** CompletableFuture lỗi → xử lý bằng exceptionally (cause bị bọc trong CompletionException). */
    @GetMapping("/error")
    public CompletableFuture<String> error() {
        return service.failingCall()
                .exceptionally(ex -> "handled: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
    }

    /** Fire-and-forget: trả về ngay. Recipient chứa "fail" → lỗi chỉ hiện trong log (AsyncUncaughtExceptionHandler). */
    @PostMapping("/notify")
    public String sendNotifications(@RequestBody List<String> recipients) {
        recipients.forEach(service::sendNotification);
        return "accepted " + recipients.size() + " notifications (check the logs)";
    }

    /** Bẫy self-invocation: gọi qua proxy chạy trên io-*, gọi this.method() chạy trên thread Tomcat. */
    @GetMapping("/self-invocation")
    public Map<String, String> selfInvocation() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("controllerThread", Thread.currentThread().getName());
        result.put("viaProxy", service.whereAmI().join());
        result.put("selfInvocation", service.selfInvocation());
        return result;
    }

    /**
     * Back-pressure với CallerRunsPolicy: backgroundExecutor (core 2, max 4, queue 10) nhận nhiều task 500ms.
     * Khi pool + queue đầy, task chạy luôn trên thread Tomcat (http-nio-*) → request này chậm lại.
     */
    @GetMapping("/saturate")
    public Map<String, Object> saturate(@RequestParam(defaultValue = "30") int tasks) {
        long start = System.nanoTime();
        List<CompletableFuture<String>> futures = IntStream.range(0, tasks)
                .mapToObj(service::slowBackgroundJob)
                .toList();
        Map<String, Long> byThreadGroup = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.groupingBy(name -> name.startsWith("bg-") ? "bg-* (pool)" : name + " (caller ran it)",
                        TreeMap::new, Collectors.counting()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tasks", tasks);
        result.put("executedBy", byThreadGroup);
        result.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    /** Ảnh chụp các chỉ số pool - đúng những gì cần đưa lên dashboard/alert. */
    @GetMapping("/pools")
    public Map<String, Object> pools() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ioExecutor", stats(ioExecutor));
        result.put("backgroundExecutor", stats(backgroundExecutor));
        return result;
    }

    private static Map<String, Object> stats(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor tpe = executor.getThreadPoolExecutor();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("poolSize", tpe.getPoolSize());
        s.put("activeCount", tpe.getActiveCount());
        s.put("corePoolSize", tpe.getCorePoolSize());
        s.put("maxPoolSize", tpe.getMaximumPoolSize());
        s.put("queueSize", tpe.getQueue().size());
        s.put("queueRemainingCapacity", tpe.getQueue().remainingCapacity());
        s.put("completedTaskCount", tpe.getCompletedTaskCount());
        return s;
    }

    private static Map<String, Object> timed(long startNanos, List<String> results) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("elapsedMs", (System.nanoTime() - startNanos) / 1_000_000);
        m.put("results", results);
        return m;
    }
}
