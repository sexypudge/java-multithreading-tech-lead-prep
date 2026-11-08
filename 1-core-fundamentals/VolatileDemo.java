/**
 * VOLATILE KEYWORD - VISIBILITY + ORDERING (KHÔNG có atomicity)
 *
 * Problem: JIT/CPU được phép giữ giá trị trong thanh ghi, hoist việc đọc ra khỏi vòng lặp, reorder lệnh
 *          → thread này có thể KHÔNG BAO GIỜ thấy thay đổi của thread khác.
 * Solution: volatile → mỗi lần đọc/ghi là một "synchronization action" của JMM:
 *          ghi volatile V happens-before mọi lần đọc V sau đó (và mọi ghi TRƯỚC lần ghi đó cũng được thấy).
 *          ("đọc/ghi thẳng từ main memory" chỉ là cách nói đơn giản hoá - xem JavaMemoryModelDemo.)
 *
 * volatile ĐẢM BẢO:       ✓ Visibility   ✓ Ordering (cấm reorder qua volatile read/write)
 *                         ✓ Đọc/ghi long/double là atomic (không bị "xé" làm hai nửa 32-bit)
 * volatile KHÔNG ĐẢM BẢO: ✗ Atomicity cho thao tác ghép - count++ vẫn sai
 *
 * Interview Question: "volatile vs synchronized?"
 * Answer: volatile - visibility + ordering, không lock, không mutual exclusion.
 *         synchronized - mutual exclusion + visibility → bảo vệ được thao tác ghép.
 *
 * Tài liệu kèm theo: 06-Volatile.md
 */
public class VolatileDemo {

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================
    // PROBLEM: WITHOUT VOLATILE
    // ============================================
    static class WithoutVolatile {
        private boolean running = true; // NO volatile

        void run() {
            Thread worker = new Thread(() -> {
                long count = 0;
                // JIT có thể biến thành: if (running) while (true) count++;  → vòng lặp vô hạn
                while (running) {
                    count++;
                }
                System.out.println("  [Worker] dừng sau " + count + " vòng");
            }, "Worker-NoVolatile");
            worker.setDaemon(true);   // nếu kẹt vĩnh viễn, JVM vẫn thoát được khi main kết thúc
            worker.start();

            sleep(1000);              // cho JIT kịp biên dịch vòng lặp nóng
            running = false;          // có thể KHÔNG BAO GIỜ được worker nhìn thấy
            joinAndReport(worker);
        }
    }

    // ============================================
    // SOLUTION: WITH VOLATILE
    // ============================================
    static class WithVolatile {
        private volatile boolean running = true;

        void run() {
            Thread worker = new Thread(() -> {
                long count = 0;
                while (running) {     // volatile read mỗi vòng → JIT không được hoist ra ngoài
                    count++;
                }
                System.out.println("  [Worker] dừng sau " + count + " vòng");
            }, "Worker-Volatile");
            worker.setDaemon(true);
            worker.start();

            sleep(1000);
            running = false;          // volatile write → happens-before lần đọc tiếp theo của worker
            joinAndReport(worker);
        }
    }

    static void joinAndReport(Thread worker) {
        try {
            worker.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(worker.isAlive()
                ? "  ✗ Worker vẫn chạy 2s sau khi running=false → visibility bug"
                : "  ✓ Worker đã thấy running=false và dừng");
    }

    // ============================================
    // VOLATILE KHÔNG ĐẢM BẢO ATOMICITY
    // ============================================
    static class VolatileNotAtomic {
        private volatile int count = 0;

        void increment() {
            count++;   // = int tmp = count; tmp = tmp + 1; count = tmp;  → 2 thread đọc cùng giá trị → mất 1 lần cộng
        }

        int get() { return count; }
    }

    // ============================================
    // KHI NÀO DÙNG VOLATILE
    // ============================================
    /**
     * Dùng được khi: (1) giá trị ghi KHÔNG phụ thuộc giá trị hiện tại (hoặc chỉ 1 thread ghi),
     *               (2) biến không tham gia bất biến (invariant) cùng biến khác.
     */
    static class VolatileUseCases {
        private volatile boolean shutdownRequested = false;   // ✓ cờ trạng thái
        private volatile Config config = new Config("a", 1);  // ✓ publish immutable snapshot (thay cả object)
        private volatile int counter = 0;                     // ✗ counter++ cần AtomicInteger / synchronized

        record Config(String host, int port) { }

        void shutdown() { shutdownRequested = true; }
        boolean isShutdownRequested() { return shutdownRequested; }
        void reload(Config newConfig) { config = newConfig; }  // reader luôn thấy cặp host/port nhất quán
        Config currentConfig() { return config; }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== VOLATILE KEYWORD DEMO ===\n");

        System.out.println("--- Demo 1: WITHOUT volatile ---");
        new WithoutVolatile().run();
        System.out.println("  (Nếu worker dừng được: lần này JIT chưa tối ưu. Code vẫn SAI theo JMM.)");

        System.out.println("\n--- Demo 2: WITH volatile ---");
        new WithVolatile().run();

        System.out.println("\n--- Demo 3: volatile KHÔNG atomic ---");
        VolatileNotAtomic demo3 = new VolatileNotAtomic();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100_000; j++) demo3.increment();
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        System.out.printf("  expected=%,d actual=%,d %s%n", 800_000, demo3.get(),
                demo3.get() == 800_000 ? "(may mắn)" : "✗ lost update dù là volatile");

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: volatile vs synchronized?");
        System.out.println("A: volatile - visibility + ordering, không lock, không mutual exclusion.");
        System.out.println("   synchronized - mutual exclusion + visibility → dùng cho thao tác ghép / bất biến nhiều biến.");

        System.out.println("\nQ: Khi nào dùng volatile?");
        System.out.println("A: ✓ Cờ trạng thái (shutdown flag)   ✓ Một writer, nhiều reader");
        System.out.println("   ✓ Publish immutable snapshot      ✓ instance trong double-checked locking");
        System.out.println("   ✗ count++, check-then-act, bất biến giữa nhiều biến");

        System.out.println("\nQ: volatile có atomic không?");
        System.out.println("A: Từng lần đọc/ghi đơn là atomic (kể cả long/double), nhưng đọc-sửa-ghi thì KHÔNG.");

        System.out.println("\nQ: Performance của volatile?");
        System.out.println("A: Đọc volatile gần như rẻ như đọc thường trên x86; ghi volatile tốn memory barrier.");
        System.out.println("   Đắt hơn biến thường, thường rẻ hơn lock có tranh chấp.");

        System.out.println("\nQ: Happens-before của volatile?");
        System.out.println("A: Ghi volatile V happens-before mọi lần đọc V sau đó.");
        System.out.println("   (Chi tiết: piggybacking, safe publication, các quy tắc HB khác → JavaMemoryModelDemo.)");

        System.out.println("\n=== DECISION TREE ===");
        System.out.println("Cần thread-safety cho...");
        System.out.println("├─ Cờ / giá trị ghi độc lập          → volatile");
        System.out.println("├─ Một biến số đếm / CAS đơn giản      → AtomicInteger / LongAdder");
        System.out.println("├─ Map/Queue dùng chung               → ConcurrentHashMap / BlockingQueue");
        System.out.println("├─ Bất biến giữa nhiều biến           → synchronized / Lock");
        System.out.println("└─ Tốt nhất: không chia sẻ state      → immutable / thread confinement");
    }
}
