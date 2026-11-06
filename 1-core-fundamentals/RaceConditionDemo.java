import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

/**
 * RACE CONDITION - KẾT QUẢ PHỤ THUỘC VÀO THỨ TỰ XEN KẼ CỦA CÁC THREAD
 *
 * Điều kiện xảy ra: nhiều thread truy cập shared mutable state, ít nhất 1 thread ghi, không có đồng bộ đúng.
 *
 * 2 mẫu kinh điển:
 *   1. Read-Modify-Write : count++, balance -= x       (đọc → tính → ghi bị xen ngang → lost update)
 *   2. Check-Then-Act    : if (x == null) x = new X()  (điều kiện đã kiểm tra có thể sai khi hành động)
 *
 * Race condition vs Data race (câu hỏi phân loại hay gặp):
 *   - Data race     : 2 thread truy cập cùng biến, ≥1 ghi, KHÔNG có happens-before (khái niệm của JMM)
 *   - Race condition: lỗi LOGIC do thứ tự thực thi; có thể xảy ra cả khi không có data race
 *                     (vd: ConcurrentHashMap get() rồi put() - mỗi thao tác đều thread-safe nhưng chuỗi thì không)
 *
 * Để demo thấy lỗi ổn định, một số chỗ cố ý "nới rộng cửa sổ race" bằng sleep/yield + "cổng xuất phát"
 * (CountDownLatch) cho mọi thread cùng chạy một lúc. Trong production, cửa sổ nhỏ hơn nhiều - lỗi hiếm nhưng VẪN CÓ.
 */
public class RaceConditionDemo {

    // ============================================
    // PROBLEM 1: READ-MODIFY-WRITE
    // ============================================
    static class UnsafeCounter {
        private int count = 0;
        void increment() { count++; }   // getfield → iadd → putfield (xem: javap -c)
        int get() { return count; }
    }

    static class SafeCounterSync {
        private int count = 0;
        synchronized void increment() { count++; }
        synchronized int get() { return count; }
    }

    static class SafeCounterAtomic {
        private final AtomicInteger count = new AtomicInteger();
        void increment() { count.incrementAndGet(); }   // CAS, lock-free
        int get() { return count.get(); }
    }

    // ============================================
    // PROBLEM 2: CHECK-THEN-ACT (lazy initialization)
    // ============================================
    static class UnsafeSingleton {
        private static UnsafeSingleton instance;

        static UnsafeSingleton getInstance() {
            if (instance == null) {                      // Check
                Thread.yield();                          // (nới rộng cửa sổ race cho demo)
                instance = new UnsafeSingleton();        // Act - nhiều thread cùng tới đây
            }
            return instance;
        }
    }

    /** Fix đơn giản nhất cho lazy singleton: holder idiom - JVM đảm bảo class init chỉ chạy 1 lần, thread-safe. */
    static class HolderSingleton {
        private HolderSingleton() { }
        private static class Holder { static final HolderSingleton INSTANCE = new HolderSingleton(); }
        static HolderSingleton getInstance() { return Holder.INSTANCE; }
    }
    // Các phương án khác: synchronized getInstance() (đơn giản, mỗi lần gọi đều lock),
    // double-checked locking với volatile (xem JavaMemoryModelDemo - vì sao BẮT BUỘC volatile), enum singleton.

    // ============================================
    // PROBLEM 3: COMPOUND OPERATION (check + update)
    // ============================================
    static class UnsafeAccount {
        private int balance = 1000;

        boolean withdraw(int amount) {
            if (balance >= amount) {                     // Check
                Thread.yield();                          // (nới rộng cửa sổ race cho demo)
                balance -= amount;                       // Act (bản thân cũng là read-modify-write)
                return true;
            }
            return false;
        }
        int balance() { return balance; }
    }

    static class SafeAccount {
        private int balance = 1000;

        synchronized boolean withdraw(int amount) {      // check + act trong CÙNG một critical section
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        }
        synchronized int balance() { return balance; }
    }

