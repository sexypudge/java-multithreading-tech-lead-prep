import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EXECUTORSERVICE & THREADPOOLEXECUTOR
 *
 * Vì sao cần thread pool?
 *   - Tạo thread tốn tiền (stack ~1MB, syscall của OS) → tái sử dụng thread.
 *   - Không giới hạn thread → hết RAM / CPU thrash. Pool = cách GIỚI HẠN tài nguyên.
 *
 * 7 THAM SỐ CỦA ThreadPoolExecutor:
 *   corePoolSize     : số thread "thường trực"
 *   maximumPoolSize  : số thread tối đa
 *   keepAliveTime    : thread dư (core..max) rảnh bao lâu thì bị thu hồi
 *   unit             : đơn vị của keepAliveTime
 *   workQueue        : hàng đợi chứa task khi core đã bận
 *   threadFactory    : đặt tên thread (BẮT BUỘC đặt tên khi debug / đọc thread dump)
 *   handler          : rejection policy khi pool + queue đều đầy
 *
 * THỨ TỰ XỬ LÝ KHI submit 1 task (câu phỏng vấn kinh điển):
 *   1. Số thread < core            → TẠO thread mới chạy task
 *   2. Ngược lại, queue còn chỗ    → ĐƯA task vào queue
 *   3. Queue đầy, thread < max     → TẠO thread mới (đến max)
 *   4. Queue đầy, thread = max     → REJECT
 *   ⚠ Hệ quả: với queue KHÔNG GIỚI HẠN thì bước 3 không bao giờ xảy ra → maxPoolSize vô nghĩa.
 *
 * Chạy: java ExecutorServiceDemo.java
 */
public class ExecutorServiceDemo {

    static ThreadFactory namedFactory(String prefix) {
        AtomicInteger n = new AtomicInteger(1);
        return r -> new Thread(r, prefix + "-" + n.getAndIncrement());
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void shutdownAndWait(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }

    // ============================================
    // DEMO 1: core → queue → max → reject
    // ============================================
    static void demo1PoolBehavior() throws InterruptedException {
        // core=2, max=4, queue=2  → sức chứa tối đa = 4 thread + 2 queue = 6 task
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                namedFactory("demo1"),
                new ThreadPoolExecutor.AbortPolicy());

        for (int i = 1; i <= 7; i++) {
            try {
                pool.execute(() -> sleep(800));
                System.out.printf("  submit task-%d → threads=%d, queue=%d%n",
                        i, pool.getPoolSize(), pool.getQueue().size());
            } catch (RejectedExecutionException e) {
                System.out.printf("  submit task-%d → ✗ BỊ TỪ CHỐI (threads=%d, queue=%d đều đầy)%n",
                        i, pool.getPoolSize(), pool.getQueue().size());
            }
        }
        // Kỳ vọng: task1-2 → thread mới (core) | task3-4 → vào queue | task5-6 → thread mới (đến max) | task7 → reject
        shutdownAndWait(pool);
    }

    // ============================================
    // DEMO 2: Rejection policy
    // ============================================
    static void demo2RejectionPolicies() throws InterruptedException {
        /*
         * AbortPolicy (mặc định)  : ném RejectedExecutionException
         * CallerRunsPolicy        : thread GỌI submit tự chạy task → tạo back-pressure tự nhiên
         * DiscardPolicy           : lặng lẽ bỏ task  (nguy hiểm: mất việc mà không biết)
         * DiscardOldestPolicy     : bỏ task cũ nhất trong queue, thử lại task mới
         */
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                namedFactory("caller-runs"),
                new ThreadPoolExecutor.CallerRunsPolicy());

