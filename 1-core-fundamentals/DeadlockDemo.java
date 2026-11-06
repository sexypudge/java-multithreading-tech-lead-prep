import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DEADLOCK - CÁC THREAD CHỜ NHAU MÃI MÃI
 *
 * 4 điều kiện Coffman (phải có ĐỦ CẢ 4 mới deadlock → phá 1 cái là hết):
 *   1. Mutual Exclusion - tài nguyên chỉ 1 thread giữ được
 *   2. Hold and Wait    - đang giữ tài nguyên này vừa chờ tài nguyên khác
 *   3. No Preemption    - không thể cưỡng chế lấy lại tài nguyên
 *   4. Circular Wait    - T1 chờ T2, T2 chờ T1 (vòng)
 *
 * Cách phá:
 *   - Lock ordering        → phá Circular Wait  (cách chuẩn, đơn giản nhất)
 *   - tryLock(timeout)     → phá Hold and Wait  (không lấy được thì nhả cái đang giữ, thử lại)
 *   - Tránh nested lock / gọi "alien method" (callback, listener) khi đang giữ lock
 *
 * Interview Question: "Làm sao detect và fix deadlock trong production?"
 * Answer: Detect: jcmd <pid> Thread.print / jstack → "Found one Java-level deadlock"; ThreadMXBean.findDeadlockedThreads().
 *         Fix: lock ordering; tryLock + timeout + backoff; giảm phạm vi lock. Restart chỉ là chữa cháy.
 */
public class DeadlockDemo {

    static final ThreadMXBean MX = ManagementFactory.getThreadMXBean();

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================
    // DOMAIN
    // ============================================
    static class BankAccount {
        final long id;                 // id duy nhất, dùng để định thứ tự lock
        private int balance;

        BankAccount(long id, int balance) {
            this.id = id;
            this.balance = balance;
        }

        void withdraw(int amount) { balance -= amount; }
        void deposit(int amount) { balance += amount; }
        int balance() { return balance; }
    }

    // ============================================
    // DEMO 1: DEADLOCK - lock theo thứ tự tham số
    // ============================================
    static void transferWithDeadlock(BankAccount from, BankAccount to, int amount) {
        synchronized (from) {
            sleep(100);                // tăng khả năng thread kia kịp lock 'to'
            synchronized (to) {
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }

    static void demo1Deadlock() throws InterruptedException {
        BankAccount a = new BankAccount(1, 1000);
        BankAccount b = new BankAccount(2, 1000);

        // A→B và B→A chạy đồng thời: T1 giữ a chờ b, T2 giữ b chờ a
        Thread t1 = new Thread(() -> transferWithDeadlock(a, b, 100), "transfer-1-to-2");
        Thread t2 = new Thread(() -> transferWithDeadlock(b, a, 200), "transfer-2-to-1");
        t1.setDaemon(true);            // daemon: thread kẹt vĩnh viễn nhưng JVM vẫn thoát được khi main xong
        t2.setDaemon(true);            // (interrupt() KHÔNG gỡ được thread đang BLOCKED ở synchronized)
        t1.start();
        t2.start();
        t1.join(1000);

        long[] ids = MX.findDeadlockedThreads();   // cùng cơ chế jstack dùng để in "Found one Java-level deadlock"
        if (ids == null) {
            System.out.println("  (lần này không deadlock - chạy lại)");
            return;
        }
        System.out.println("  ✗ findDeadlockedThreads() phát hiện " + ids.length + " thread:");
        for (ThreadInfo ti : MX.getThreadInfo(ids)) {
            System.out.println("    \"" + ti.getThreadName() + "\" " + ti.getThreadState()
                    + " chờ " + ti.getLockName() + ", đang bị giữ bởi \"" + ti.getLockOwnerName() + "\"");
        }
        System.out.println("  → Trong lúc demo chạy, thử: jcmd <pid> Thread.print (cuối dump có 'Found one Java-level deadlock')");
    }

    // ============================================
    // DEMO 2: FIX - LOCK ORDERING
    // ============================================
    static void transferWithLockOrdering(BankAccount from, BankAccount to, int amount) {
        // Mọi thread đều lock account có id nhỏ trước → không thể có vòng chờ
        BankAccount first = from.id < to.id ? from : to;
        BankAccount second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
        // Nếu không có id duy nhất: dùng System.identityHashCode + một "tie-breaker lock" khi bằng nhau
        // (Java Concurrency in Practice, 10.1.2).
    }

    static void demo2LockOrdering() throws InterruptedException {
        BankAccount a = new BankAccount(1, 1000);
        BankAccount b = new BankAccount(2, 1000);
        Thread[] ts = new Thread[8];
        for (int i = 0; i < ts.length; i++) {
            final boolean aToB = i % 2 == 0;
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    if (aToB) transferWithLockOrdering(a, b, 1);
                    else transferWithLockOrdering(b, a, 1);
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        System.out.println("  80.000 transfer hai chiều xong, tổng tiền = " + (a.balance() + b.balance())
                + " (phải 2000) " + ((a.balance() + b.balance()) == 2000 ? "✓" : "✗"));
    }

    // ============================================
    // DEMO 3: FIX - tryLock + timeout + RETRY có backoff ngẫu nhiên
    // ============================================
    static class LockedAccount {
        final long id;
        final Lock lock = new ReentrantLock();
        int balance;

        LockedAccount(long id, int balance) {
            this.id = id;
            this.balance = balance;
        }
    }

    /** Trả true nếu chuyển được; false nếu hết hạn chót (để caller quyết định: báo lỗi, đưa vào queue retry...). */
    static boolean transferWithTryLock(LockedAccount from, LockedAccount to, int amount, long deadlineMs)
            throws InterruptedException {
        long stopAt = System.currentTimeMillis() + deadlineMs;
        while (System.currentTimeMillis() < stopAt) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            from.balance -= amount;
                            to.balance += amount;
                            return true;
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();   // không lấy đủ 2 lock → NHẢ lock đang giữ (phá Hold-and-Wait)
                }
            }
            // Backoff NGẪU NHIÊN: nếu 2 thread cùng retry theo cùng nhịp, chúng có thể đụng nhau mãi → LIVELOCK
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
        }
        return false;
    }