    // ============================================
    // HELPERS - "cổng xuất phát" để mọi thread chạy cùng lúc
    // ============================================
    static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    startGate.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            ts[i].start();
        }
        startGate.countDown();
        for (Thread t : ts) t.join();
    }

    static void testCounter(String name, Runnable increment, IntSupplier get) throws InterruptedException {
        final int THREADS = 8, PER_THREAD = 100_000;
        runConcurrently(THREADS, () -> { for (int i = 0; i < PER_THREAD; i++) increment.run(); });
        int expected = THREADS * PER_THREAD;
        System.out.printf("  %-13s expected=%,d actual=%,d %s%n", name, expected, get.getAsInt(),
                expected == get.getAsInt() ? "✓" : "✗ lost update");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== RACE CONDITION DEMO ===\n");

        System.out.println("--- Demo 1: Read-Modify-Write ---");
        UnsafeCounter unsafe = new UnsafeCounter();
        testCounter("Unsafe", unsafe::increment, unsafe::get);
        SafeCounterSync sync = new SafeCounterSync();
        testCounter("synchronized", sync::increment, sync::get);
        SafeCounterAtomic atomic = new SafeCounterAtomic();
        testCounter("AtomicInteger", atomic::increment, atomic::get);

        System.out.println("\n--- Demo 2: Check-Then-Act (lazy singleton) ---");
        Set<Object> unsafeInstances = Collections.synchronizedSet(new HashSet<>());
        runConcurrently(50, () -> unsafeInstances.add(UnsafeSingleton.getInstance()));
        System.out.println("  UnsafeSingleton : " + unsafeInstances.size() + " instance được tạo"
                + (unsafeInstances.size() == 1 ? "  (may mắn - vẫn SAI)" : "  ✗ (phải là 1)"));
        Set<Object> holderInstances = Collections.synchronizedSet(new HashSet<>());
        runConcurrently(50, () -> holderInstances.add(HolderSingleton.getInstance()));
        System.out.println("  HolderSingleton : " + holderInstances.size() + " instance  ✓");

        System.out.println("\n--- Demo 3: Compound operation (rút tiền) ---");
        // 150 thread, mỗi thread rút 10 từ tài khoản 1000 → đúng ra chỉ 100 lần thành công, số dư 0
        UnsafeAccount ua = new UnsafeAccount();
        AtomicInteger unsafeOk = new AtomicInteger();
        runConcurrently(150, () -> { if (ua.withdraw(10)) unsafeOk.incrementAndGet(); });
        System.out.println("  Unsafe : " + unsafeOk.get() + " lần thành công, số dư = " + ua.balance()
                + (ua.balance() == 0 && unsafeOk.get() == 100 ? "  (may mắn)" : "  ✗ (phải là 100 lần, số dư 0)"));

        SafeAccount sa = new SafeAccount();
        AtomicInteger safeOk = new AtomicInteger();
        runConcurrently(150, () -> { if (sa.withdraw(10)) safeOk.incrementAndGet(); });
        System.out.println("  Safe   : " + safeOk.get() + " lần thành công, số dư = " + sa.balance() + "  ✓");

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Race condition là gì? 2 mẫu phổ biến?");
        System.out.println("A: Kết quả phụ thuộc thứ tự xen kẽ của thread. Read-modify-write (count++) và check-then-act.");

        System.out.println("\nQ: Race condition khác data race?");
        System.out.println("A: Data race = truy cập không có happens-before (JMM). Race condition = lỗi logic do timing,");
        System.out.println("   có thể xảy ra dù mọi thao tác riêng lẻ đều thread-safe.");

        System.out.println("\nQ: Cách fix?");
        System.out.println("A: Theo thứ tự ưu tiên: bỏ shared mutable state (immutable, confinement) → thao tác atomic có sẵn");
        System.out.println("   (AtomicInteger, ConcurrentHashMap.merge/computeIfAbsent) → lock bao trọn cả chuỗi check+act.");

        System.out.println("\nQ: Làm sao phát hiện/tái hiện?");
        System.out.println("A: Stress test nhiều thread + start gate (CountDownLatch), chạy lặp nhiều lần; code review tìm");
        System.out.println("   shared mutable state; jcstress cho test JMM nghiêm túc. Không thấy lỗi ≠ code đúng.");

        System.out.println("\nQ: Ví dụ thực tế?");
        System.out.println("A: Đếm metric, cache 'nếu chưa có thì load', kiểm tra tồn kho rồi trừ, cấp ID tăng dần, double-submit.");
        System.out.println("   Lưu ý: nhiều instance service → lock trong JVM vô dụng; cần DB constraint/optimistic lock/distributed lock.");
    }
}
