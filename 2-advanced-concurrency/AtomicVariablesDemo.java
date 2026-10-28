import java.util.concurrent.atomic.*;

/**
 * ATOMIC VARIABLES - LOCK-FREE PROGRAMMING
 * 
 * Compare-And-Swap (CAS) operations:
 * ✓ No locks → no deadlock
 * ✓ Better performance under low contention
 * ✓ Non-blocking algorithms
 * 
 * Interview Question: "Atomic vs synchronized?"
 * Answer: Atomic - lock-free, CAS-based, better for simple operations
 *         synchronized - lock-based, better for complex operations
 */
public class AtomicVariablesDemo {

    // ============================================
    // 1. ATOMICINTEGER
    // ============================================
    static void atomicIntegerDemo() throws InterruptedException {
        System.out.println("--- AtomicInteger ---");
        
        AtomicInteger counter = new AtomicInteger(0);
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    counter.incrementAndGet(); // Atomic
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        System.out.println("  Counter: " + counter.get()); // Always 10000
        
        // Other operations
        counter.set(100);
        System.out.println("  addAndGet: " + counter.addAndGet(50)); // 150
        System.out.println("  compareAndSet: " + counter.compareAndSet(150, 200)); // true
        System.out.println("  Value: " + counter.get()); // 200
    }

    // ============================================
    // 2. ATOMICREFERENCE
    // ============================================
    static void atomicReferenceDemo() {
        System.out.println("\n--- AtomicReference ---");
        
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        
        System.out.println("  Value: " + ref.get());
        
        // CAS operation
        boolean updated = ref.compareAndSet("Initial", "Updated");
        System.out.println("  Updated: " + updated); // true
        System.out.println("  Value: " + ref.get()); // Updated
        
        // Update with function
        ref.updateAndGet(v -> v + " Again");
        System.out.println("  Value: " + ref.get()); // Updated Again
    }

    // ============================================
    // 3. ATOMICLONG - HIGH CONTENTION
    // ============================================
    static void atomicLongDemo() throws InterruptedException {
        System.out.println("\n--- AtomicLong vs LongAdder ---");
        
        // AtomicLong
        AtomicLong atomicLong = new AtomicLong(0);
        long start1 = System.currentTimeMillis();
        
        Thread[] threads1 = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads1[i] = new Thread(() -> {
                for (int j = 0; j < 100000; j++) {
                    atomicLong.incrementAndGet();
                }
            });
            threads1[i].start();
        }
        for (Thread t : threads1) t.join();
        
        System.out.println("  AtomicLong: " + atomicLong.get() + 
                          " in " + (System.currentTimeMillis() - start1) + "ms");
        
        // LongAdder - better for high contention
        LongAdder adder = new LongAdder();
        long start2 = System.currentTimeMillis();
        
        Thread[] threads2 = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads2[i] = new Thread(() -> {
                for (int j = 0; j < 100000; j++) {
                    adder.increment();
                }
            });
            threads2[i].start();
        }
        for (Thread t : threads2) t.join();
        
        System.out.println("  LongAdder: " + adder.sum() + 
                          " in " + (System.currentTimeMillis() - start2) + "ms");
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== ATOMIC VARIABLES DEMO ===\n");
        
        atomicIntegerDemo();
        atomicReferenceDemo();
        atomicLongDemo();
        
        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Atomic vs synchronized?");
        System.out.println("A: Atomic - lock-free, CAS, simple operations");
        System.out.println("   synchronized - lock-based, complex operations");
        
        System.out.println("\nQ: AtomicLong vs LongAdder?");
        System.out.println("A: AtomicLong - low contention");
        System.out.println("   LongAdder - high contention, better performance");
        
        System.out.println("\nQ: CAS operation?");
        System.out.println("A: Compare-And-Swap - atomic CPU instruction");
        System.out.println("   compareAndSet(expected, new) - update if match");
        
        System.out.println("\n=== USE CASES ===");
        System.out.println("AtomicInteger: Counters, IDs");
        System.out.println("AtomicReference: Lock-free data structures");
        System.out.println("LongAdder: High-contention counters");
    }
}
