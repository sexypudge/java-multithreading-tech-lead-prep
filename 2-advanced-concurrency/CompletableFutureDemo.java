import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * CALLABLE, FUTURE, COMPLETABLEFUTURE
 *
 *   Runnable : không trả giá trị, không ném checked exception
 *   Callable : có trả giá trị (V call() throws Exception)
 *   Future   : "phiếu nhận hàng" — get() CHẶN thread gọi cho đến khi có kết quả
 *   CompletableFuture : Future + pipeline bất đồng bộ (thenApply, thenCompose, allOf, ...)
 *
 * BỐN CÁI BẪY (đề bài trong lộ trình):
 *   1. Pool mặc định  : supplyAsync() không truyền executor → ForkJoinPool.commonPool() (DÙNG CHUNG cả JVM)
 *   2. Nuốt exception : lỗi nằm trong CompletableFuture, không ai get/join/handle → mất dấu hoàn toàn
 *   3. Mất context    : ThreadLocal (MDC, SecurityContext, transaction) KHÔNG đi theo sang thread khác
 *   4. Chặn thread    : join()/get() trong task của chính pool đó → pool starvation / deadlock
 *
 * Chạy: java CompletableFutureDemo.java
 */
public class CompletableFutureDemo {

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static ThreadFactory namedFactory(String prefix) {
        AtomicInteger n = new AtomicInteger(1);
        return r -> new Thread(r, prefix + "-" + n.getAndIncrement());
    }

