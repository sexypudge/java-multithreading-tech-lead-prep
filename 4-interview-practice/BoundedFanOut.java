import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BÀI TẬP (Tech Lead): Trang "product detail" cần dữ liệu từ 20 downstream service.
 *   - Downstream chỉ chịu tối đa 5 request đồng thời từ service của mình (bulkhead / concurrency limit)
 *   - Mỗi lời gọi có timeout 300ms (như read-timeout của HTTP client); lỗi/timeout → FALLBACK cho phần đó
 *   - Cả trang có deadline tổng 1.5s; quá hạn → phần chưa xong dùng fallback, KHÔNG chờ thêm
 *   - Không để rò thread / task chạy mãi sau khi đã trả response
 *
 * Hai lời giải để so sánh trade-off:
 *   A. CompletableFuture + executor có giới hạn (pool size = giới hạn đồng thời)
 *   B. Virtual threads + Semaphore + code đồng bộ đơn giản (Java 21)
 *
 * Chạy: java BoundedFanOut.java
 */
public class BoundedFanOut {

    static final int SERVICES = 20;
    static final int MAX_CONCURRENT = 5;
    static final long CLIENT_TIMEOUT_MS = 300;
    static final long OVERALL_DEADLINE_MS = 1500;

    /**
     * Downstream giả lập: thường 50-150ms, vài service chậm 1s (bị client timeout cắt ở 300ms), vài service lỗi.
     * Đếm số request đồng thời để kiểm chứng giới hạn.
     */
    static class Downstream {
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();

