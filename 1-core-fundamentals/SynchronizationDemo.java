/**
 * SYNCHRONIZATION TRONG JAVA
 * 
 * Synchronization đảm bảo chỉ 1 thread truy cập critical section tại một thời điểm
 * 
 * 3 loại synchronization:
 * 1. Synchronized Method - lock trên instance (this)
 * 2. Synchronized Block - lock trên object cụ thể
 * 3. Static Synchronized - lock trên Class object
 * 
 * Interview Question: "synchronized method vs synchronized block?"
 * Answer: Block cho phép fine-grained control, minimize lock scope, better performance
 */
public class SynchronizationDemo {

    // ============================================
    // PROBLEM: RACE CONDITION WITHOUT SYNC
    // ============================================
    static class UnsafeCounter {
        private int count = 0;

        public void increment() {
            count++; // NOT atomic! Read-Modify-Write
        }

        public int getCount() {
            return count;
        }
    }

    // ============================================
    // SOLUTION 1: SYNCHRONIZED METHOD
    // ============================================
    /**
     * Lock trên instance (this)
     * Toàn bộ method được protect
     */
    static class SafeCounterMethod {
        private int count = 0;

        // synchronized method - lock on 'this'
        public synchronized void increment() {
            count++;
        }

        public synchronized int getCount() {
            return count;
        }
    }

    // ============================================
    // SOLUTION 2: SYNCHRONIZED BLOCK (BETTER)
    // ============================================
    /**
     * Lock trên object cụ thể
     * Chỉ critical section được protect
     * Better performance
     */
    static class SafeCounterBlock {
        private int count = 0;
        private final Object lock = new Object(); // Dedicated lock object

        public void increment() {
            // Non-critical code here (không cần lock)
            
            synchronized (lock) { // Only lock critical section
                count++;
            }
            
            // More non-critical code
        }

        public int getCount() {
            synchronized (lock) {
                return count;
            }
        }
    }

    // ============================================
    // SOLUTION 3: STATIC SYNCHRONIZED
    // ============================================
    /**
     * Lock trên Class object
     * Dùng cho static methods/fields
     */
    static class StaticCounter {
        private static int count = 0;

        // Lock on StaticCounter.class
        public static synchronized void increment() {
            count++;
        }

        public static synchronized int getCount() {
            return count;
        }
    }

    // ============================================
    // DEMO: SO SÁNH UNSAFE VS SAFE
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== SYNCHRONIZATION DEMO ===\n");

        final int THREADS = 10;
        final int INCREMENTS = 1000;

        // Test 1: Unsafe Counter (Race Condition)
        System.out.println("--- Test 1: WITHOUT Synchronization ---");
        UnsafeCounter unsafeCounter = new UnsafeCounter();
        testCounter(() -> unsafeCounter.increment(), 
                    () -> unsafeCounter.getCount(), 
                    THREADS, INCREMENTS);

        // Test 2: Synchronized Method
        System.out.println("\n--- Test 2: Synchronized Method ---");
        SafeCounterMethod safeMethod = new SafeCounterMethod();
        testCounter(() -> safeMethod.increment(), 
                    () -> safeMethod.getCount(), 
                    THREADS, INCREMENTS);

        // Test 3: Synchronized Block
        System.out.println("\n--- Test 3: Synchronized Block ---");
        SafeCounterBlock safeBlock = new SafeCounterBlock();
        testCounter(() -> safeBlock.increment(), 
                    () -> safeBlock.getCount(), 
                    THREADS, INCREMENTS);

        // Test 4: Static Synchronized
        System.out.println("\n--- Test 4: Static Synchronized ---");
        testCounter(() -> StaticCounter.increment(), 
                    () -> StaticCounter.getCount(), 
                    THREADS, INCREMENTS);

        // ============================================
        // DEMO: REENTRANT LOCK
        // ============================================
        System.out.println("\n--- Reentrant Lock Demo ---");
        ReentrantExample reentrant = new ReentrantExample();
        reentrant.outerMethod();

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: synchronized method vs block?");
        System.out.println("A: Block tốt hơn - minimize lock scope, better performance");
        System.out.println("   Method lock toàn bộ, block chỉ lock critical section");
        
        System.out.println("\nQ: synchronized có reentrant không?");
        System.out.println("A: CÓ! Thread đã hold lock có thể acquire lại");
        
        System.out.println("\nQ: synchronized trên null object?");
        System.out.println("A: NullPointerException!");
        
        System.out.println("\nQ: Performance impact?");
        System.out.println("A: Lock contention → context switching → slower");
        System.out.println("   Minimize lock scope, use concurrent collections");
        
        System.out.println("\nQ: Alternatives to synchronized?");
        System.out.println("A: ReentrantLock, ReadWriteLock, StampedLock");
        System.out.println("   Atomic variables, Concurrent collections");
    }

    /**
     * Helper method để test counter
     */
    private static void testCounter(Runnable incrementTask, 
                                    java.util.function.Supplier<Integer> getCount,
                                    int threads, int increments) throws InterruptedException {
        Thread[] threadArray = new Thread[threads];

        long startTime = System.currentTimeMillis();

        // Create and start threads
        for (int i = 0; i < threads; i++) {
            threadArray[i] = new Thread(() -> {
                for (int j = 0; j < increments; j++) {
                    incrementTask.run();
                }
            });
            threadArray[i].start();
        }

        // Wait for all threads
        for (Thread t : threadArray) {
            t.join();
        }

        long endTime = System.currentTimeMillis();

        int expected = threads * increments;
        int actual = getCount.get();
        
        System.out.println("Expected: " + expected);
        System.out.println("Actual:   " + actual);
        System.out.println("Time:     " + (endTime - startTime) + "ms");
        System.out.println("Result:   " + (expected == actual ? "✓ CORRECT" : "✗ RACE CONDITION!"));
    }

    /**
     * Demo Reentrant Lock
     */
    static class ReentrantExample {
        public synchronized void outerMethod() {
            System.out.println("  In outerMethod");
            innerMethod(); // Thread đã hold lock, có thể gọi synchronized method khác
        }

        public synchronized void innerMethod() {
            System.out.println("  In innerMethod - Reentrant lock works!");
        }
    }
}
