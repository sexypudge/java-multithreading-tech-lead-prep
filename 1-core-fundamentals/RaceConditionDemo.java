import java.util.concurrent.atomic.AtomicInteger;

/**
 * RACE CONDITION - KẾT QUẢ PHỤ THUỘC VÀO TIMING
 * 
 * Race condition xảy ra khi:
 * - Multiple threads truy cập shared data
 * - Ít nhất 1 thread modify data
 * - Không có synchronization
 * 
 * 2 loại phổ biến:
 * 1. Check-Then-Act - check condition rồi act based on it
 * 2. Read-Modify-Write - đọc value, modify, write back
 * 
 * Interview Question: "Cho ví dụ race condition trong real project?"
 * Answer: Singleton pattern, counter, bank transfer, cache update...
 */
public class RaceConditionDemo {

    // ============================================
    // PROBLEM 1: READ-MODIFY-WRITE
    // ============================================
    /**
     * count++ KHÔNG phải atomic operation
     * Gồm 3 bước: Read → Modify → Write
     */
    static class UnsafeCounter {
        private int count = 0;

        public void increment() {
            // NOT ATOMIC!
            // Step 1: Read count
            // Step 2: Add 1
            // Step 3: Write back
            count++;
        }

        public int getCount() {
            return count;
        }
    }

    // ============================================
    // SOLUTION 1A: SYNCHRONIZED
    // ============================================
    static class SafeCounterSync {
        private int count = 0;

        public synchronized void increment() {
            count++; // Now atomic
        }

        public synchronized int getCount() {
            return count;
        }
    }

    // ============================================
    // SOLUTION 1B: ATOMIC VARIABLE (BETTER)
    // ============================================
    static class SafeCounterAtomic {
        private AtomicInteger count = new AtomicInteger(0);

        public void increment() {
            count.incrementAndGet(); // Atomic operation
        }

        public int getCount() {
            return count.get();
        }
    }

    // ============================================
    // PROBLEM 2: CHECK-THEN-ACT
    // ============================================
    /**
     * Lazy initialization với race condition
     */
    static class UnsafeSingleton {
        private static UnsafeSingleton instance;

        public static UnsafeSingleton getInstance() {
            if (instance == null) { // Check
                // Race condition here!
                // Multiple threads có thể pass check cùng lúc
                instance = new UnsafeSingleton(); // Act
            }
            return instance;
        }
    }

    // ============================================
    // SOLUTION 2A: SYNCHRONIZED METHOD
    // ============================================
    static class SafeSingletonSync {
        private static SafeSingletonSync instance;

        public static synchronized SafeSingletonSync getInstance() {
            if (instance == null) {
                instance = new SafeSingletonSync();
            }
            return instance;
        }
    }

    // ============================================
    // SOLUTION 2B: DOUBLE-CHECKED LOCKING (BEST)
    // ============================================
    static class SafeSingletonDCL {
        private static volatile SafeSingletonDCL instance; // volatile important!

        public static SafeSingletonDCL getInstance() {
            if (instance == null) { // First check (no lock)
                synchronized (SafeSingletonDCL.class) {
                    if (instance == null) { // Second check (with lock)
                        instance = new SafeSingletonDCL();
                    }
                }
            }
            return instance;
        }
    }

    // ============================================
    // PROBLEM 3: COMPOUND OPERATIONS
    // ============================================
    /**
     * Multiple operations phải atomic together
     */
    static class UnsafeTransfer {
        private int balance = 1000;

        public void transfer(int amount) {
            // Race condition: check và withdraw không atomic
            if (balance >= amount) { // Check
                // Another thread có thể withdraw ở đây!
                balance -= amount; // Act
            }
        }

        public int getBalance() {
            return balance;
        }
    }

    // ============================================
    // SOLUTION 3: SYNCHRONIZED BLOCK
    // ============================================
    static class SafeTransfer {
        private int balance = 1000;

        public void transfer(int amount) {
            synchronized (this) {
                if (balance >= amount) {
                    balance -= amount;
                }
            }
        }

        public synchronized int getBalance() {
            return balance;
        }
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== RACE CONDITION DEMO ===\n");

        final int THREADS = 10;
        final int INCREMENTS = 1000;

        // Demo 1: Read-Modify-Write race condition
        System.out.println("--- Demo 1: Read-Modify-Write Race Condition ---");
        UnsafeCounter unsafeCounter = new UnsafeCounter();
        testCounter(() -> unsafeCounter.increment(), 
                    () -> unsafeCounter.getCount(), 
                    THREADS, INCREMENTS, "Unsafe");

