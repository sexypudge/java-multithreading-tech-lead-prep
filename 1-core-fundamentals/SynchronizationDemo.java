import java.util.function.IntSupplier;

/**
 * SYNCHRONIZATION TRONG JAVA (intrinsic lock / monitor)
 *
 * synchronized đảm bảo HAI thứ:
 *   1. Mutual exclusion : tại một thời điểm chỉ 1 thread giữ một monitor cụ thể
 *   2. Visibility       : unlock monitor M happens-before lần lock M tiếp theo
 *                         → thread sau thấy mọi thay đổi thread trước làm trong khối synchronized
 *
 * 3 dạng - quan trọng nhất là biết LOCK TRÊN OBJECT NÀO:
 *   synchronized instance method  → lock trên 'this'
 *   synchronized (lockObj) { }    → lock trên lockObj
 *   static synchronized method    → lock trên Class object (Foo.class)
 *
 * Interview Question: "synchronized method vs synchronized block?"
 * Answer: Khác nhau ở PHẠM VI và LOCK OBJECT. Block cho phép thu hẹp critical section và dùng lock
 *         private (không ai bên ngoài lock nhầm được). Block KHÔNG tự động nhanh hơn: chỉ có lợi khi
 *         nó thực sự giảm thời gian giữ lock.
 *
 * Tài liệu kèm theo: 05-Synchronization.md (giải thích + câu trả lời tiếng Anh).
 */
public class SynchronizationDemo {

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================
    // PROBLEM: RACE CONDITION WITHOUT SYNC
    // ============================================
    static class UnsafeCounter {
        private int count = 0;

        void increment() { count++; }      // NOT atomic: read → modify → write
        int getCount() { return count; }
    }

    // ============================================
    // SOLUTION 1: SYNCHRONIZED METHOD (lock 'this')
    // ============================================
    static class SafeCounterMethod {
        private int count = 0;

        synchronized void increment() { count++; }
        synchronized int getCount() { return count; }   // đọc CŨNG phải synchronized → visibility
    }

    // ============================================
    // SOLUTION 2: SYNCHRONIZED BLOCK (lock private)
    // ============================================
    static class SafeCounterBlock {
        private int count = 0;
        private final Object lock = new Object();         // private final: code bên ngoài không lock nhầm được

        void increment() {
            // ... code không đụng shared state có thể nằm ngoài lock ...
            synchronized (lock) {
                count++;
            }
        }

        int getCount() {
            synchronized (lock) {
                return count;
            }
        }
    }

    // ============================================
    // SOLUTION 3: STATIC SYNCHRONIZED (lock Class object)
    // ============================================
    static class StaticCounter {
        private static int count = 0;

        static synchronized void increment() { count++; }   // ≡ synchronized (StaticCounter.class)
        static synchronized int getCount() { return count; }
    }

    // ============================================
    // REENTRANCY
    // ============================================
    static class ReentrantExample {
        synchronized void outerMethod() {
            System.out.println("  In outerMethod (giữ monitor của this)");
            innerMethod();   // cần lại CÙNG monitor → được phép vì intrinsic lock là reentrant
        }

        synchronized void innerMethod() {
            System.out.println("  In innerMethod - reentrant: không tự deadlock");
        }
    }

    // ============================================
    // LOCK TARGET: ai block ai?
    // ============================================
    static class Resource {
        synchronized void instanceWork(String who) {
            System.out.println("    " + who + " vào instanceWork của " + this.hashCode());
            sleep(300);
        }

        static synchronized void staticWork(String who) {
            System.out.println("    " + who + " vào staticWork (lock Resource.class)");
            sleep(300);
        }
    }

    /** Chạy 2 việc song song và đo thời gian: ~300ms = không chặn nhau, ~600ms = chặn nhau. */
    static long runPair(Runnable a, Runnable b) throws InterruptedException {
        long start = System.nanoTime();
        Thread t1 = new Thread(a);
        Thread t2 = new Thread(b);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        return (System.nanoTime() - start) / 1_000_000;
    }

    static void demoLockTargets() throws InterruptedException {
        Resource r1 = new Resource();
        Resource r2 = new Resource();

        long sameInstance = runPair(() -> r1.instanceWork("T1"), () -> r1.instanceWork("T2"));
        System.out.println("  Cùng instance           : " + sameInstance + "ms  → CHẶN nhau (cùng monitor r1)");

        long diffInstance = runPair(() -> r1.instanceWork("T1"), () -> r2.instanceWork("T2"));
        System.out.println("  Khác instance           : " + diffInstance + "ms  → KHÔNG chặn (r1 và r2 là 2 monitor khác)");

        long staticVsInstance = runPair(() -> r1.instanceWork("T1"), () -> Resource.staticWork("T2"));
        System.out.println("  static vs instance      : " + staticVsInstance + "ms  → KHÔNG chặn (Resource.class ≠ r1)");
        System.out.println("  ⚠ Bẫy: method static sửa field static, method instance cũng sửa field static đó");
        System.out.println("    → hai lock khác nhau bảo vệ cùng một dữ liệu = KHÔNG thread-safe.");
    }

