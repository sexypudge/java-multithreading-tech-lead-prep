import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VIRTUAL THREADS (Java 21+, JEP 444)
 *
 * Platform thread (thread truyền thống) : 1-1 với thread của OS, ~1MB stack, tạo/đổi ngữ cảnh đắt → chỉ vài nghìn.
 * Virtual thread                        : do JVM quản lý, stack nằm ở HEAP và nhỏ (vài KB) → hàng trăm nghìn/triệu.
 *
 * Cơ chế:
 *   - Virtual thread được "gắn" (mount) lên một CARRIER THREAD (platform thread trong ForkJoinPool) để chạy.
 *   - Khi gặp thao tác CHẶN kiểu IO/sleep/lock của java.util.concurrent → nó được "gỡ" (unmount),
 *     carrier thread rảnh đi chạy virtual thread khác. Xong chờ thì mount lại.
 *   ⇒ Viết code đồng bộ, đơn giản (thread-per-request) mà vẫn chịu được lượng đồng thời rất lớn cho việc IO-bound.
 *
 * KHÔNG phải để chạy NHANH hơn:
 *   - Không tăng tốc việc CPU-bound (vẫn bị giới hạn bởi số core).
 *   - Lợi ích là THROUGHPUT (số việc đồng thời) cho IO-bound, không phải latency của 1 request.
 *
 * PINNING (bị "đóng đinh"): virtual thread KHÔNG unmount được → giữ chặt carrier thread khi:
 *   - đang trong khối synchronized (Java 21-23; từ Java 24 JEP 491 đã khắc phục cho synchronized)
 *   - đang gọi native method / foreign function
 *   Cách tránh (Java 21-23): thay synchronized bằng ReentrantLock quanh thao tác chặn.
 *   Phát hiện: -Djdk.tracePinnedThreads=full
 *
 * Chạy (cần JDK 21+): java VirtualThreadsDemo.java
 */
public class VirtualThreadsDemo {

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ============================================
    // DEMO 1: 2000 task chặn 200ms - pool 100 thread vs virtual thread
    // ============================================
    static void demo1Throughput() throws InterruptedException {
        final int TASKS = 2000;

        long t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(100)) {   // ExecutorService là AutoCloseable từ Java 19
            for (int i = 0; i < TASKS; i++) pool.submit(() -> sleep(200));
        }                                                                  // close() = shutdown + awaitTermination
        System.out.printf("  Pool 100 platform thread : %d ms  (≈ %d/100 × 200ms)%n", (System.nanoTime() - t0) / 1_000_000, TASKS);

