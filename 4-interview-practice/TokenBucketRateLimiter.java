import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BÀI TẬP: Viết rate limiter thread-safe: tối đa `ratePerSecond` request/giây, cho phép burst tới `capacity`.
 *
 * Token bucket:
 *   - Xô chứa tối đa `capacity` token, được nạp lại đều `ratePerSecond` token/giây.
 *   - Mỗi request lấy 1 token; hết token → từ chối (429) hoặc chờ.
 *   - Nạp "lười" (lazy refill): không cần thread nền, tính số token cần thêm dựa trên thời gian trôi qua.
 *
 * Phân biệt khi phỏng vấn:
 *   - Rate limiter    : giới hạn SỐ REQUEST TRÊN GIÂY (throughput)        → token bucket / sliding window
 *   - Concurrency limit: giới hạn SỐ REQUEST ĐANG CHẠY CÙNG LÚC (in-flight) → Semaphore / bulkhead
 *   - Nhiều instance  : limiter trong JVM chỉ giới hạn từng instance → cần Redis/API gateway cho giới hạn toàn cục
 *
 * Chạy: java TokenBucketRateLimiter.java
 */
public class TokenBucketRateLimiter {

    private final long capacity;
    private final double refillPerNano;
    private double tokens;                 // bảo vệ bởi 'this'
    private long lastRefillNanos;          // bảo vệ bởi 'this'

    public TokenBucketRateLimiter(long ratePerSecond, long capacity) {
        this.capacity = capacity;
        this.refillPerNano = ratePerSecond / 1_000_000_000.0;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();   // nanoTime: đơn điệu, không bị ảnh hưởng khi đổi giờ hệ thống
    }

    /**
     * synchronized vì refill + kiểm tra + trừ token là MỘT thao tác ghép (check-then-act trên 2 biến).
     * Critical section rất ngắn (vài phép tính) → lock gần như không tranh chấp đáng kể.
     * (Có thể làm lock-free bằng AtomicLong + CAS trên trạng thái đóng gói, nhưng phức tạp hơn mà ít lợi.)
     */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
        lastRefillNanos = now;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== TOKEN BUCKET RATE LIMITER ===\n");

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100, 20);   // 100 req/s, burst 20
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        final long durationMs = 2000;
        final int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                while (System.nanoTime() < end) {
                    if (limiter.tryAcquire()) allowed.incrementAndGet(); else rejected.incrementAndGet();
                    Thread.onSpinWait();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        long expectedMax = 20 + 100 * durationMs / 1000;      // burst ban đầu + nạp lại trong 2s
        System.out.println("  8 thread spam trong " + durationMs + "ms: allowed=" + allowed.get()
                + " (≤ " + expectedMax + "), rejected=" + rejected.get()
                + (allowed.get() <= expectedMax + 2 ? "  ✓" : "  ✗"));

        System.out.println("\nCâu hỏi follow-up:");
        System.out.println("  - Muốn CHỜ thay vì từ chối? → tính thời gian tới token kế tiếp rồi park; hoặc Guava RateLimiter.acquire().");
        System.out.println("  - Mỗi user một limiter? → ConcurrentHashMap<userId, Limiter>.computeIfAbsent + eviction (Caffeine) để không rò RAM.");
        System.out.println("  - 10 instance sau load balancer? → limiter phân tán (Redis + Lua script atomic) hoặc ở API gateway.");
        System.out.println("  - Production: Resilience4j RateLimiter / Bucket4j thay vì tự viết.");
    }
}
