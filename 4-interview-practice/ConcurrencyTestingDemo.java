import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BÀI TẬP: (1) Viết counter thread-safe theo 4 cách. (2) Viết một TEST lặp lại được để chứng minh bản unsafe sai.
 *
 * "Làm sao bạn test code concurrent?" là câu hỏi Tech Lead rất hay gặp. Công thức:
 *   1. START GATE: CountDownLatch(1) để mọi thread bắt đầu CÙNG LÚC (tối đa hoá xen kẽ).
 *      Nếu không, thread đầu có thể chạy xong trước khi thread cuối kịp start → test "luôn xanh".
 *   2. Nhiều thread hơn số core, nhiều vòng lặp, và LẶP LẠI cả test nhiều lần.
 *   3. Assert BẤT BIẾN (tổng = threads × ops), không assert thứ tự in ra.
 *   4. Mọi chỗ chờ đều có TIMEOUT (test treo còn tệ hơn test đỏ). Không dùng sleep() để "đợi cho chắc".
 *   5. Propagate exception từ worker về thread test (Future.get()) - assert trong thread khác KHÔNG làm test fail.
 *   6. Biết giới hạn: test xanh ≠ code đúng. Bug JMM (visibility/reordering) cần jcstress; đo hiệu năng cần JMH.
 *
 * Chạy: java ConcurrencyTestingDemo.java
 */
public class ConcurrencyTestingDemo {

    interface Counter {
        void increment();
        long get();
    }

    static class UnsafeCounter implements Counter {
        private long n;
        public void increment() { n++; }
        public long get() { return n; }
    }

    static class SynchronizedCounter implements Counter {
        private long n;
        public synchronized void increment() { n++; }
        public synchronized long get() { return n; }
    }

    static class LockCounter implements Counter {
        private final ReentrantLock lock = new ReentrantLock();
        private long n;
        public void increment() { lock.lock(); try { n++; } finally { lock.unlock(); } }
        public long get() { lock.lock(); try { return n; } finally { lock.unlock(); } }
    }

    static class AtomicCounter implements Counter {
        private final AtomicInteger n = new AtomicInteger();
        public void increment() { n.incrementAndGet(); }
        public long get() { return n.get(); }
    }

    static class LongAdderCounter implements Counter {
        private final LongAdder n = new LongAdder();
        public void increment() { n.increment(); }
        public long get() { return n.sum(); }       // chính xác SAU KHI mọi writer đã xong (sau join/get)
    }

    /**
     * Test harness tái sử dụng được: chạy `threads` worker cùng lúc, mỗi worker gọi action `ops` lần.
     * Trả về thời gian (ms). Ném AssertionError nếu worker lỗi hoặc treo.
     */
    static long runConcurrently(int threads, int ops, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    ready.countDown();
                    startGate.await();                               // mọi worker đứng ở vạch xuất phát
                    for (int i = 0; i < ops; i++) action.run();
                    return null;
                }));
            }
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker không sẵn sàng");
            long start = System.nanoTime();
            startGate.countDown();                                   // XUẤT PHÁT
            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS); // propagate exception + timeout chống treo
            return (System.nanoTime() - start) / 1_000_000;
        } finally {
            pool.shutdownNow();
        }
    }

    static void testCounter(String name, java.util.function.Supplier<Counter> factory, int repetitions) throws Exception {
        final int THREADS = Math.max(4, Runtime.getRuntime().availableProcessors());
        final int OPS = 50_000;
        int failures = 0;
        long totalMs = 0;
        for (int r = 0; r < repetitions; r++) {
            Counter c = factory.get();
            totalMs += runConcurrently(THREADS, OPS, c::increment);
            if (c.get() != (long) THREADS * OPS) failures++;
        }
        System.out.printf("  %-12s %d/%d lần chạy SAI, trung bình %d ms  %s%n", name, failures, repetitions,
                totalMs / repetitions, failures == 0 ? "✓" : "✗");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== CONCURRENCY TESTING ===");
        System.out.println("threads = " + Math.max(4, Runtime.getRuntime().availableProcessors()) + ", ops/thread = 50,000\n");

        testCounter("Unsafe", UnsafeCounter::new, 10);
        testCounter("synchronized", SynchronizedCounter::new, 10);
        testCounter("ReentrantLock", LockCounter::new, 10);
        testCounter("AtomicInteger", AtomicCounter::new, 10);
        testCounter("LongAdder", LongAdderCounter::new, 10);

        System.out.println("\n  (Thời gian ở đây KHÔNG phải benchmark nghiêm túc: không warm-up, không fork JVM.");
        System.out.println("   Muốn so sánh hiệu năng → JMH. Xu hướng thường thấy khi tranh chấp cao: LongAdder < Atomic < lock.)");

        System.out.println("\nCâu hỏi follow-up:");
        System.out.println("  - Test bất đồng bộ (CompletableFuture/@Async) đợi kết quả thế nào? → join()/get(timeout), hoặc Awaitility");
        System.out.println("    await().atMost(2, SECONDS).until(...) thay vì Thread.sleep().");
        System.out.println("  - Test bug visibility (thiếu volatile)? → unit test gần như không bắt được; dùng jcstress.");
        System.out.println("  - Đo throughput của 4 counter? → JMH với @Threads(N), @Fork, @Warmup.");
    }
}