        t0 = System.nanoTime();
        try (ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor()) { // mỗi task 1 virtual thread mới
            for (int i = 0; i < TASKS; i++) vt.submit(() -> sleep(200));
        }
        System.out.printf("  Virtual thread per task  : %d ms  (cả 2000 task chờ cùng lúc)%n", (System.nanoTime() - t0) / 1_000_000);
        System.out.println("  → Cùng một kiểu code 'chặn đơn giản', nhưng virtual thread không bị trần ở số thread của OS.");
    }

    // ============================================
    // DEMO 2: API cơ bản và đặc điểm
    // ============================================
    static void demo2Api() throws InterruptedException {
        Thread v = Thread.ofVirtual().name("my-virtual").start(() -> {
            Thread t = Thread.currentThread();
            System.out.println("  isVirtual = " + t.isVirtual() + ", isDaemon = " + t.isDaemon() + "  (luôn là daemon)");
            System.out.println("  toString  = " + t);   // VirtualThread[#id,name]/runnable@ForkJoinPool-1-worker-N ← carrier
        });
        v.join();

        Thread v2 = Thread.startVirtualThread(() -> { });   // cách ngắn nhất
        v2.join();

        Thread p = Thread.ofPlatform().name("my-platform").start(() -> { });
        p.join();
        System.out.println("  Virtual thread: rẻ → TẠO MỚI cho mỗi task, KHÔNG pool nó. Pool để giới hạn tài nguyên khan hiếm,");
        System.out.println("  còn virtual thread không khan hiếm. Muốn giới hạn đồng thời → dùng Semaphore (demo 4).");
    }

    // ============================================
    // DEMO 3: Pinning (cần 1 carrier để thấy rõ)
    // ============================================
    static void demo3Pinning() throws InterruptedException {
        // Hai virtual thread, mỗi thread chặn 500ms bên trong khoá RIÊNG của mình (không tranh nhau khoá).
        // Chỉ có 1 carrier thread (đặt ở đầu main) → nếu thread đầu bị pin thì thread sau không có chỗ chạy.
        Object monitorA = new Object(), monitorB = new Object();
        long t0 = System.nanoTime();
        Thread a = Thread.ofVirtual().start(() -> { synchronized (monitorA) { sleep(500); } });
        Thread b = Thread.ofVirtual().start(() -> { synchronized (monitorB) { sleep(500); } });
        a.join(); b.join();
        long syncMs = (System.nanoTime() - t0) / 1_000_000;

        ReentrantLock lockA = new ReentrantLock(), lockB = new ReentrantLock();
        t0 = System.nanoTime();
        Thread c = Thread.ofVirtual().start(() -> { lockA.lock(); try { sleep(500); } finally { lockA.unlock(); } });
        Thread d = Thread.ofVirtual().start(() -> { lockB.lock(); try { sleep(500); } finally { lockB.unlock(); } });
        c.join(); d.join();
        long lockMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("  sleep trong synchronized : %d ms%n", syncMs);
        System.out.printf("  sleep trong ReentrantLock: %d ms%n", lockMs);
        int jv = Runtime.version().feature();
        if (jv >= 24) {
            System.out.println("  Java " + jv + " ≥ 24: JEP 491 → synchronized không còn pin, hai con số gần bằng nhau.");
        } else {
            System.out.println("  Java " + jv + ": synchronized PIN carrier → ~1000ms (chạy lần lượt); ReentrantLock ~500ms (chạy song song).");
        }
        System.out.println("  Phát hiện pinning: chạy với -Djdk.tracePinnedThreads=full (in stack trace khi bị pin) hoặc JFR event jdk.VirtualThreadPinned.");
    }

    // ============================================
    // DEMO 4: Giới hạn đồng thời bằng Semaphore
    // ============================================
    static void demo4LimitConcurrency() throws InterruptedException {
        Semaphore dbPermits = new Semaphore(10);       // DB chỉ chịu được 10 truy vấn đồng thời
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();

        try (ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1000; i++) {
                vt.submit(() -> {
                    try {
                        dbPermits.acquire();
                        try {
                            max.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            sleep(20);                 // "truy vấn DB"
                            inside.decrementAndGet();
                        } finally { dbPermits.release(); }
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }
        }
        System.out.println("  1000 virtual thread nhưng chỉ tối đa " + max.get() + " vào DB cùng lúc (≤ 10 ✓)");
        System.out.println("  → Virtual thread KHÔNG tự bảo vệ DB/API downstream. Không còn pool làm 'van' → phải tự đặt van (Semaphore).");
    }

    public static void main(String[] args) throws Exception {
        // Ép chỉ có 1 carrier thread để demo 3 thấy rõ pinning. PHẢI đặt trước khi dùng virtual thread lần đầu.
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "1");

        System.out.println("=== VIRTUAL THREADS DEMO (Java " + Runtime.version().feature() + ") ===\n");

        System.out.println("--- Demo 1: Throughput 2000 task chặn ---");
        demo1Throughput();

        System.out.println("\n--- Demo 2: API cơ bản ---");
        demo2Api();

        System.out.println("\n--- Demo 3: Pinning ---");
        demo3Pinning();

        System.out.println("\n--- Demo 4: Giới hạn đồng thời ---");
        demo4LimitConcurrency();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Virtual thread khác platform thread thế nào?");
        System.out.println("A: Platform thread = 1-1 với thread OS (nặng, ~1MB). Virtual thread do JVM lập lịch trên một số ít carrier thread,");
        System.out.println("   stack ở heap, rất rẻ. Khi chặn IO nó unmount khỏi carrier để carrier chạy việc khác.");

        System.out.println("\nQ: Khi nào NÊN dùng? Khi nào KHÔNG?");
        System.out.println("A: NÊN: nhiều tác vụ IO-bound đồng thời (web server thread-per-request, gọi nhiều service).");
        System.out.println("   KHÔNG: CPU-bound (không nhanh hơn), hoặc code đầy synchronized + IO chậm (Java 21-23) / native call (bị pin).");

        System.out.println("\nQ: Pinning là gì? Tránh thế nào?");
        System.out.println("A: Virtual thread không unmount được nên giữ chết carrier thread (trong synchronized ở Java 21-23, hoặc native).");
        System.out.println("   Thay bằng ReentrantLock quanh đoạn chặn; Java 24+ đã sửa cho synchronized. Phát hiện: tracePinnedThreads / JFR.");

        System.out.println("\nQ: Có nên pool virtual thread không?");
        System.out.println("A: Không. Tạo mới mỗi task. Cần giới hạn đồng thời thì dùng Semaphore, không dùng pool thread.");

        System.out.println("\nQ: Virtual thread có thay thế được reactive/CompletableFuture không?");
        System.out.println("A: Với nhiều trường hợp IO-bound, code đồng bộ + virtual thread đơn giản và dễ debug hơn (stack trace thật).");
        System.out.println("   Nhưng CompletableFuture vẫn hữu ích để ghép nối/chạy song song các lời gọi độc lập.");

        System.out.println("\nQ: ThreadLocal với virtual thread?");
        System.out.println("A: Vẫn dùng được, nhưng mỗi virtual thread có bản riêng và có thể có hàng triệu thread → tốn bộ nhớ.");
        System.out.println("   Hướng mới: ScopedValue (preview Java 21-24, chính thức từ Java 25 - JEP 506). Đừng dùng ThreadLocal để cache object nặng.");
    }
}