        for (int i = 1; i <= 4; i++) {
            final int id = i;
            pool.execute(() -> {
                System.out.println("  task-" + id + " chạy bởi thread: " + Thread.currentThread().getName());
                sleep(300);
            });
        }
        System.out.println("  → task nào chạy ở thread 'main' nghĩa là pool đầy và main bị 'kéo chậm lại'");
        System.out.println("    (đó là back-pressure: producer tự chậm lại theo khả năng của consumer)");
        shutdownAndWait(pool);
    }

    // ============================================
    // DEMO 3: Cái bẫy của Executors.newXxx()
    // ============================================
    static void demo3ExecutorsPitfalls() throws InterruptedException {
        // newFixedThreadPool(n) = new ThreadPoolExecutor(n, n, 0, ..., new LinkedBlockingQueue<>())
        //   → queue KHÔNG GIỚI HẠN → task dồn vô hạn → OutOfMemoryError khi tải cao
        ThreadPoolExecutor fixed = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        CountDownLatch block = new CountDownLatch(1);
        for (int i = 0; i < 10_000; i++) {
            fixed.execute(() -> {
                try { block.await(); } catch (InterruptedException ignored) { }
            });
        }
        System.out.println("  newFixedThreadPool(1): đã nhận 10.000 task, queue.size = " + fixed.getQueue().size()
                + "  ← không bao giờ reject, chỉ phình RAM");
        block.countDown();
        shutdownAndWait(fixed);

        // newCachedThreadPool() = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60s, new SynchronousQueue<>())
        //   → số thread KHÔNG GIỚI HẠN → 10.000 task chậm = 10.000 thread
        ThreadPoolExecutor cached = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        CountDownLatch block2 = new CountDownLatch(1);
        for (int i = 0; i < 200; i++) {
            cached.execute(() -> {
                try { block2.await(); } catch (InterruptedException ignored) { }
            });
        }
        System.out.println("  newCachedThreadPool(): 200 task chậm → " + cached.getPoolSize()
                + " thread được tạo  ← không giới hạn");
        block2.countDown();
        shutdownAndWait(cached);

        System.out.println("  ⇒ Production: tự tạo ThreadPoolExecutor với queue CÓ GIỚI HẠN + rejection policy rõ ràng.");
    }

    // ============================================
    // DEMO 4: Chọn kích thước pool
    // ============================================
    static void demo4PoolSizing() throws InterruptedException {
        int cpu = Runtime.getRuntime().availableProcessors();
        /*
         * CPU-bound (tính toán thuần)  : threads ≈ N_cpu (hoặc N_cpu + 1)
         *    → nhiều hơn chỉ tốn context switch
         * IO-bound (chờ DB/HTTP/disk)  : threads ≈ N_cpu × (1 + W/C)
         *    W = thời gian chờ, C = thời gian tính toán
         *    vd: 8 core, mỗi request chờ 90ms và tính 10ms → 8 × (1 + 9) = 80 thread
         * Đây chỉ là điểm xuất phát — PHẢI đo (load test) để chỉnh.
         */
        System.out.println("  availableProcessors = " + cpu);
        System.out.println("  IO-bound ví dụ (W=90ms, C=10ms): " + cpu + " × (1 + 90/10) = " + cpu * 10 + " thread");

        // Minh hoạ: 8 task IO-bound (mỗi task chờ 300ms) với pool nhỏ/lớn
        for (int size : new int[]{1, 4, 8}) {
            ExecutorService pool = Executors.newFixedThreadPool(size);
            long start = System.nanoTime();
            for (int i = 0; i < 8; i++) pool.execute(() -> sleep(300));
            shutdownAndWait(pool);
            System.out.printf("  pool=%d → 8 task IO xong sau %d ms%n", size, (System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("  (task chỉ ngủ/chờ nên thêm thread = nhanh hơn; với task CPU-bound thì KHÔNG)");
    }

    // ============================================
    // DEMO 5: execute() vs submit() - exception
    // ============================================
    static void demo5ExceptionHandling() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1, namedFactory("exc"));

        // submit(): exception bị NUỐT vào Future, không in gì cả nếu bạn không gọi get()
        Future<?> f = pool.submit(() -> { throw new IllegalStateException("lỗi trong submit"); });
        sleep(200);
        System.out.println("  submit(): task đã lỗi nhưng không có log nào → lỗi bị nuốt, isDone=" + f.isDone());
        try {
            f.get();
        } catch (ExecutionException e) {
            System.out.println("  Chỉ khi gọi f.get() mới thấy: ExecutionException, cause = " + e.getCause());
        }

        // execute(): exception đi ra UncaughtExceptionHandler (in stack trace), worker thread chết và pool thay thế
        System.out.println("  execute(): (stack trace bên dưới do thread 'exc-x' in ra)");
        pool.execute(() -> { throw new IllegalStateException("lỗi trong execute"); });
        sleep(300);

        System.out.println("  ⇒ Luôn bắt exception TRONG task (try/catch + log) hoặc xử lý Future.get().");
        shutdownAndWait(pool);
    }

    // ============================================
    // DEMO 6: shutdown đúng cách
    // ============================================
    static void demo6Shutdown() throws InterruptedException {
        /*
         * shutdown()    : ngừng nhận task mới, CHẠY NỐT task đang chạy + trong queue
         * shutdownNow() : ngừng nhận, xoá queue (trả về danh sách task chưa chạy),
         *                 interrupt các thread đang chạy
         * awaitTermination : chờ pool dừng hẳn (có timeout)
         * Quên shutdown → thread non-daemon của pool giữ JVM không thoát được!
         */
        ExecutorService pool = Executors.newFixedThreadPool(2, namedFactory("shutdown"));
        for (int i = 0; i < 4; i++) {
            final int id = i;
            pool.execute(() -> {
                try {
                    Thread.sleep(2000);
                    System.out.println("  task-" + id + " xong");
                } catch (InterruptedException e) {
                    System.out.println("  task-" + id + " bị interrupt (do shutdownNow)");
                    Thread.currentThread().interrupt(); // khôi phục cờ interrupt
                }
            });
        }
        sleep(200);

        // Mẫu chuẩn (theo Javadoc của ExecutorService):
        pool.shutdown();                                         // 1. nhẹ nhàng
        if (!pool.awaitTermination(500, TimeUnit.MILLISECONDS)) { // 2. chờ có hạn
            var pending = pool.shutdownNow();                     // 3. mạnh tay
            System.out.println("  shutdownNow(): " + pending.size() + " task chưa chạy bị huỷ khỏi queue");
            pool.awaitTermination(5, TimeUnit.SECONDS);           // 4. chờ lần cuối
        }
    }

    // ============================================
    // DEMO 7: invokeAll / invokeAny
    // ============================================
    static void demo7InvokeAllAny() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3, namedFactory("invoke"));
        List<Callable<String>> calls = List.of(
                () -> { sleep(300); return "replica-A"; },
                () -> { sleep(100); return "replica-B"; },
                () -> { sleep(200); return "replica-C"; });

        // invokeAll: chờ TẤT CẢ xong (hoặc hết timeout → task chưa xong bị cancel), giữ đúng thứ tự danh sách
        List<Future<String>> all = pool.invokeAll(calls, 1, TimeUnit.SECONDS);
        StringBuilder sb = new StringBuilder();
        for (Future<String> f : all) sb.append(f.get()).append(' ');
        System.out.println("  invokeAll → " + sb.toString().trim() + "  (thứ tự theo danh sách, không theo thời gian xong)");

        // invokeAny: trả kết quả của task THÀNH CÔNG ĐẦU TIÊN, cancel các task còn lại (hedged request)
        System.out.println("  invokeAny → " + pool.invokeAny(calls) + "  (nhanh nhất thắng, phần còn lại bị cancel)");
        shutdownAndWait(pool);
    }

    // ============================================
    // DEMO 8: ScheduledExecutorService - fixedRate vs fixedDelay
    // ============================================
    static void demo8Scheduled() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, namedFactory("sched"));
        long t0 = System.nanoTime();
        List<Long> rateStarts = new CopyOnWriteArrayList<>();
        List<Long> delayStarts = new CopyOnWriteArrayList<>();

        // Task mất 300ms, chu kỳ 200ms
        // fixedRate : lịch tính từ lúc BẮT ĐẦU → task chậm hơn chu kỳ thì lần sau chạy NGAY khi lần trước xong
        //             (KHÔNG chạy chồng lên nhau - cùng 1 task không bao giờ chạy song song với chính nó)
        // fixedDelay: chờ 200ms SAU KHI lần trước KẾT THÚC
        ScheduledFuture<?> rate = scheduler.scheduleAtFixedRate(() -> {
            rateStarts.add((System.nanoTime() - t0) / 1_000_000);
            sleep(300);
        }, 0, 200, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> delay = scheduler.scheduleWithFixedDelay(() -> {
            delayStarts.add((System.nanoTime() - t0) / 1_000_000);
            sleep(300);
        }, 0, 200, TimeUnit.MILLISECONDS);

        Thread.sleep(1700);
        rate.cancel(false);
        delay.cancel(false);
        System.out.println("  fixedRate  bắt đầu lúc (ms): " + rateStarts + "  ← cách nhau ~300 (bị task chậm kéo theo)");
        System.out.println("  fixedDelay bắt đầu lúc (ms): " + delayStarts + "  ← cách nhau ~500 (300 chạy + 200 nghỉ)");

        // BẪY: exception trong task định kỳ → lịch BỊ HUỶ ÂM THẦM, không log gì
        AtomicInteger runs = new AtomicInteger();
        ScheduledFuture<?> fragile = scheduler.scheduleAtFixedRate(() -> {
            if (runs.incrementAndGet() == 2) throw new IllegalStateException("lỗi lần chạy thứ 2");
        }, 0, 50, TimeUnit.MILLISECONDS);
        Thread.sleep(400);
        System.out.println("  Task ném exception ở lần 2 → chỉ chạy " + runs.get() + " lần rồi DỪNG HẲN, isDone="
                + fragile.isDone() + "  ✗ không có log nào!");
        System.out.println("  ⇒ Luôn bọc thân task định kỳ trong try/catch(Throwable) + log.");
        scheduler.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== EXECUTORSERVICE DEMO ===\n");

        System.out.println("--- Demo 1: core → queue → max → reject ---");
        demo1PoolBehavior();

        System.out.println("\n--- Demo 2: Rejection policy (CallerRuns) ---");
        demo2RejectionPolicies();

        System.out.println("\n--- Demo 3: Bẫy của Executors.newFixedThreadPool / newCachedThreadPool ---");
        demo3ExecutorsPitfalls();

        System.out.println("\n--- Demo 4: Chọn kích thước pool ---");
        demo4PoolSizing();

        System.out.println("\n--- Demo 5: execute() vs submit() - exception bị nuốt ---");
        demo5ExceptionHandling();

        System.out.println("\n--- Demo 6: shutdown đúng cách ---");
        demo6Shutdown();

        System.out.println("\n--- Demo 7: invokeAll / invokeAny ---");
        demo7InvokeAllAny();

        System.out.println("\n--- Demo 8: ScheduledExecutorService ---");
        demo8Scheduled();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Thứ tự ThreadPoolExecutor xử lý task mới?");
        System.out.println("A: core → queue → (tạo thêm đến max) → reject. Queue vô hạn thì max không bao giờ được dùng.");

        System.out.println("\nQ: Vì sao không nên dùng Executors.newFixedThreadPool/newCachedThreadPool ở production?");
        System.out.println("A: Fixed dùng queue vô hạn (OOM). Cached cho phép thread vô hạn (OOM/CPU thrash).");
        System.out.println("   Tự tạo ThreadPoolExecutor với queue có giới hạn + rejection policy + tên thread.");

        System.out.println("\nQ: Chọn kích thước pool thế nào?");
        System.out.println("A: CPU-bound ≈ số core. IO-bound ≈ core × (1 + wait/compute). Sau đó đo và chỉnh.");
        System.out.println("   Tách pool riêng cho các loại việc khác nhau (bulkhead) để việc chậm không kéo sập việc khác.");

        System.out.println("\nQ: submit() và execute() khác gì?");
        System.out.println("A: submit trả Future và nuốt exception vào Future; execute ném exception ra UncaughtExceptionHandler.");

        System.out.println("\nQ: shutdown() vs shutdownNow()?");
        System.out.println("A: shutdown chạy nốt việc còn lại; shutdownNow xoá queue và interrupt thread đang chạy.");

        System.out.println("\nQ: scheduleAtFixedRate vs scheduleWithFixedDelay?");
        System.out.println("A: fixedRate tính chu kỳ từ lúc bắt đầu (task chậm → lần sau chạy ngay, không chồng lấn);");
        System.out.println("   fixedDelay tính từ lúc kết thúc. Exception trong task → lịch bị huỷ âm thầm.");

        System.out.println("\nQ: Tăng pool size có luôn làm hệ thống nhanh hơn?");
        System.out.println("A: Không. Nếu nút thắt là DB (connection pool 10) thì 200 worker chỉ xếp hàng chờ connection,");
        System.out.println("   tốn RAM, tăng context switch, tăng latency đuôi (p99). Pool phải khớp với tài nguyên downstream.");
    }
}
