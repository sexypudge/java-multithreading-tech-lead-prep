import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LIVELOCK & STARVATION - CÁC LỖI "LIVENESS" KHÁC NGOÀI DEADLOCK
 *
 * | Vấn đề     | Thread trạng thái        | Có tiến triển? | Ví dụ                                 |
 * |------------|--------------------------|----------------|---------------------------------------|
 * | Deadlock   | BLOCKED/WAITING          | KHÔNG          | A giữ L1 chờ L2, B giữ L2 chờ L1      |
 * | Livelock   | RUNNABLE (vẫn chạy!)     | KHÔNG          | 2 người cùng nhường đường cho nhau    |
 * | Starvation | RUNNABLE/BLOCKED         | 1 thread không | Thread priority thấp / lock unfair    |
 *
 * Livelock rất khó phát hiện vì CPU vẫn bận, thread dump thấy RUNNABLE, log vẫn chạy.
 * Dấu hiệu: CPU cao + throughput bằng 0 + log lặp lại cùng một mẫu.
 *
 * Interview Question: "Deadlock vs Livelock vs Starvation?"
 * Answer: Deadlock - đứng im chờ nhau. Livelock - cứ phản ứng với nhau nhưng không tiến lên.
 *         Starvation - một thread bị các thread khác chiếm tài nguyên mãi.
 */
public class LivelockStarvationDemo {

    static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================
    // DEMO 1: LIVELOCK - "Hai người ăn chung một cái thìa"
    // ============================================
    /**
     * Quy tắc lịch sự: nếu bạn đời cũng đang đói thì nhường thìa cho họ.
     * Cả hai cùng lịch sự → thìa chuyền qua lại mãi, không ai ăn được.
     */
    static class Spoon {
        private volatile Diner owner;

        Spoon(Diner owner) { this.owner = owner; }
        Diner getOwner() { return owner; }
        void setOwner(Diner d) { this.owner = d; }
    }

    static class Diner {
        final String name;
        volatile boolean hungry = true;
        final AtomicInteger handoffs = new AtomicInteger();

        Diner(String name) { this.name = name; }

        /**
         * @param randomBackoff false = luôn nhường (gây livelock); true = nhường ngẫu nhiên (tự thoát)
         */
        void eatWith(Spoon spoon, Diner spouse, int maxRounds, boolean randomBackoff) {
            int rounds = 0;
            while (hungry && rounds++ < maxRounds) {
                if (spoon.getOwner() != this) {
                    sleepQuietly(1); // chờ tới lượt
                    continue;
                }
                boolean shouldYield = spouse.hungry
                        && (!randomBackoff || ThreadLocalRandom.current().nextBoolean());
                if (shouldYield) {
                    handoffs.incrementAndGet();
                    spoon.setOwner(spouse); // "Mời anh/chị dùng trước"
                    continue;
                }
                hungry = false;             // ăn được rồi
                spoon.setOwner(spouse);
            }
        }
    }

    static void runDiners(boolean randomBackoff) throws InterruptedException {
        Diner a = new Diner("Alice");
        Diner b = new Diner("Bob");
        Spoon spoon = new Spoon(a);
        final int MAX_ROUNDS = 500;

        Thread ta = new Thread(() -> a.eatWith(spoon, b, MAX_ROUNDS, randomBackoff), "Alice");
        Thread tb = new Thread(() -> b.eatWith(spoon, a, MAX_ROUNDS, randomBackoff), "Bob");
        ta.start();
        tb.start();
        ta.join();
        tb.join();

        System.out.println("  Alice còn đói? " + a.hungry + " | Bob còn đói? " + b.hungry
                + " | số lần nhường thìa: " + (a.handoffs.get() + b.handoffs.get()));
        if (a.hungry && b.hungry) {
            System.out.println("  ✗ LIVELOCK: hai thread chạy suốt nhưng không ai ăn được");
        } else {
            System.out.println("  ✓ Cả hai đều ăn được: random backoff phá vỡ sự đối xứng giữa 2 thread"
                    + " (chạy lại vài lần để thấy số lần nhường thay đổi)");
        }
    }

