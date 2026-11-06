import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WAIT / NOTIFY / NOTIFYALL - GIAO TIẾP GIỮA CÁC THREAD (mức thấp)
 *
 *   wait()      - NHẢ monitor và chờ (WAITING) tới khi được notify/interrupt (hoặc spurious wakeup)
 *   notify()    - đánh thức MỘT thread bất kỳ đang wait trên CÙNG monitor (không chọn được thread nào)
 *   notifyAll() - đánh thức TẤT CẢ; chúng lần lượt tranh lại monitor
 *
 * BA QUY TẮC:
 *   1. Phải GIỮ monitor của object đó khi gọi (trong synchronized(obj)), nếu không → IllegalMonitorStateException
 *   2. Luôn wait() trong vòng WHILE kiểm tra điều kiện, không dùng IF
 *   3. Đổi điều kiện TRƯỚC rồi mới notify; ưu tiên notifyAll() trừ khi chứng minh được notify() đủ
 *
 * Interview Question: "Tại sao wait() phải nằm trong synchronized?"
 * Answer: Để "kiểm tra điều kiện" và "đi vào chờ" là một bước nguyên tử với "đổi điều kiện + notify".
 *         Nếu không, thread kia có thể đổi điều kiện và notify ĐÚNG LÚC GIỮA lúc ta kiểm tra và lúc ta wait
 *         → tín hiệu bị mất (lost wakeup) → chờ mãi. Notification KHÔNG được lưu lại.
 *
 * Production: hầu như KHÔNG tự viết wait/notify. Dùng BlockingQueue, CountDownLatch, Semaphore,
 * hoặc Lock + Condition (xem ExplicitLockDemo, 2-advanced-concurrency/ConcurrentCollectionsDemo).
 */
public class WaitNotifyDemo {

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================
    // DEMO 1: BOUNDED BUFFER - Producer/Consumer đúng chuẩn
    // ============================================
    static class BoundedBuffer {
        private final Queue<Integer> queue = new ArrayDeque<>();
        private final int capacity;
        private final Object lock = new Object();          // private lock: không ai bên ngoài notify nhầm

        BoundedBuffer(int capacity) { this.capacity = capacity; }

        void put(int value) throws InterruptedException {
            synchronized (lock) {
                while (queue.size() == capacity) {         // WHILE: thức dậy phải kiểm tra lại
                    lock.wait();                           // nhả lock để consumer vào lấy
                }
                queue.add(value);
                lock.notifyAll();                          // có producer VÀ consumer cùng chờ trên 1 monitor
            }                                              // → notifyAll để không đánh thức nhầm "phe mình"
            // KHÔNG sleep() bên trong synchronized: sleep giữ lock, chặn mọi thread khác
        }

        int take() throws InterruptedException {
            synchronized (lock) {
                while (queue.isEmpty()) {
                    lock.wait();
                }
                int value = queue.poll();
                lock.notifyAll();
                return value;
            }
        }
    }