        SafeCounterSync safeSync = new SafeCounterSync();
        testCounter(() -> safeSync.increment(), 
                    () -> safeSync.getCount(), 
                    THREADS, INCREMENTS, "Synchronized");

        SafeCounterAtomic safeAtomic = new SafeCounterAtomic();
        testCounter(() -> safeAtomic.increment(), 
                    () -> safeAtomic.getCount(), 
                    THREADS, INCREMENTS, "Atomic");

        // Demo 2: Check-Then-Act race condition
        System.out.println("\n--- Demo 2: Check-Then-Act (Singleton) ---");
        testSingleton();

        // Demo 3: Compound operations
        System.out.println("\n--- Demo 3: Compound Operations ---");
        UnsafeTransfer unsafeTransfer = new UnsafeTransfer();
        testTransfer(() -> unsafeTransfer.transfer(10), 
                     () -> unsafeTransfer.getBalance(), 
                     "Unsafe");

        SafeTransfer safeTransfer = new SafeTransfer();
        testTransfer(() -> safeTransfer.transfer(10), 
                     () -> safeTransfer.getBalance(), 
                     "Safe");

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Race condition là gì?");
        System.out.println("A: Kết quả phụ thuộc vào timing/ordering của threads");
        System.out.println("   Không deterministic, khó reproduce");
        
        System.out.println("\nQ: 2 loại race condition phổ biến?");
        System.out.println("A: 1. Read-Modify-Write (count++)");
        System.out.println("   2. Check-Then-Act (if-then pattern)");
        
        System.out.println("\nQ: Cách fix race condition?");
        System.out.println("A: 1. synchronized - simple, works for all");
        System.out.println("   2. Atomic variables - better performance cho counters");
        System.out.println("   3. Concurrent collections - built-in thread-safety");
        System.out.println("   4. Immutable objects - no shared mutable state");
        
        System.out.println("\nQ: Làm sao detect race condition?");
        System.out.println("A: - Stress testing với nhiều threads");
        System.out.println("   - Thread sanitizers / race detectors");
        System.out.println("   - Code review - tìm shared mutable state");
        System.out.println("   - Production bugs - inconsistent results");
        
        System.out.println("\nQ: Real-world examples?");
        System.out.println("A: - Singleton initialization");
        System.out.println("   - Counter/statistics");
        System.out.println("   - Cache updates");
        System.out.println("   - Bank transfers");
        System.out.println("   - Session management");

        System.out.println("\n=== PREVENTION CHECKLIST ===");
        System.out.println("□ Identify shared mutable state");
        System.out.println("□ Minimize shared state (prefer immutable)");
        System.out.println("□ Synchronize access to shared state");
        System.out.println("□ Use thread-safe collections");
        System.out.println("□ Prefer atomic variables for counters");
        System.out.println("□ Document thread-safety guarantees");
    }

    // Helper methods
    private static void testCounter(Runnable incrementTask, 
                                    java.util.function.Supplier<Integer> getCount,
                                    int threads, int increments, String name) throws InterruptedException {
        Thread[] threadArray = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            threadArray[i] = new Thread(() -> {
                for (int j = 0; j < increments; j++) {
                    incrementTask.run();
                }
            });
            threadArray[i].start();
        }

        for (Thread t : threadArray) {
            t.join();
        }

        int expected = threads * increments;
        int actual = getCount.get();
        
        System.out.println(name + " - Expected: " + expected + ", Actual: " + actual + 
                          " " + (expected == actual ? "✓" : "✗"));
    }

    private static void testSingleton() throws InterruptedException {
        final int THREADS = 100;
        Thread[] threads = new Thread[THREADS];
        final java.util.Set<UnsafeSingleton> instances = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(() -> {
                instances.add(UnsafeSingleton.getInstance());
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("Unsafe Singleton - Instances created: " + instances.size() + 
                          (instances.size() == 1 ? " ✓" : " ✗ (should be 1)"));
    }

    private static void testTransfer(Runnable transferTask, 
                                     java.util.function.Supplier<Integer> getBalance,
                                     String name) throws InterruptedException {
        Thread[] threads = new Thread[100];

        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(transferTask);
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        int balance = getBalance.get();
        System.out.println(name + " Transfer - Final balance: " + balance + 
                          (balance == 0 ? " ✓" : " ✗ (should be 0)"));
    }
}