        String call(int id) throws InterruptedException, SocketTimeoutException {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                long latency = (id % 7 == 3) ? 1000 : 50 + ThreadLocalRandom.current().nextInt(100);
                if (latency > CLIENT_TIMEOUT_MS) {                      // client-side read timeout
                    Thread.sleep(CLIENT_TIMEOUT_MS);
                    throw new SocketTimeoutException("read timed out after " + CLIENT_TIMEOUT_MS + "ms");
                }
                Thread.sleep(latency);
                if (id % 9 == 5) throw new IllegalStateException("503 from service-" + id);
                return "data-" + id;
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    // ============================================
    // A. CompletableFuture + bounded executor
    // ============================================
    static Map<Integer, String> fanOutWithCompletableFuture(Downstream ds) {
        // Pool size = giới hạn đồng thời. (Số task bị chặn trên bởi SERVICES nên queue vô hạn của
        // newFixedThreadPool chấp nhận được ở đây; với tải đến từ người dùng → queue có giới hạn.)
        ExecutorService pool = Executors.newFixedThreadPool(MAX_CONCURRENT);
        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < SERVICES; i++) {
                final int id = i;
                futures.add(CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return ds.call(id);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new CompletionException(e);
                            } catch (SocketTimeoutException e) {
                                throw new CompletionException(e);
                            }
                        }, pool)
                        .exceptionally(ex -> "fallback(" + rootCause(ex).getClass().getSimpleName() + ")"));
                // ⚠ Không dùng orTimeout(300ms) cho per-call timeout ở đây: nó tính từ lúc SUBMIT, gồm cả thời gian
                //   xếp hàng chờ thread → task cuối hàng timeout oan. Timeout thật nên đặt ở client (như trên).
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(OVERALL_DEADLINE_MS, TimeUnit.MILLISECONDS);   // deadline tổng
            } catch (TimeoutException | ExecutionException ignored) {
                // phần chưa xong sẽ lấy fallback ở dưới
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Map<Integer, String> result = new LinkedHashMap<>();
            for (int i = 0; i < SERVICES; i++) result.put(i, futures.get(i).getNow("fallback(deadline)"));
            return result;
        } finally {
            pool.shutdownNow();   // interrupt lời gọi còn dở → không rò thread/việc sau khi đã trả response
        }
    }

    // ============================================
    // B. Virtual threads + Semaphore (code đồng bộ, dễ đọc, stack trace thật)
    // ============================================
    static Map<Integer, String> fanOutWithVirtualThreads(Downstream ds) throws InterruptedException {
        Semaphore permits = new Semaphore(MAX_CONCURRENT);         // "van" giới hạn đồng thời tới downstream
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OVERALL_DEADLINE_MS);
        Map<Integer, Future<String>> futures = new LinkedHashMap<>();

        try (ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < SERVICES; i++) {
                final int id = i;
                futures.put(id, vt.submit(() -> {
                    if (!permits.tryAcquire(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
                        return "fallback(no-permit-before-deadline)";
                    }
                    try {
                        return ds.call(id);                       // code chặn bình thường: virtual thread rẻ
                    } finally {
                        permits.release();
                    }
                }));
            }
            Map<Integer, String> result = new LinkedHashMap<>();
            for (var e : futures.entrySet()) {
                try {
                    result.put(e.getKey(), e.getValue().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
                } catch (TimeoutException ex) {
                    e.getValue().cancel(true);                    // interrupt virtual thread còn đang chờ
                    result.put(e.getKey(), "fallback(deadline)");
                } catch (ExecutionException ex) {
                    result.put(e.getKey(), "fallback(" + ex.getCause().getClass().getSimpleName() + ")");
                }
            }
            return result;
        }   // close(): chờ mọi virtual thread kết thúc (thread bị cancel thoát ngay vì bị interrupt)
    }

    static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t != t.getCause()) t = t.getCause();
        return t;
    }

    static void report(String name, Map<Integer, String> result, Downstream ds, long ms) {
        long ok = result.values().stream().filter(v -> v.startsWith("data-")).count();
        boolean pass = ds.maxInFlight.get() <= MAX_CONCURRENT && ms <= OVERALL_DEADLINE_MS + 200;
        System.out.printf("  %-22s %4d ms | thành công %2d/%d | max đồng thời tới downstream = %d (≤ %d) %s%n",
                name, ms, ok, SERVICES, ds.maxInFlight.get(), MAX_CONCURRENT, pass ? "✓" : "✗");
        Map<String, Long> reasons = new TreeMap<>();
        result.values().stream().filter(v -> !v.startsWith("data-")).forEach(v -> reasons.merge(v, 1L, Long::sum));
        System.out.println("      fallback theo lý do: " + reasons);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== BOUNDED FAN-OUT ===");
        System.out.println(SERVICES + " downstream, tối đa " + MAX_CONCURRENT + " đồng thời, client timeout "
                + CLIENT_TIMEOUT_MS + "ms, deadline tổng " + OVERALL_DEADLINE_MS + "ms\n");

        Downstream ds1 = new Downstream();
        long t0 = System.nanoTime();
        Map<Integer, String> r1 = fanOutWithCompletableFuture(ds1);
        report("A. CompletableFuture", r1, ds1, (System.nanoTime() - t0) / 1_000_000);

        Downstream ds2 = new Downstream();
        t0 = System.nanoTime();
        Map<Integer, String> r2 = fanOutWithVirtualThreads(ds2);
        report("B. Virtual threads", r2, ds2, (System.nanoTime() - t0) / 1_000_000);

        System.out.println("\nĐiểm cần nói khi trình bày lời giải:");
        System.out.println("  1. Giới hạn đồng thời bảo vệ DOWNSTREAM - không phải để app nhanh hơn. Thiếu nó, 1000 user × 20 call");
        System.out.println("     = 20.000 request đồng thời đổ vào service khác (cascading failure).");
        System.out.println("  2. Timeout ở MỌI tầng: HTTP client (connect/read), từng call, và deadline tổng của request.");
        System.out.println("     CompletableFuture.orTimeout KHÔNG dừng lời gọi đang chạy và tính cả thời gian xếp hàng.");
        System.out.println("  3. Fallback trả một phần dữ liệu (degraded) tốt hơn lỗi cả trang; đánh dấu rõ phần nào là fallback.");
        System.out.println("  4. Production: Resilience4j (Bulkhead, TimeLimiter, CircuitBreaker, Retry) thay vì tự viết,");
        System.out.println("     nhưng phải giải thích được cơ chế bên dưới như trên.");
        System.out.println("  5. A vs B: A tiện ghép nối/biến đổi kết quả; B code tuần tự dễ đọc, stack trace rõ, không lo pool cạn");
        System.out.println("     thread - nhưng vẫn PHẢI có Semaphore vì virtual thread không giới hạn gì cả.");
    }
}
