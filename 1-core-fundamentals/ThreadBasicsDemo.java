/**
 * THREAD BASICS - join(), sleep(), yield(), DAEMON, UNCAUGHT EXCEPTION
 *
 * Những API nhỏ nhưng hay bị hỏi "vặt" trong interview:
 *
 * | Method        | Ý nghĩa                                         | Giữ lock? | State của thread gọi   |
 * |---------------|--------------------------------------------------|-----------|-------------------------|
 * | sleep(ms)     | Ngủ ít nhất ms, không phụ thuộc thread khác      | GIỮ       | TIMED_WAITING           |
 * | join()        | Chờ thread khác kết thúc                         | (không liên quan) | WAITING         |
 * | join(ms)      | Như trên nhưng có timeout                        |           | TIMED_WAITING           |
 * | yield()       | GỢI Ý scheduler nhường CPU (không đảm bảo)       | GIỮ       | vẫn RUNNABLE            |
 *
 * Interview Question: "Daemon thread là gì? JVM thoát khi nào?"
 * Answer: JVM thoát khi KHÔNG còn thread non-daemon nào chạy. Daemon thread
 *         bị dừng đột ngột (không chạy finally) → không dùng cho việc cần dọn dẹp/ghi dữ liệu.
 */
public class ThreadBasicsDemo {

    static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== THREAD BASICS DEMO ===\n");

        // ============================================
        // DEMO 1: join() - chờ thread khác
        // ============================================
        System.out.println("--- Demo 1: join() ---");
        Thread worker = new Thread(() -> {
            sleepQuietly(1000);
            System.out.println("  [Worker] Xong việc");
        }, "Worker");

        // Thread giám sát: in state của main khi main đang join
        final Thread mainThread = Thread.currentThread();
        Thread watcher = new Thread(() -> {
            sleepQuietly(300);
            System.out.println("  [Watcher] main đang join() không timeout → state = " + mainThread.getState());
        }, "Watcher");

        worker.start();
        watcher.start();
        worker.join(); // main chờ worker xong
        System.out.println("  [Main] Worker xong, isAlive = " + worker.isAlive());
        watcher.join();

        // join có timeout
        Thread slow = new Thread(() -> sleepQuietly(2000), "SlowWorker");
        slow.start();
        slow.join(500);
        System.out.println("  [Main] join(500) hết hạn, SlowWorker còn sống? " + slow.isAlive()
                + "  → join(timeout) KHÔNG báo lỗi khi timeout, phải tự kiểm tra isAlive()");
        slow.join();

        // ============================================
        // DEMO 2: Daemon phải set TRƯỚC khi start
        // ============================================
        System.out.println("\n--- Demo 2: setDaemon() sau start() ---");
        Thread t = new Thread(() -> sleepQuietly(300), "LateDaemon");
        t.start();
        try {
            t.setDaemon(true);
        } catch (IllegalThreadStateException e) {
            System.out.println("  ✓ IllegalThreadStateException: phải setDaemon() TRƯỚC start()");
        }
        t.join();

        // ============================================
        // DEMO 3: Exception trong thread KHÔNG lan lên thread cha
        // ============================================
        System.out.println("\n--- Demo 3: Uncaught exception ---");
        Thread crash = new Thread(() -> {
            throw new IllegalStateException("Lỗi trong thread con!");
        }, "CrashingThread");

        // Không đặt handler: JVM in stack trace ra stderr, main KHÔNG biết gì
        // Đặt handler: bắt được để log/alert
        crash.setUncaughtExceptionHandler((thread, ex) ->
                System.out.println("  [Handler] Thread '" + thread.getName() + "' chết vì: " + ex.getMessage()));
        crash.start();
        crash.join();
        System.out.println("  [Main] vẫn chạy bình thường, try/catch ở main KHÔNG bắt được exception của thread con");

        // ============================================
        // DEMO 4: Thread properties
        // ============================================
        System.out.println("\n--- Demo 4: Name / Priority / Daemon ---");
        Thread info = new Thread(() -> {}, "InfoThread");
        System.out.println("  name     = " + info.getName());
        System.out.println("  priority = " + info.getPriority() + " (1..10, mặc định 5, chỉ là GỢI Ý cho OS)");
        System.out.println("  daemon   = " + info.isDaemon() + " (kế thừa từ thread cha tạo ra nó)");
        System.out.println("  main thread daemon = " + Thread.currentThread().isDaemon());

        // ============================================
        // DEMO 5: yield()
        // ============================================
        System.out.println("\n--- Demo 5: yield() ---");
        System.out.println("  Thread.yield() chỉ là gợi ý, scheduler có thể bỏ qua.");
        System.out.println("  Không dùng để đồng bộ. Trong spin-wait hiện đại dùng Thread.onSpinWait().");
        Thread.yield();

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: sleep() có release lock không?");
        System.out.println("A: KHÔNG. wait() mới release lock.");

        System.out.println("\nQ: join() hoạt động thế nào bên trong?");
        System.out.println("A: Gọi wait() trên chính đối tượng Thread đó trong vòng while(isAlive()).");
        System.out.println("   Khi thread kết thúc, JVM gọi notifyAll() trên nó.");
        System.out.println("   → Vì vậy KHÔNG nên dùng Thread object làm lock cho việc khác.");

        System.out.println("\nQ: Daemon thread dùng khi nào? Ví dụ?");
        System.out.println("A: Việc phụ trợ: GC, monitoring, metrics reporter, cache eviction.");
        System.out.println("   Không dùng cho việc ghi file/DB vì có thể bị dừng giữa chừng.");

        System.out.println("\nQ: Thread.start() vs run()?");
        System.out.println("A: start() tạo thread mới, run() chỉ là gọi method thường.");

        System.out.println("\nQ: Exception trong thread con xử lý thế nào?");
        System.out.println("A: setUncaughtExceptionHandler (từng thread) hoặc Thread.setDefaultUncaughtExceptionHandler.");
        System.out.println("   Với ExecutorService.submit(): exception bị 'giấu' trong Future, gọi get() mới thấy");
        System.out.println("   (ExecutionException). Với execute(): đi vào uncaught handler.");

        // ============================================
        // DEMO 6 (cuối): Daemon thread bị bỏ lại khi main kết thúc
        // ============================================
        System.out.println("\n--- Demo 6: Daemon thread khi JVM thoát ---");
        Thread daemon = new Thread(() -> {
            while (true) {
                sleepQuietly(200);
                System.out.println("  [Daemon] vẫn đang chạy...");
            }
        }, "Daemon");
        daemon.setDaemon(true);
        daemon.start();

        Thread.sleep(700);
        System.out.println("  [Main] kết thúc → JVM thoát dù Daemon còn vòng lặp vô hạn");
    }
}