    // ============================================
    // DEMO 2: STARVATION - fair vs unfair lock
    // ============================================
    static void runLockContention(boolean fair) throws InterruptedException {
        final ReentrantLock lock = new ReentrantLock(fair);
        final int THREADS = 4;
        final long DURATION_MS = 1000;
        final AtomicInteger[] counts = new AtomicInteger[THREADS];
        Thread[] ts = new Thread[THREADS];

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DURATION_MS);
        for (int i = 0; i < THREADS; i++) {
            counts[i] = new AtomicInteger();
            final int idx = i;
            ts[i] = new Thread(() -> {
                while (System.nanoTime() < deadline) {
                    lock.lock();
                    try {
                        counts[idx].incrementAndGet();
                        LockSupport.parkNanos(20_000); // giữ lock ~20µs
                    } finally {
                        lock.unlock();
                    }
                }
            }, "T" + i);
            ts[i].start();
        }
        for (Thread t : ts) t.join();

        int min = Integer.MAX_VALUE, max = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < THREADS; i++) {
            int c = counts[i].get();
            min = Math.min(min, c);
            max = Math.max(max, c);
            sb.append("T").append(i).append("=").append(c).append(" ");
        }
        System.out.println("  " + (fair ? "FAIR  : " : "UNFAIR: ") + sb
                + "| chênh lệch max/min = " + String.format("%.1f", (double) max / Math.max(min, 1)) + "x");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== LIVELOCK & STARVATION DEMO ===\n");

        System.out.println("--- Demo 1a: LIVELOCK (luôn nhường nhau) ---");
        runDiners(false);

        System.out.println("\n--- Demo 1b: FIX bằng random backoff ---");
        runDiners(true);

        System.out.println("\n--- Demo 2: STARVATION với lock unfair vs fair ---");
        System.out.println("(Kết quả khác nhau theo máy/OS: unfair thường lệch nhiều, fair thường đều hơn)");
        runLockContention(false);
        runLockContention(true);

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Livelock xảy ra khi nào? Ví dụ thực tế?");
        System.out.println("A: Khi các thread liên tục phản ứng với nhau (nhường, retry, rollback) mà không tiến lên.");
        System.out.println("   Ví dụ: 2 transaction cùng rollback-retry ngay lập tức khi conflict; tryLock rồi unlock");
        System.out.println("   ngay nếu không lấy đủ cả 2 lock - lặp lại mãi, mỗi lần đều đụng nhau.");

        System.out.println("\nQ: Cách khắc phục livelock?");
        System.out.println("A: Random backoff / exponential backoff có jitter, đặt thứ tự ưu tiên (người có ID nhỏ");
        System.out.println("   được đi trước), giới hạn số lần retry, dùng coordinator.");

        System.out.println("\nQ: Nguyên nhân starvation?");
        System.out.println("A: 1. Lock unfair để thread 'barging' chiếm liên tục");
        System.out.println("   2. Thread priority thấp (không đáng tin vì phụ thuộc OS)");
        System.out.println("   3. Thread giữ lock quá lâu (I/O trong critical section)");
        System.out.println("   4. Pool nhỏ bị task dài chiếm hết, task ngắn chờ mãi");
        System.out.println("   5. ReadWriteLock: reader liên tục làm writer đói");

        System.out.println("\nQ: Cách khắc phục starvation?");
        System.out.println("A: Fair lock (đánh đổi throughput), rút ngắn critical section, tách pool theo loại task,");
        System.out.println("   không dựa vào thread priority.");

        System.out.println("\nQ: Phát hiện livelock trong production?");
        System.out.println("A: CPU cao nhưng throughput thấp; thread dump nhiều lần liên tiếp thấy cùng thread");
        System.out.println("   RUNNABLE ở cùng đoạn code retry; log lặp. jstack không báo 'deadlock found'.");
    }
}
