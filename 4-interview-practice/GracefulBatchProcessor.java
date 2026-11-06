import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * BÀI TẬP (Tech Lead): Thiết kế batch processor xử lý 200 bản ghi (vd: gửi hoá đơn) với yêu cầu:
 *   1. Concurrency có giới hạn (4 worker) và BACK-PRESSURE - không nạp cả triệu task vào RAM
 *   2. Retry có giới hạn với exponential backoff + jitter cho lỗi tạm thời
 *   3. IDEMPOTENCY: bản ghi bị nộp trùng không được xử lý 2 lần
 *   4. GRACEFUL SHUTDOWN: nhận tín hiệu dừng → ngừng nhận việc mới, cho việc đang chạy một khoảng thời gian,
 *      sau đó interrupt; task phải phản hồi interrupt; báo cáo việc nào chưa xong
 *   5. Có số liệu: processed / failed / skipped / rejected
 *
 * Chạy: java GracefulBatchProcessor.java
 */
public class GracefulBatchProcessor {

    static final int WORKERS = 4;
    static final int MAX_ATTEMPTS = 3;

    final AtomicInteger processed = new AtomicInteger();
    final AtomicInteger failed = new AtomicInteger();
    final AtomicInteger duplicatesSkipped = new AtomicInteger();
    final AtomicInteger interrupted = new AtomicInteger();
    final Set<Integer> done = ConcurrentHashMap.newKeySet();          // thực tế: bảng DB có unique key / processed_at
    private final AtomicInteger threadSeq = new AtomicInteger();

    final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            WORKERS, WORKERS, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),                              // queue NHỎ, có giới hạn
            r -> new Thread(r, "batch-worker-" + threadSeq.incrementAndGet()),
            new ThreadPoolExecutor.CallerRunsPolicy());                 // đầy → thread nộp việc tự chạy → tự chậm lại

    /** Xử lý 1 bản ghi: lỗi tạm thời ~20% mỗi lần thử. */
    void handle(int recordId) throws InterruptedException {
        Thread.sleep(20);                                              // I/O giả lập; sleep phản hồi interrupt
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            throw new IllegalStateException("transient error for record " + recordId);
        }
    }

    void process(int recordId) {
        // Idempotency: add() trên ConcurrentHashMap.newKeySet là atomic - chỉ 1 thread "giành" được bản ghi.
        // (Ở đây đánh dấu TRƯỚC khi xử lý cho gọn; production thường: ghi trạng thái PROCESSING/DONE trong DB
        //  với unique constraint, hoặc outbox pattern, để crash giữa chừng cũng không mất/không trùng.)
        if (!done.add(recordId)) {
            duplicatesSkipped.incrementAndGet();
            return;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                handle(recordId);
                processed.incrementAndGet();
                return;
            } catch (InterruptedException e) {
                // Shutdown đang diễn ra: KHÔNG retry, khôi phục cờ và thoát; bỏ đánh dấu để lần chạy sau xử lý lại
                done.remove(recordId);
                interrupted.incrementAndGet();
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    failed.incrementAndGet();                          // production: đưa vào dead-letter queue + alert
                    return;
                }
                long backoff = (long) (10 * Math.pow(2, attempt - 1));                 // 10, 20, 40 ms...
                long jitter = ThreadLocalRandom.current().nextLong(backoff + 1);       // full jitter: tránh retry đồng loạt
                try {
                    Thread.sleep(jitter);
                } catch (InterruptedException ie) {
                    done.remove(recordId);
                    interrupted.incrementAndGet();
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Graceful shutdown theo mẫu Javadoc của ExecutorService. Trả về số task chưa chạy bị huỷ. */
    int shutdownGracefully(long graceMs) throws InterruptedException {
        pool.shutdown();                                               // 1. ngừng nhận việc mới
        if (pool.awaitTermination(graceMs, TimeUnit.MILLISECONDS)) return 0;  // 2. cho việc đang chạy thời gian ân hạn
        List<Runnable> neverStarted = pool.shutdownNow();              // 3. interrupt + lấy lại việc chưa chạy
        if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {             // 4. chờ lần cuối
            System.err.println("  pool không dừng được - có task không phản hồi interrupt!");
        }
        return neverStarted.size();                                    // production: ghi lại để chạy lần sau
    }

    void printStats(String label, int unstarted) {
        System.out.printf("  %-28s processed=%d failed=%d duplicatesSkipped=%d interrupted=%d neverStarted=%d%n",
                label, processed.get(), failed.get(), duplicatesSkipped.get(), interrupted.get(), unstarted);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== GRACEFUL BATCH PROCESSOR ===\n");

        // --- Lần chạy 1: chạy hết, có bản ghi bị nộp TRÙNG ---
        GracefulBatchProcessor p1 = new GracefulBatchProcessor();
        List<Integer> records = IntStream.range(0, 200).boxed().toList();
        for (int id : records) p1.pool.execute(() -> p1.process(id));
        for (int id = 0; id < 30; id++) { final int dup = id; p1.pool.execute(() -> p1.process(dup)); }  // nộp trùng
        int unstarted1 = p1.shutdownGracefully(30_000);
        p1.printStats("Chạy hết (200 + 30 trùng):", unstarted1);
        int total = p1.processed.get() + p1.failed.get();
        System.out.println("  processed + failed = " + total + " (phải 200), duplicatesSkipped = 30  "
                + (total == 200 && p1.duplicatesSkipped.get() == 30 ? "✓" : "✗"));

        // --- Lần chạy 2: nhận tín hiệu dừng giữa chừng (vd: SIGTERM khi deploy) ---
        GracefulBatchProcessor p2 = new GracefulBatchProcessor();
        Thread submitter = new Thread(() -> {
            for (int id = 0; id < 10_000; id++) {
                // ⚠ Sau shutdown(), CallerRunsPolicy LẶNG LẼ BỎ task (không ném exception) → phải tự kiểm tra
                if (p2.pool.isShutdown()) return;
                final int rec = id;
                p2.pool.execute(() -> p2.process(rec));                // CallerRuns: submitter tự chậm lại theo worker
            }
        }, "submitter");
        submitter.start();
        Thread.sleep(300);
        System.out.println("\n  Tín hiệu dừng sau 300ms, queue đang có " + p2.pool.getQueue().size()
                + " task (không phải 10.000 - nhờ queue có giới hạn + CallerRuns)");
        int unstarted2 = p2.shutdownGracefully(50);                    // ân hạn ngắn để thấy shutdownNow
        submitter.join();
        p2.printStats("Dừng giữa chừng:", unstarted2);
        System.out.println("  → Việc bị interrupt được bỏ đánh dấu 'done' để lần chạy sau làm lại; việc chưa chạy được trả về.");

        System.out.println("\nĐiểm cần nói khi trình bày:");
        System.out.println("  - Đăng ký Runtime.getRuntime().addShutdownHook(...) (hoặc @PreDestroy / SmartLifecycle trong Spring)");
        System.out.println("    để gọi shutdownGracefully khi nhận SIGTERM; Kubernetes cho terminationGracePeriodSeconds (mặc định 30s).");
        System.out.println("  - Retry chỉ cho lỗi TẠM THỜI (timeout, 503); lỗi nghiệp vụ (400) retry vô ích.");
        System.out.println("  - Idempotency là bắt buộc khi có retry hoặc at-least-once delivery (Kafka/SQS).");
        System.out.println("  - Metric + alert: processed/failed rate, queue depth, thời gian xử lý p99, số lần retry.");
    }
}