    // ============================================
    // EDGE CASES
    // ============================================
    static void demoEdgeCases() throws InterruptedException {
        // 1. synchronized(null) → NullPointerException
        Object nullLock = null;
        try {
            synchronized (nullLock) {
                System.out.println("  không bao giờ in");
            }
        } catch (NullPointerException e) {
            System.out.println("  synchronized(null)      → NullPointerException");
        }

        // 2. Exception trong khối synchronized → monitor VẪN được nhả (khác với Lock quên unlock)
        Object lock = new Object();
        try {
            synchronized (lock) {
                throw new IllegalStateException("boom");
            }
        } catch (IllegalStateException e) {
            Thread other = new Thread(() -> {
                synchronized (lock) {
                    System.out.println("  exception trong sync    → monitor tự nhả, thread khác vào được ✓");
                }
            });
            other.start();
            other.join();
        }

        // 3. Lock trên object bị gán lại → mỗi thread có thể lock một object KHÁC NHAU
        System.out.println("  ⚠ Đừng lock trên field không final (bị gán lại = đổi lock), trên String literal");
        System.out.println("    hay Integer cache (dùng chung toàn JVM), hay object public (người khác lock nhầm).");
    }

    // ============================================
    // HELPER
    // ============================================
    static void testCounter(String name, Runnable increment, IntSupplier getCount,
                            int threads, int increments) throws InterruptedException {
        Thread[] ts = new Thread[threads];
        long start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < increments; j++) increment.run();
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();   // join() cũng tạo happens-before: main thấy mọi ghi của worker
        long ms = (System.nanoTime() - start) / 1_000_000;

        int expected = threads * increments;
        int actual = getCount.getAsInt();
        System.out.printf("  %-22s expected=%,d actual=%,d  %4dms  %s%n", name, expected, actual, ms,
                expected == actual ? "✓" : "✗ RACE CONDITION (mất " + (expected - actual) + " lần cộng)");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== SYNCHRONIZATION DEMO ===\n");

        final int THREADS = 8;
        final int INCREMENTS = 100_000;

        System.out.println("--- Demo 1: Counter (unsafe vs 3 dạng synchronized) ---");
        UnsafeCounter unsafe = new UnsafeCounter();
        testCounter("Unsafe", unsafe::increment, unsafe::getCount, THREADS, INCREMENTS);

        SafeCounterMethod method = new SafeCounterMethod();
        testCounter("Synchronized method", method::increment, method::getCount, THREADS, INCREMENTS);

        SafeCounterBlock block = new SafeCounterBlock();
        testCounter("Synchronized block", block::increment, block::getCount, THREADS, INCREMENTS);

        testCounter("Static synchronized", StaticCounter::increment, StaticCounter::getCount, THREADS, INCREMENTS);

        System.out.println("\n--- Demo 2: Reentrancy ---");
        new ReentrantExample().outerMethod();

        System.out.println("\n--- Demo 3: Lock target - ai chặn ai? ---");
        demoLockTargets();

        System.out.println("\n--- Demo 4: Edge cases ---");
        demoEdgeCases();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: synchronized đảm bảo gì?");
        System.out.println("A: Mutual exclusion trên CÙNG monitor + visibility (unlock happens-before lock tiếp theo).");
        System.out.println("   Vì vậy getter cũng phải synchronized, không chỉ setter.");

        System.out.println("\nQ: synchronized method vs block?");
        System.out.println("A: Method lock 'this' (hoặc Class) cho cả method. Block chọn lock object và phạm vi.");
        System.out.println("   Ưu tiên block với private final lock: thu hẹp critical section, tránh code ngoài lock nhầm.");

        System.out.println("\nQ: Hai thread gọi synchronized method trên 2 instance khác nhau có chặn nhau không?");
        System.out.println("A: Không - mỗi instance có monitor riêng. static synchronized và instance synchronized cũng không chặn nhau.");

        System.out.println("\nQ: synchronized có reentrant không? synchronized(null)?");
        System.out.println("A: Có reentrant (đếm số lần giữ). synchronized(null) → NullPointerException.");

        System.out.println("\nQ: synchronized có chậm không?");
        System.out.println("A: Không tranh chấp thì rất rẻ (JVM tối ưu). Chậm là do CONTENTION: thread phải chờ, park/unpark,");
        System.out.println("   context switch. Giảm bằng critical section ngắn, không làm I/O khi giữ lock, tách lock, hoặc");
        System.out.println("   dùng cấu trúc phù hợp (Atomic*, ConcurrentHashMap, LongAdder).");

        System.out.println("\nQ: Alternatives?");
        System.out.println("A: ReentrantLock (tryLock/timeout/interruptible/fair/nhiều Condition), ReadWriteLock, StampedLock,");
        System.out.println("   Atomic*, concurrent collections, immutable objects. Xem ExplicitLockDemo.");
    }
}
