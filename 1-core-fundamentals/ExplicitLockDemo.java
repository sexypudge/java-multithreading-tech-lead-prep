import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * EXPLICIT LOCKS (java.util.concurrent.locks)
 *
 * synchronized đơn giản nhưng thiếu: timeout, interruptible, fairness,
 * nhiều điều kiện chờ, tách lock đọc/ghi.
 *
 * | Tính năng               | synchronized | ReentrantLock | ReadWriteLock | StampedLock |
 * |-------------------------|:------------:|:-------------:|:-------------:|:-----------:|
 * | Tự động unlock          |      ✓       |  ✗ (finally)  |  ✗ (finally)  | ✗ (finally) |
 * | tryLock / timeout       |      ✗       |      ✓        |      ✓        |     ✓       |
 * | Interruptible           |      ✗       |      ✓        |      ✓        |     ✓       |
 * | Fair mode               |      ✗       |      ✓        |      ✓        |     ✗       |
 * | Nhiều Condition         |      ✗       |      ✓        |   (write)     |     ✗       |
 * | Reader đồng thời        |      ✗       |      ✗        |      ✓        |     ✓       |
 * | Optimistic read         |      ✗       |      ✗        |      ✗        |     ✓       |
 * | Reentrant               |      ✓       |      ✓        |      ✓        |   ✗ (KHÔNG) |
 *
 * Interview Question: "Khi nào dùng ReentrantLock thay vì synchronized?"
 * Answer: Khi cần tryLock/timeout, lockInterruptibly, fair lock, hoặc nhiều Condition.
 *         Ngoài ra, ưu tiên synchronized vì đơn giản và không thể quên unlock.
 */
public class ExplicitLockDemo {

    static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================
    // DEMO 1: REENTRANTLOCK CƠ BẢN + tryLock
    // ============================================
    static void demo1ReentrantLockBasics() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        // Pattern BẮT BUỘC: lock() ngay trước try, unlock() trong finally
        lock.lock();
        try {
            lock.lock(); // reentrant
            try {
                System.out.println("  getHoldCount() = " + lock.getHoldCount() + "  (reentrant)");
            } finally {
                lock.unlock();
            }
        } finally {
            lock.unlock();
        }

