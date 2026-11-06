package com.example.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * @Async = Spring bọc bean bằng PROXY; proxy đẩy thân method vào Executor rồi trả về ngay.
 *
 * Hệ quả của việc là proxy (hay bị hỏi):
 *   1. Self-invocation: gọi this.asyncMethod() trong CÙNG class KHÔNG qua proxy → chạy ĐỒNG BỘ (xem selfInvocation()).
 *   2. Method private/final, class final → proxy không chặn được → chạy đồng bộ.
 *   3. Thiếu @EnableAsync → không có gì xảy ra, không báo lỗi.
 *   (@Transactional có đúng các bẫy proxy này.)
 *
 * Kiểu trả về quyết định exception đi đâu:
 *   - CompletableFuture<T> : lỗi nằm trong future → caller xử lý bằng exceptionally/handle
 *   - void                 : caller KHÔNG thấy lỗi → AsyncUncaughtExceptionHandler (AsyncConfig)
 */
@Service
public class AsyncService {

    private static final Logger log = LoggerFactory.getLogger(AsyncService.class);

    /** Giả lập lời gọi downstream (HTTP/DB) mất latencyMs. */
    @Async("ioExecutor")
    public CompletableFuture<String> callDownstream(String service, long latencyMs) {
        log.info("calling {} ({} ms)", service, latencyMs);    // log có requestId nhờ MdcTaskDecorator
        sleep(latencyMs);
        return CompletableFuture.completedFuture(service + " OK");
    }

    /** Ném exception thẳng: Spring tự hoàn thành CompletableFuture trả về bằng exception đó. */
    @Async("ioExecutor")
    public CompletableFuture<String> failingCall() {
        log.info("calling flaky-service");
        throw new IllegalStateException("flaky-service returned 503");
    }

    /** Fire-and-forget. Lỗi ở đây chỉ được AsyncUncaughtExceptionHandler nhìn thấy. */
    @Async("backgroundExecutor")
    public void sendNotification(String recipient) {
        log.info("sending notification to {}", recipient);
        sleep(300);
        if (recipient.contains("fail")) {
            throw new IllegalArgumentException("invalid recipient: " + recipient);
        }
        log.info("notification sent to {}", recipient);
    }

    /** Việc nền chậm, trả về tên thread đã chạy nó → để quan sát CallerRunsPolicy. */
    @Async("backgroundExecutor")
    public CompletableFuture<String> slowBackgroundJob(int id) {
        sleep(500);
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    /** Trả về tên thread thực thi + requestId thấy được trong MDC. */
    @Async("ioExecutor")
    public CompletableFuture<String> whereAmI() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().getName() + " (requestId=" + MDC.get(RequestIdFilter.KEY) + ")");
    }

    /** ✗ Self-invocation: this.whereAmI() không đi qua proxy → chạy NGAY trên thread gọi. */
    public String selfInvocation() {
        return whereAmI().join();
    }

    /** Phiên bản đồng bộ để so sánh. */
    public String callDownstreamSync(String service, long latencyMs) {
        log.info("calling {} synchronously ({} ms)", service, latencyMs);
        sleep(latencyMs);
        return service + " OK";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