    // ============================================
    // DEMO 1: Callable + Future (cách cũ)
    // ============================================
    static void demo1CallableFuture() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2, namedFactory("fut"));

        Callable<Integer> task = () -> { sleep(300); return 42; };
        Future<Integer> f = pool.submit(task);

        System.out.println("  Main vẫn làm việc khác trong khi task chạy... isDone=" + f.isDone());
        System.out.println("  f.get() → " + f.get() + "   ← CHẶN main cho đến khi xong");

        // Exception được bọc trong ExecutionException
        Future<Integer> bad = pool.submit(() -> { throw new IllegalArgumentException("boom"); });
        try {
            bad.get();
        } catch (ExecutionException e) {
            System.out.println("  Lỗi bị bọc: " + e.getClass().getSimpleName() + " ← cause: " + e.getCause());
        }

        // get() nên luôn có timeout
        Future<Integer> slow = pool.submit(() -> { sleep(2000); return 1; });
        try {
            slow.get(200, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("  get(timeout) → TimeoutException; cancel(true) = " + slow.cancel(true));
        }
        System.out.println("  Hạn chế của Future: không callback, không ghép nối, muốn chờ nhiều Future phải get() lần lượt.");
        pool.shutdownNow();
    }

    // ============================================
    // DEMO 2: Bẫy #1 - pool mặc định (commonPool)
    // ============================================
    static void demo2DefaultPool() {
        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName());
        System.out.println("  supplyAsync() mặc định chạy trên: " + cf.join());
        System.out.println("  (commonPool parallelism = " + ForkJoinPool.getCommonPoolParallelism()
                + "; nếu ≤ 1 — máy 1-2 core — CF tự tạo thread mới 'Thread-N' thay vì dùng commonPool)");
        System.out.println("  → ForkJoinPool.commonPool: số thread ≈ (số core - 1), là DAEMON thread, dùng chung với parallel stream.");
        System.out.println("  → Task IO chậm (HTTP/DB) chiếm hết commonPool sẽ làm parallelStream() và mọi CF khác bị đứng.");
        System.out.println("  → Main thoát sớm thì task đang chạy bị bỏ (daemon).");

        ExecutorService ioPool = Executors.newFixedThreadPool(4, namedFactory("io-pool"));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), ioPool);
        System.out.println("  supplyAsync(task, ioPool) chạy trên: " + cf2.join() + "   ← luôn truyền executor riêng cho việc IO");
        ioPool.shutdown();
    }

    // ============================================
    // DEMO 3: Ghép nối - thenApply / thenCompose / thenCombine
    // ============================================
    static CompletableFuture<String> fetchUser(ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> { sleep(400); return "user-1"; }, pool);
    }
    static CompletableFuture<List<String>> fetchOrders(String user, ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> { sleep(400); return List.of(user + ":order-A", user + ":order-B"); }, pool);
    }
    static CompletableFuture<Integer> fetchLoyaltyPoints(ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> { sleep(400); return 120; }, pool);
    }

    static void demo3Chaining() {
        ExecutorService pool = Executors.newFixedThreadPool(4, namedFactory("chain"));
        long t0 = System.nanoTime();

        /*
         * thenApply   : biến đổi kết quả (như map)           T → U
         * thenCompose : nối tiếp 1 bước BẤT ĐỒNG BỘ khác (như flatMap)   T → CompletableFuture<U>
         * thenCombine : ghép 2 CF CHẠY SONG SONG
         * thenAccept  : tiêu thụ kết quả, không trả gì
         */
        CompletableFuture<String> result = fetchUser(pool)
                .thenCompose(user -> fetchOrders(user, pool))          // tuần tự: cần user trước → 400ms
                .thenCombine(fetchLoyaltyPoints(pool),                 // loyalty chạy song song từ đầu
                        (orders, points) -> orders.size() + " đơn hàng, " + points + " điểm");

        System.out.println("  Kết quả: " + result.join());
        System.out.printf("  Thời gian: %d ms (tuần tự 3 bước sẽ ~1200 ms; ở đây ~800 vì loyalty chạy song song)%n",
                (System.nanoTime() - t0) / 1_000_000);
        pool.shutdown();
    }

    // ============================================
    // DEMO 4: Chờ nhiều CF - allOf
    // ============================================
    static void demo4AllOf() {
        ExecutorService pool = Executors.newFixedThreadPool(5, namedFactory("all"));
        long t0 = System.nanoTime();

        List<CompletableFuture<Integer>> cfs = List.of(1, 2, 3, 4, 5).stream()
                .map(i -> CompletableFuture.supplyAsync(() -> { sleep(400); return i * i; }, pool))
                .collect(Collectors.toList());

        // allOf trả CompletableFuture<Void>: phải tự lấy từng kết quả bằng join()
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])).join();
        List<Integer> results = cfs.stream().map(CompletableFuture::join).collect(Collectors.toList());

        System.out.printf("  5 task × 400ms chạy song song → %s trong %d ms%n", results, (System.nanoTime() - t0) / 1_000_000);

        // anyOf: hoàn thành khi task ĐẦU TIÊN xong (thành công HOẶC lỗi) - kiểu trả về là Object
        CompletableFuture<Object> fastest = CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> { sleep(300); return "mirror-EU"; }, pool),
                CompletableFuture.supplyAsync(() -> { sleep(100); return "mirror-US"; }, pool));
        System.out.println("  anyOf → " + fastest.join() + "  (hedged request: gửi nhiều nơi, lấy kết quả về trước)");
        System.out.println("  ⚠ allOf: 1 CF lỗi → allOf lỗi, nhưng các CF khác VẪN chạy tiếp (không tự cancel).");
        pool.shutdown();
    }

    // ============================================
    // DEMO 5: Bẫy #2 - exception
    // ============================================
    static void demo5Exceptions() {
        ExecutorService pool = Executors.newFixedThreadPool(2, namedFactory("exc"));

        // (a) Không ai quan tâm kết quả → lỗi biến mất, không có log
        CompletableFuture.runAsync(() -> { throw new IllegalStateException("fire-and-forget bị lỗi"); }, pool);
        sleep(200);
        System.out.println("  (a) fire-and-forget: task đã lỗi nhưng KHÔNG có log nào → mất dấu");

        // (b) join() ném CompletionException (unchecked), get() ném ExecutionException (checked)
        CompletableFuture<Integer> bad = CompletableFuture.supplyAsync(() -> { throw new IllegalStateException("boom"); }, pool);
        try {
            bad.join();
        } catch (CompletionException e) {
            System.out.println("  (b) join() → " + e.getClass().getSimpleName() + ", cause = " + e.getCause());
        }

        // (c) exceptionally: nhận Throwable (bị bọc CompletionException trong pipeline), trả giá trị thay thế
        int v1 = CompletableFuture.<Integer>supplyAsync(() -> { throw new IllegalStateException("x"); }, pool)
                .exceptionally(ex -> { System.out.println("  (c) exceptionally nhận: " + ex); return -1; })
                .join();
        System.out.println("      giá trị fallback = " + v1);

        // (d) handle: nhận cả (kết quả, lỗi) - luôn chạy; whenComplete: quan sát, không đổi kết quả
        String v2 = CompletableFuture.supplyAsync(() -> 10, pool)
                .handle((val, ex) -> ex == null ? "ok:" + val : "fail")
                .join();
        System.out.println("  (d) handle → " + v2);

        // (e) Quy tắc: kết thúc pipeline bằng handle/exceptionally/whenComplete để LOG lỗi
        CompletableFuture.runAsync(() -> { throw new IllegalStateException("đã được log"); }, pool)
                .whenComplete((r, ex) -> { if (ex != null) System.out.println("  (e) LOG lỗi: " + ex.getCause()); })
                .exceptionally(ex -> null)
                .join();
        pool.shutdown();
    }

    // ============================================
    // DEMO 6: Timeout (Java 9+)
    // ============================================
    static void demo6Timeout() {
        ExecutorService pool = Executors.newFixedThreadPool(2, namedFactory("to"));

        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> { sleep(2000); return "xong"; }, pool);

        // orTimeout: quá hạn → hoàn thành với TimeoutException
        // completeOnTimeout: quá hạn → hoàn thành với giá trị mặc định
        String r = slow.completeOnTimeout("giá trị mặc định", 300, TimeUnit.MILLISECONDS).join();
        System.out.println("  completeOnTimeout → " + r);

        try {
            CompletableFuture.supplyAsync(() -> { sleep(2000); return "xong"; }, pool)
                    .orTimeout(300, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            System.out.println("  orTimeout → " + e.getCause().getClass().getSimpleName());
        }
        System.out.println("  ⚠ Timeout KHÔNG huỷ task đang chạy ở pool (nó vẫn chạy tiếp, chỉ là bạn không chờ nữa).");
        pool.shutdownNow();
    }

    // ============================================
    // DEMO 7: Bẫy #3 - mất context (ThreadLocal)
    // ============================================
    static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();   // giống MDC / SecurityContextHolder

    static void demo7ContextLoss() {
        ExecutorService pool = Executors.newFixedThreadPool(1, namedFactory("ctx"));
        REQUEST_ID.set("req-123");   // thread main đang xử lý request req-123

        String lost = CompletableFuture.supplyAsync(() -> "requestId = " + REQUEST_ID.get(), pool).join();
        System.out.println("  Không xử lý : " + lost + "   ← MẤT (ThreadLocal thuộc về thread main)");

        // Cách sửa: CHỤP giá trị ở thread gọi, rồi gắn lại ở thread chạy
        String captured = REQUEST_ID.get();
        String ok = CompletableFuture.supplyAsync(() -> {
            REQUEST_ID.set(captured);
            try {
                return "requestId = " + REQUEST_ID.get();
            } finally {
                REQUEST_ID.remove();     // BẮT BUỘC dọn: thread pool tái sử dụng thread → rò rỉ sang task sau
            }
        }, pool).join();
        System.out.println("  Chụp & gắn lại: " + ok);

        System.out.println("  → Làm thủ công dễ quên; thực tế dùng wrapper/decorator (xem SpringAsyncNotes.md: TaskDecorator).");
        REQUEST_ID.remove();
        pool.shutdown();
    }

    // ============================================
    // DEMO 8: Bẫy #4 - chặn thread trong chính pool đó
    // ============================================
    static void demo8Starvation() {
        // Thread DAEMON có chủ đích: worker sẽ kẹt trong join(), mà join() KHÔNG phản hồi interrupt
        // (get() thì có) → shutdownNow() không giải cứu được. Nếu là thread thường, JVM sẽ không bao giờ thoát.
        ExecutorService pool = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "starve-1");
            t.setDaemon(true);
            return t;
        });
        CompletableFuture<String> outer = CompletableFuture.supplyAsync(() -> {
            // Task ngoài chiếm thread duy nhất của pool, rồi chờ task trong...
            CompletableFuture<String> inner = CompletableFuture.supplyAsync(() -> "inner", pool);
            return inner.join();   // ...nhưng task trong cần đúng thread đó để chạy → CHỜ NHAU MÃI MÃI
        }, pool);

        try {
            outer.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("  ✗ Treo! Pool 1 thread: task ngoài chờ task trong, task trong chờ thread rảnh.");
            System.out.println("    (Đây là 'thread pool starvation deadlock' — thread dump sẽ thấy thread ở trạng thái WAITING tại join())");
        } catch (Exception e) {
            System.out.println("  lỗi lạ: " + e);
        }
        System.out.println("  Cách tránh: dùng thenCompose thay vì join() lồng nhau, hoặc tách pool cho 2 tầng việc.");
        System.out.println("  ⚠ join() không phản hồi interrupt (get() thì có) → thread kẹt ở join() không thể bị shutdownNow() cứu,");
        System.out.println("    ở production nó kẹt vĩnh viễn cho đến khi restart. Luôn ưu tiên get(timeout) / orTimeout cho đường chờ.");
        pool.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== COMPLETABLEFUTURE DEMO ===\n");

        System.out.println("--- Demo 1: Callable + Future ---");
        demo1CallableFuture();

        System.out.println("\n--- Demo 2: Bẫy #1 - pool mặc định ---");
        demo2DefaultPool();

        System.out.println("\n--- Demo 3: thenCompose / thenCombine ---");
        demo3Chaining();

        System.out.println("\n--- Demo 4: allOf ---");
        demo4AllOf();

        System.out.println("\n--- Demo 5: Bẫy #2 - exception ---");
        demo5Exceptions();

        System.out.println("\n--- Demo 6: Timeout ---");
        demo6Timeout();

        System.out.println("\n--- Demo 7: Bẫy #3 - mất context ---");
        demo7ContextLoss();

        System.out.println("\n--- Demo 8: Bẫy #4 - starvation ---");
        demo8Starvation();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Runnable vs Callable? Future có hạn chế gì?");
        System.out.println("A: Callable trả giá trị và được ném checked exception. Future.get() chặn, không callback,");
        System.out.println("   không ghép nối được → CompletableFuture.");

        System.out.println("\nQ: thenApply vs thenCompose?");
        System.out.println("A: thenApply = map (hàm trả giá trị thường). thenCompose = flatMap (hàm trả CompletableFuture),");
        System.out.println("   tránh ra CompletableFuture<CompletableFuture<T>>.");

        System.out.println("\nQ: supplyAsync() không truyền executor thì chạy ở đâu? Vấn đề gì?");
        System.out.println("A: ForkJoinPool.commonPool (dùng chung cả JVM, chỉ ~core-1 thread). Task IO chậm làm nghẽn cả app.");
        System.out.println("   Luôn truyền executor riêng cho IO.");

        System.out.println("\nQ: Exception trong CompletableFuture đi đâu?");
        System.out.println("A: Nằm trong CF, bọc CompletionException. Không join/get/handle thì mất hẳn.");
        System.out.println("   Kết thúc chuỗi bằng handle/exceptionally/whenComplete để log.");

        System.out.println("\nQ: Vì sao MDC/SecurityContext/transaction mất khi chạy async?");
        System.out.println("A: Chúng dựa trên ThreadLocal, gắn với thread gọi. Thread khác không có → phải chụp và truyền sang");
        System.out.println("   (TaskDecorator/wrapper), và nhớ remove() sau khi xong.");
    }
}
