import java.util.concurrent.locks.ReentrantLock;

/**
 * THREAD INTERRUPTION - CÁCH DỪNG THREAD AN TOÀN
 *
 * Java KHÔNG có cách "kill" thread an toàn (stop() đã deprecated).
 * Thay vào đó dùng cơ chế HỢP TÁC (cooperative):
 *   - Bên ngoài gọi t.interrupt()  → chỉ SET cờ interrupted = true
 *   - Thread tự kiểm tra cờ (isInterrupted) hoặc bị đánh thức bởi
 *     InterruptedException khi đang sleep/wait/join/lockInterruptibly...
 *
 * QUY TẮC VÀNG:
 *   1. Bắt InterruptedException thì: hoặc ném tiếp, hoặc khôi phục cờ
 *      bằng Thread.currentThread().interrupt(). KHÔNG được nuốt im lặng.
 *   2. Khi InterruptedException được ném, cờ interrupted bị CLEAR.
 *   3. Thread đang BLOCKED chờ synchronized KHÔNG phản ứng với interrupt.
 *
 * Interview Question: "Làm sao dừng một thread đúng cách?"
 * Answer: Dùng interrupt + kiểm tra cờ / xử lý InterruptedException,
 *         hoặc volatile flag. ExecutorService.shutdownNow() cũng dùng interrupt.
 */
public class ThreadInterruptionDemo {