        // tryLock khi lock đang bị thread khác giữ
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                sleepQuietly(600);
            } finally {
                lock.unlock();
            }
        }, "Holder");
        holder.start();
        Thread.sleep(100);

        System.out.println("  tryLock() ngay lập tức        = " + lock.tryLock() + "  (không chờ)");
        boolean got = lock.tryLock(200, TimeUnit.MILLISECONDS);
        System.out.println("  tryLock(200ms)                = " + got + "  (hết timeout)");
        holder.join();
        if (lock.tryLock()) {
            try {
                System.out.println("  tryLock() sau khi holder xong = true");
            } finally {
                lock.unlock();
            }
        }
    }

    // ============================================
    // DEMO 2: CONDITION - NHIỀU ĐIỀU KIỆN CHỜ (Bounded Buffer)
    // ============================================
    /**
     * Với wait/notify chỉ có 1 wait-set → phải notifyAll() đánh thức cả producer lẫn consumer.
     * Với Condition: notFull và notEmpty tách biệt → signal đúng nhóm cần đánh thức.
     */
    static class BoundedBuffer<T> {
        private final Object[] items;
        private int putIdx, takeIdx, count;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BoundedBuffer(int capacity) {
            items = new Object[capacity];
        }

        void put(T x) throws InterruptedException {
            lock.lock();
            try {
                while (count == items.length) {   // vẫn dùng WHILE như wait/notify
                    notFull.await();
                }
                items[putIdx] = x;
                putIdx = (putIdx + 1) % items.length;
                count++;
                notEmpty.signal();                // chỉ đánh thức consumer
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        T take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) {
                    notEmpty.await();
                }
                T x = (T) items[takeIdx];
                items[takeIdx] = null;
                takeIdx = (takeIdx + 1) % items.length;
                count--;
                notFull.signal();                 // chỉ đánh thức producer
                return x;
            } finally {
                lock.unlock();
            }
        }
    }

    static void demo2Condition() throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(3);
        AtomicInteger sum = new AtomicInteger();
        final int PRODUCERS = 2, CONSUMERS = 2, ITEMS_EACH = 50;

        Thread[] threads = new Thread[PRODUCERS + CONSUMERS];
        for (int i = 0; i < PRODUCERS; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 1; j <= ITEMS_EACH; j++) buffer.put(j);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Producer-" + i);
        }
        for (int i = 0; i < CONSUMERS; i++) {
            threads[PRODUCERS + i] = new Thread(() -> {
                try {
                    for (int j = 1; j <= ITEMS_EACH; j++) sum.addAndGet(buffer.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int expected = PRODUCERS * (ITEMS_EACH * (ITEMS_EACH + 1) / 2);
        System.out.println("  Tổng nhận được = " + sum.get() + ", mong đợi = " + expected
                + (sum.get() == expected ? "  ✓" : "  ✗"));
    }

    // ============================================
    // DEMO 3: READWRITELOCK - nhiều reader song song
    // ============================================
    static class Cache {
        private final Map<String, String> map = new HashMap<>();
        private final ReadWriteLock rw = new ReentrantReadWriteLock();

        String get(String key, long holdMs) {
            rw.readLock().lock();
            try {
                sleepQuietly(holdMs); // giả lập đọc chậm
                return map.get(key);
            } finally {
                rw.readLock().unlock();
            }
        }

        void put(String key, String value) {
            rw.writeLock().lock();
            try {
                map.put(key, value);
            } finally {
                rw.writeLock().unlock();
            }
        }
    }

    static void demo3ReadWriteLock() throws InterruptedException {
        Cache cache = new Cache();
        cache.put("k", "v");

        Thread[] readers = new Thread[3];
        long start = System.currentTimeMillis();
        for (int i = 0; i < readers.length; i++) {
            readers[i] = new Thread(() -> cache.get("k", 300));
            readers[i].start();
        }
        for (Thread r : readers) r.join();
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  3 reader x 300ms chạy mất ~" + elapsed + "ms  (≈300ms nghĩa là song song;"
                + " synchronized sẽ mất ≈900ms)");
        System.out.println("  Lưu ý: ReentrantReadWriteLock KHÔNG cho nâng cấp read → write (sẽ deadlock),"
                + " chỉ cho hạ cấp write → read.");
    }

    // ============================================
    // DEMO 4: STAMPEDLOCK - OPTIMISTIC READ
    // ============================================
    static class Point {
        private double x, y;
        private final StampedLock sl = new StampedLock();

        void move(double dx, double dy) {
            long stamp = sl.writeLock();
            try {
                x += dx;
                y += dy;
            } finally {
                sl.unlockWrite(stamp);
            }
        }

        double distanceFromOrigin() {
            long stamp = sl.tryOptimisticRead();   // KHÔNG lock, chỉ lấy "tem"
            double cx = x, cy = y;                 // đọc dữ liệu
            if (!sl.validate(stamp)) {             // có writer chen vào trong lúc đọc?
                stamp = sl.readLock();             // fallback: đọc với read lock thật
                try {
                    cx = x;
                    cy = y;
                } finally {
                    sl.unlockRead(stamp);
                }
            }
            return Math.sqrt(cx * cx + cy * cy);
        }
    }

    static void demo4StampedLock() {
        Point p = new Point();
        p.move(3, 4);
        System.out.println("  distanceFromOrigin = " + p.distanceFromOrigin() + "  (mong đợi 5.0)");
        System.out.println("  StampedLock KHÔNG reentrant: lock 2 lần trong cùng thread sẽ tự deadlock.");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== EXPLICIT LOCK DEMO ===\n");

        System.out.println("--- Demo 1: ReentrantLock + tryLock ---");
        demo1ReentrantLockBasics();

        System.out.println("\n--- Demo 2: Condition (Bounded Buffer) ---");
        demo2Condition();

        System.out.println("\n--- Demo 3: ReadWriteLock ---");
        demo3ReadWriteLock();

        System.out.println("\n--- Demo 4: StampedLock ---");
        demo4StampedLock();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Vì sao unlock() phải trong finally?");
        System.out.println("A: Nếu code giữa lock/unlock ném exception mà không finally → lock bị giữ mãi,");
        System.out.println("   mọi thread khác bị treo. synchronized tự unlock còn Lock thì không.");

        System.out.println("\nQ: ReentrantLock fair vs unfair?");
        System.out.println("A: Fair: thread chờ lâu nhất được ưu tiên, tránh starvation nhưng throughput thấp hơn.");
        System.out.println("   Unfair (mặc định): cho phép 'barging', nhanh hơn nhưng có thể đói.");

        System.out.println("\nQ: Condition khác wait/notify thế nào?");
        System.out.println("A: Một Lock có nhiều Condition → nhiều hàng đợi riêng (notFull, notEmpty),");
        System.out.println("   signal đúng đối tượng, không cần đánh thức tất cả.");

        System.out.println("\nQ: Khi nào dùng ReadWriteLock? Nhược điểm?");
        System.out.println("A: Đọc nhiều, ghi ít, critical section đọc đủ lâu. Nhược: writer có thể bị đói,");
        System.out.println("   overhead cao hơn synchronized khi section ngắn, không upgrade được read→write.");

        System.out.println("\nQ: StampedLock có gì đặc biệt?");
        System.out.println("A: Optimistic read: đọc không lock rồi validate. Rất nhanh khi ít ghi.");
        System.out.println("   Nhưng không reentrant, không có Condition, dễ dùng sai.");

        System.out.println("\nQ: Với virtual thread (Java 21) nên dùng gì?");
        System.out.println("A: ReentrantLock thay cho synchronized quanh code blocking để tránh pinning");
        System.out.println("   (Java 21-23; từ Java 24 synchronized đã bớt pin). Sẽ học ở module sau.");
    }
}