    static void demo1ProducerConsumer() throws InterruptedException {
        BoundedBuffer buffer = new BoundedBuffer(3);
        final int PRODUCERS = 2, CONSUMERS = 2, ITEMS_PER_PRODUCER = 1000;
        AtomicLong consumedSum = new AtomicLong();
        List<Thread> threads = new ArrayList<>();

        for (int p = 0; p < PRODUCERS; p++) {
            threads.add(new Thread(() -> {
                try {
                    for (int i = 1; i <= ITEMS_PER_PRODUCER; i++) buffer.put(i);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Producer-" + p));
        }
        for (int c = 0; c < CONSUMERS; c++) {
            threads.add(new Thread(() -> {
                try {
                    for (int i = 0; i < ITEMS_PER_PRODUCER; i++) consumedSum.addAndGet(buffer.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + c));
        }
        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        long expected = (long) PRODUCERS * ITEMS_PER_PRODUCER * (ITEMS_PER_PRODUCER + 1) / 2;
        System.out.println("  2 producer + 2 consumer, buffer=3: tổng nhận " + consumedSum.get()
                + " / mong đợi " + expected + (consumedSum.get() == expected ? "  ✓" : "  ✗"));
        System.out.println("  (Thử đổi notifyAll → notify và chạy nhiều lần: có lúc TREO, vì producer có thể");
        System.out.println("   đánh thức một producer khác thay vì consumer, rồi tất cả cùng wait.)");
    }

    // ============================================
    // DEMO 2: VÌ SAO WHILE MÀ KHÔNG PHẢI IF (tái hiện tất định)
    // ============================================
    static class IfBuffer {
        final Queue<Integer> queue = new ArrayDeque<>();

        synchronized Integer takeWithIf() throws InterruptedException {
            if (queue.isEmpty()) {          // ✗ IF: thức dậy là đi tiếp, không kiểm tra lại
                wait();
            }
            return queue.poll();            // có thể null nếu thread khác đã lấy mất
        }

        synchronized void put(int v) {
            queue.add(v);
            notifyAll();
        }
    }

    static void demo2WhileVsIf() throws InterruptedException {
        IfBuffer buf = new IfBuffer();
        Integer[] results = new Integer[2];
        Thread c1 = new Thread(() -> { try { results[0] = buf.takeWithIf(); } catch (InterruptedException ignored) { } });
        Thread c2 = new Thread(() -> { try { results[1] = buf.takeWithIf(); } catch (InterruptedException ignored) { } });
        c1.start();
        c2.start();
        while (c1.getState() != Thread.State.WAITING || c2.getState() != Thread.State.WAITING) Thread.sleep(5);

        buf.put(42);                        // CHỈ 1 phần tử, nhưng notifyAll đánh thức cả 2 consumer
        c1.join();
        c2.join();
        System.out.println("  Consumer-1 nhận: " + results[0] + ", Consumer-2 nhận: " + results[1]);
        System.out.println("  ✗ Một consumer nhận null: nó thức dậy sau khi consumer kia đã lấy mất phần tử.");
        System.out.println("  → WHILE cần vì: (1) điều kiện có thể bị thread khác 'cướp' trước khi ta lấy lại lock,");
        System.out.println("    (2) spurious wakeup - JVM/OS được phép đánh thức mà không có notify.");
    }

    // ============================================
    // DEMO 3: notify() vs notifyAll()
    // ============================================
    static class Gate {
        private boolean open = false;

        synchronized void await() throws InterruptedException {
            while (!open) wait();
        }

        synchronized void openWithNotify() { open = true; notify(); }
        synchronized void openWithNotifyAll() { open = true; notifyAll(); }
    }

    static void runGate(boolean useNotifyAll) throws InterruptedException {
        Gate gate = new Gate();
        Thread[] waiters = new Thread[3];
        for (int i = 0; i < waiters.length; i++) {
            waiters[i] = new Thread(() -> {
                try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "Waiter-" + i);
            waiters[i].start();
        }
        for (Thread w : waiters) while (w.getState() != Thread.State.WAITING) Thread.sleep(5);

        if (useNotifyAll) gate.openWithNotifyAll(); else gate.openWithNotify();
        Thread.sleep(200);

        int stillWaiting = 0;
        for (Thread w : waiters) if (w.isAlive()) stillWaiting++;
        System.out.println("  " + (useNotifyAll ? "notifyAll()" : "notify()   ") + " → còn " + stillWaiting
                + "/3 thread chờ mãi" + (stillWaiting > 0 ? "  ✗ (không ai đánh thức chúng nữa)" : "  ✓"));

        for (Thread w : waiters) w.interrupt();     // dọn dẹp: thread kẹt ở wait() phản hồi interrupt
        for (Thread w : waiters) w.join();
    }

    // ============================================
    // DEMO 4: gọi wait() khi không giữ monitor
    // ============================================
    static void demo4IllegalMonitorState() {
        Object lock = new Object();
        try {
            lock.wait();
        } catch (IllegalMonitorStateException e) {
            System.out.println("  lock.wait() ngoài synchronized(lock) → IllegalMonitorStateException");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== WAIT/NOTIFY DEMO ===\n");

        System.out.println("--- Demo 1: Producer-Consumer với bounded buffer ---");
        demo1ProducerConsumer();

        System.out.println("\n--- Demo 2: while vs if ---");
        demo2WhileVsIf();

        System.out.println("\n--- Demo 3: notify() vs notifyAll() ---");
        runGate(false);
        runGate(true);

        System.out.println("\n--- Demo 4: IllegalMonitorStateException ---");
        demo4IllegalMonitorState();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Tại sao wait() phải trong synchronized?");
        System.out.println("A: Kiểm tra điều kiện + wait phải nguyên tử với đổi điều kiện + notify, nếu không sẽ lost wakeup.");
        System.out.println("   JVM ép buộc bằng IllegalMonitorStateException.");

        System.out.println("\nQ: Tại sao dùng while thay vì if?");
        System.out.println("A: Thread khác có thể đã 'lấy mất' điều kiện trước khi ta giành lại lock, và spurious wakeup.");

        System.out.println("\nQ: notify() vs notifyAll()?");
        System.out.println("A: notify đánh thức 1 thread tuỳ ý. Chỉ an toàn khi mọi thread chờ cùng 1 điều kiện và mỗi tín hiệu");
        System.out.println("   chỉ cần 1 thread xử lý. Khác đi (producer + consumer cùng monitor) → dùng notifyAll,");
        System.out.println("   hoặc Lock với 2 Condition (notFull/notEmpty) để signal đúng nhóm.");

        System.out.println("\nQ: wait() vs sleep()?");
        System.out.println("A: wait() thuộc Object, NHẢ monitor, dùng để phối hợp theo điều kiện.");
        System.out.println("   sleep() thuộc Thread, GIỮ mọi lock, chỉ để tạm dừng theo thời gian.");

        System.out.println("\nQ: Sau notify(), thread được đánh thức chạy ngay chứ?");
        System.out.println("A: Không. notify() không nhả lock; thread kia chuyển sang BLOCKED chờ lấy lại monitor");
        System.out.println("   sau khi thread notify thoát khỏi synchronized.");

        System.out.println("\n=== COMMON PATTERN ===");
        System.out.println("synchronized (lock) {");
        System.out.println("    while (!condition) {   // WHILE, not IF");
        System.out.println("        lock.wait();");
        System.out.println("    }");
        System.out.println("    // làm việc, đổi trạng thái");
        System.out.println("    lock.notifyAll();");
        System.out.println("}");
    }
}
