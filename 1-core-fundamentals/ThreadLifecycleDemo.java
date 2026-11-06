import java.util.concurrent.CountDownLatch;

/**
 * THREAD LIFECYCLE & STATES
 *
 * Thread.State chỉ có 6 giá trị (KHÔNG có "RUNNING"):
 *   1. NEW           - đã new Thread() nhưng chưa start()
 *   2. RUNNABLE      - đang chạy HOẶC sẵn sàng chạy HOẶC đang chờ I/O ở mức OS (socket read...)
 *   3. BLOCKED       - chờ vào khối synchronized (monitor đang bị thread khác giữ)
 *   4. WAITING       - chờ vô hạn: Object.wait(), Thread.join(), LockSupport.park() (ReentrantLock, Future.get()...)
 *   5. TIMED_WAITING - chờ có hạn: sleep(ms), wait(ms), join(ms), parkNanos
 *   6. TERMINATED    - run() đã kết thúc (bình thường hoặc do exception)
 *
 * Interview Question: "Thread ở state nào khi gọi sleep()? Khi chờ ReentrantLock.lock()?"
 * Answer: sleep() → TIMED_WAITING. ReentrantLock.lock() → WAITING (dùng park), KHÔNG phải BLOCKED.
 *         BLOCKED chỉ dành cho monitor (synchronized).
 *
 * Demo này dùng CountDownLatch để quan sát state một cách tất định, không phụ thuộc vào may rủi của sleep().
 */
public class ThreadLifecycleDemo {

    private static void printState(Thread thread, String expected) {
        Thread.State actual = thread.getState();
        System.out.printf("  %-14s → state = %-13s %s%n", thread.getName(), actual,
                actual.name().equals(expected) ? "✓" : "(mong đợi " + expected + ")");
    }

    /** Chờ tới khi thread đạt state mong muốn (tối đa 2s) - tránh race giữa "start()" và "đọc state". */
    private static void awaitState(Thread t, Thread.State state) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (t.getState() != state && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== THREAD LIFECYCLE DEMO ===\n");

        // ============================================
        // NEW → RUNNABLE → TIMED_WAITING → TERMINATED
        // ============================================
        System.out.println("--- NEW / RUNNABLE / TIMED_WAITING / TERMINATED ---");
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Sleeper");
        printState(sleeper, "NEW");

        sleeper.start();
        // Ngay sau start() thường là RUNNABLE, nhưng thread có thể đã kịp vào sleep → không khẳng định được.
        System.out.println("  (ngay sau start(): " + sleeper.getState() + " - có thể RUNNABLE hoặc đã TIMED_WAITING)");

        awaitState(sleeper, Thread.State.TIMED_WAITING);
        printState(sleeper, "TIMED_WAITING");

        sleeper.join();
        printState(sleeper, "TERMINATED");

        // ============================================
        // BLOCKED: chờ monitor (synchronized)
        // ============================================
        System.out.println("\n--- BLOCKED (chờ synchronized) ---");
        Object monitor = new Object();
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                holderHasLock.countDown();
                try {
                    releaseLock.await();          // giữ lock tới khi main cho phép thả
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Holder");
        Thread contender = new Thread(() -> {
            synchronized (monitor) {
                // vào được sau khi Holder thả lock
            }
        }, "Contender");

        holder.start();
        holderHasLock.await();                    // chắc chắn Holder đã giữ lock
        contender.start();
        awaitState(contender, Thread.State.BLOCKED);
        printState(holder, "WAITING");            // Holder giữ lock nhưng đang await() latch → WAITING (vẫn giữ monitor!)
        printState(contender, "BLOCKED");
        releaseLock.countDown();
        holder.join();
        contender.join();

        // ============================================
        // WAITING: Object.wait() trên một monitor RIÊNG + điều kiện (tránh lost notification)
        // ============================================
        System.out.println("\n--- WAITING (Object.wait) ---");
        Object signal = new Object();
        boolean[] ready = {false};                // điều kiện được bảo vệ bởi 'signal'

        Thread waiter = new Thread(() -> {
            synchronized (signal) {
                while (!ready[0]) {               // WHILE: nếu notify đến TRƯỚC wait thì cờ đã true → không chờ mãi
                    try {
                        signal.wait();            // nhả monitor 'signal' và vào WAITING
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "Waiter");
        waiter.start();
        awaitState(waiter, Thread.State.WAITING);
        printState(waiter, "WAITING");

        synchronized (signal) {
            ready[0] = true;                      // đổi điều kiện TRƯỚC, rồi mới notify
            signal.notifyAll();
        }
        waiter.join();
        printState(waiter, "TERMINATED");

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: BLOCKED khác WAITING thế nào?");
        System.out.println("A: BLOCKED - chờ lấy monitor để vào synchronized (không cần ai 'đánh thức').");
        System.out.println("   WAITING  - tự nguyện chờ một tín hiệu: wait()/join()/park(). ReentrantLock.lock() bị chặn → WAITING.");

        System.out.println("\nQ: Sau khi được notify(), thread vào state nào?");
        System.out.println("A: Chưa chạy ngay: WAITING → BLOCKED (phải lấy lại monitor) → RUNNABLE.");

        System.out.println("\nQ: Thread đang đọc socket (blocking I/O) ở state nào?");
        System.out.println("A: RUNNABLE. JVM không phân biệt 'chạy' và 'chờ I/O ở OS' → thread dump RUNNABLE chưa chắc đang ngốn CPU.");

        System.out.println("\nQ: Thread TERMINATED có start() lại được không?");
        System.out.println("A: KHÔNG. start() lần hai → IllegalThreadStateException. Muốn chạy lại thì tạo Thread/Task mới.");

        System.out.println("\nQ: sleep() vs wait()?");
        System.out.println("A: sleep() - KHÔNG nhả lock, TIMED_WAITING, gọi ở đâu cũng được.");
        System.out.println("   wait()  - NHẢ monitor, WAITING (wait(ms) → TIMED_WAITING), phải giữ monitor đó khi gọi.");

        // ============================================
        // LIFECYCLE DIAGRAM
        // ============================================
        System.out.println("\n=== THREAD LIFECYCLE ===");
        System.out.println("          start()                      run() return / throw");
        System.out.println("  NEW ───────────► RUNNABLE ───────────────────────────────► TERMINATED");
        System.out.println("                   │   ▲    │   ▲");
        System.out.println("  vào synchronized │   │    │   │ notify / unpark / hết hạn / interrupt");
        System.out.println("  nhưng lock bận   ▼   │    ▼   │");
        System.out.println("               BLOCKED │  WAITING / TIMED_WAITING");
        System.out.println("                   └───┘  (wait, join, park / sleep, wait(ms), join(ms))");
        System.out.println("            lấy được monitor");
        System.out.println("  Lưu ý: thread thoát wait() phải lấy lại monitor → có thể đi WAITING → BLOCKED → RUNNABLE.");
    }
}