    static void demo3TryLock() throws InterruptedException {
        LockedAccount a = new LockedAccount(1, 1000);
        LockedAccount b = new LockedAccount(2, 1000);
        int[] failures = new int[1];
        Thread t1 = new Thread(() -> runTransfers(a, b, failures));
        Thread t2 = new Thread(() -> runTransfers(b, a, failures));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("  2 thread × 200 transfer ngược chiều: tổng = " + (a.balance + b.balance)
                + " (phải 2000), số lần hết hạn = " + failures[0]
                + ((a.balance + b.balance) == 2000 ? "  ✓ không deadlock" : "  ✗"));
        System.out.println("  Lưu ý: tryLock đánh đổi sự đơn giản; caller PHẢI xử lý trường hợp trả false.");
    }

    static void runTransfers(LockedAccount from, LockedAccount to, int[] failures) {
        try {
            for (int i = 0; i < 200; i++) {
                if (!transferWithTryLock(from, to, 1, 1000)) {
                    synchronized (failures) { failures[0]++; }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== DEADLOCK DEMO ===\n");

        System.out.println("--- Demo 1: Tạo deadlock và phát hiện bằng ThreadMXBean ---");
        demo1Deadlock();

        System.out.println("\n--- Demo 2: Fix bằng lock ordering ---");
        demo2LockOrdering();

        System.out.println("\n--- Demo 3: Fix bằng tryLock + backoff ---");
        demo3TryLock();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: 4 điều kiện của deadlock?");
        System.out.println("A: Mutual exclusion, hold and wait, no preemption, circular wait. Phá 1 điều kiện là đủ.");

        System.out.println("\nQ: Detect deadlock?");
        System.out.println("A: jcmd <pid> Thread.print / jstack <pid> (tự in 'Found one Java-level deadlock' cho monitor");
        System.out.println("   và ReentrantLock), JConsole/VisualVM tab Threads, ThreadMXBean.findDeadlockedThreads() để alert.");

        System.out.println("\nQ: Cách phòng tránh?");
        System.out.println("A: 1. Lock ordering toàn cục (theo id)  2. tryLock + timeout + backoff ngẫu nhiên");
        System.out.println("   3. Không gọi code lạ (callback/listener/remote call) khi giữ lock  4. Giữ ít lock, phạm vi nhỏ");
        System.out.println("   5. Dùng công cụ cấp cao hơn (concurrent collections, message passing, DB transaction).");

        System.out.println("\nQ: Deadlock trong DB khác gì?");
        System.out.println("A: DB tự phát hiện và rollback một transaction (deadlock victim) → app phải retry.");
        System.out.println("   JVM thì KHÔNG tự gỡ: thread kẹt mãi tới khi restart.");

        System.out.println("\nQ: Còn deadlock nào không có lock?");
        System.out.println("A: Thread-pool starvation deadlock: task trong pool chờ (get/join) task khác cùng pool, pool hết thread.");
        System.out.println("   Xem 2-advanced-concurrency/CompletableFutureDemo (Demo 8) và ThreadDiagnosisDemo.");
    }
}