    // Helper: sleep nhưng KHÔNG nuốt interrupt (đúng best practice)
    static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // khôi phục cờ
        }
    }

    // ============================================
    // DEMO 1: INTERRUPT THREAD ĐANG SLEEP
    // ============================================
    static void demo1SleepInterrupted() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                System.out.println("  [Worker] Sleep 10 giây...");
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                // Cờ đã bị CLEAR khi exception được ném
                System.out.println("  [Worker] InterruptedException! isInterrupted() = "
                        + Thread.currentThread().isInterrupted() + "  (bị clear)");
                Thread.currentThread().interrupt(); // khôi phục cờ
                System.out.println("  [Worker] Sau khi interrupt() lại: isInterrupted() = "
                        + Thread.currentThread().isInterrupted());
            }
        }, "Worker");

        t.start();
        Thread.sleep(300);
        t.interrupt();
        t.join();
    }

    // ============================================
    // DEMO 2: INTERRUPT THREAD ĐANG CHẠY VÒNG LẶP (CPU-BOUND)
    // ============================================
    static void demo2BusyLoop() throws InterruptedException {
        Thread t = new Thread(() -> {
            long n = 0;
            // Thread CPU-bound phải TỰ kiểm tra cờ, không có exception nào cả
            while (!Thread.currentThread().isInterrupted()) {
                n++;
            }
            System.out.println("  [Worker] Phát hiện interrupt, dừng sau " + n + " vòng lặp");
        }, "BusyWorker");

        t.start();
        Thread.sleep(300);
        t.interrupt();
        t.join();
    }

    // ============================================
    // DEMO 3: isInterrupted() vs interrupted()
    // ============================================
    static void demo3FlagMethods() {
        Thread.currentThread().interrupt(); // tự set cờ cho main
        System.out.println("  isInterrupted()          = " + Thread.currentThread().isInterrupted()
                + "  (chỉ đọc, KHÔNG clear)");
        System.out.println("  Thread.interrupted() #1  = " + Thread.interrupted()
                + "  (đọc và CLEAR - static method)");
        System.out.println("  Thread.interrupted() #2  = " + Thread.interrupted()
                + " (đã bị clear ở lần trước)");
    }

    // ============================================
    // DEMO 4: NUỐT INTERRUPT (SAI) vs KHÔI PHỤC (ĐÚNG)
    // ============================================
    static class BadWorker implements Runnable {
        volatile boolean stop = false;
        volatile int iterations = 0;

        @Override
        public void run() {
            while (!stop) {
                try {
                    Thread.sleep(100);
                    iterations++;
                } catch (InterruptedException e) {
                    // ✗ SAI: nuốt exception → cờ bị mất → không ai biết thread bị yêu cầu dừng
                }
            }
        }
    }

    static class GoodWorker implements Runnable {
        volatile int iterations = 0;

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(100);
                    iterations++;
                } catch (InterruptedException e) {
                    // ✓ ĐÚNG: khôi phục cờ → điều kiện while thoát vòng lặp
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    static void demo4SwallowVsRestore() throws InterruptedException {
        BadWorker bad = new BadWorker();
        Thread tBad = new Thread(bad, "BadWorker");
        tBad.start();
        Thread.sleep(250);
        tBad.interrupt();
        Thread.sleep(400);
        System.out.println("  [Bad]  Sau interrupt, thread còn sống? " + tBad.isAlive()
                + " (iterations=" + bad.iterations + ")  ✗ interrupt bị bỏ qua!");
        bad.stop = true; // phải dùng cách khác để dừng
        tBad.join();

        GoodWorker good = new GoodWorker();
        Thread tGood = new Thread(good, "GoodWorker");
        tGood.start();
        Thread.sleep(250);
        tGood.interrupt();
        tGood.join(1000);
        System.out.println("  [Good] Sau interrupt, thread còn sống? " + tGood.isAlive() + "  ✓ đã dừng");
    }

    // ============================================
    // DEMO 5: synchronized KHÔNG interruptible, lockInterruptibly() thì CÓ
    // ============================================
    static void demo5BlockedNotInterruptible() throws InterruptedException {
        // --- synchronized ---
        final Object monitor = new Object();
        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                sleepQuietly(1500);
            }
        }, "Holder");

        Thread waiter = new Thread(() -> {
            synchronized (monitor) { // bị BLOCKED ở đây
                System.out.println("  [Waiter-sync] Cuối cùng cũng lấy được lock, isInterrupted = "
                        + Thread.currentThread().isInterrupted());
            }
        }, "Waiter-sync");

        holder.start();
        Thread.sleep(200);
        waiter.start();
        Thread.sleep(200);
        waiter.interrupt();
        Thread.sleep(200);
        System.out.println("  [synchronized] Waiter state = " + waiter.getState()
                + "  → interrupt() KHÔNG làm nó thoát BLOCKED");
        holder.join();
        waiter.join();

        // --- ReentrantLock.lockInterruptibly ---
        final ReentrantLock lock = new ReentrantLock();
        Thread holder2 = new Thread(() -> {
            lock.lock();
            try {
                sleepQuietly(1500);
            } finally {
                lock.unlock();
            }
        }, "Holder2");

        Thread waiter2 = new Thread(() -> {
            try {
                lock.lockInterruptibly(); // có thể bị interrupt khi đang chờ
                try {
                    System.out.println("  [Waiter-lock] Lấy được lock");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("  [Waiter-lock] ✓ Bị interrupt khi đang chờ lock → thoát ngay");
            }
        }, "Waiter-lock");

        holder2.start();
        Thread.sleep(200);
        waiter2.start();
        Thread.sleep(200);
        waiter2.interrupt();
        waiter2.join(500);
        System.out.println("  [lockInterruptibly] Waiter còn sống? " + waiter2.isAlive());
        holder2.join();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== THREAD INTERRUPTION DEMO ===\n");

        System.out.println("--- Demo 1: Interrupt thread đang sleep ---");
        demo1SleepInterrupted();

        System.out.println("\n--- Demo 2: Interrupt thread đang chạy vòng lặp ---");
        demo2BusyLoop();

        System.out.println("\n--- Demo 3: isInterrupted() vs Thread.interrupted() ---");
        demo3FlagMethods();

        System.out.println("\n--- Demo 4: Nuốt interrupt (SAI) vs khôi phục (ĐÚNG) ---");
        demo4SwallowVsRestore();

        System.out.println("\n--- Demo 5: synchronized vs lockInterruptibly ---");
        demo5BlockedNotInterruptible();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Vì sao Thread.stop() bị deprecated?");
        System.out.println("A: Nó giải phóng lock đột ngột, để object ở trạng thái không nhất quán.");

        System.out.println("\nQ: interrupt() có dừng thread ngay không?");
        System.out.println("A: KHÔNG. Chỉ set cờ. Thread phải tự kiểm tra hoặc đang ở method blocking");
        System.out.println("   (sleep/wait/join/BlockingQueue.take...) thì mới bị đánh thức bằng exception.");

        System.out.println("\nQ: Bắt InterruptedException thì làm gì?");
        System.out.println("A: Ném tiếp (throws), hoặc Thread.currentThread().interrupt() rồi thoát.");
        System.out.println("   Tuyệt đối không để catch rỗng.");

        System.out.println("\nQ: isInterrupted() vs Thread.interrupted()?");
        System.out.println("A: isInterrupted() - instance, chỉ đọc. interrupted() - static, đọc và CLEAR cờ.");

        System.out.println("\nQ: ExecutorService.shutdown() vs shutdownNow()?");
        System.out.println("A: shutdown() - không nhận task mới, chạy nốt task đang có.");
        System.out.println("   shutdownNow() - interrupt các worker, trả về danh sách task chưa chạy.");
        System.out.println("   → Task của bạn phải xử lý interrupt đúng thì shutdownNow mới có tác dụng.");
    }
}
