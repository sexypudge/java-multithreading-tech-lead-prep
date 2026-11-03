/**
 * VOLATILE KEYWORD - VISIBILITY GUARANTEE
 * 
 * Problem: CPU cache → thread không thấy changes từ thread khác
 * Solution: volatile → force read/write từ main memory
 * 
 * volatile ĐẢM BẢO:
 * ✓ Visibility - changes visible to all threads
 * ✓ Happens-before relationship
 * 
 * volatile KHÔNG ĐẢM BẢO:
 * ✗ Atomicity - count++ vẫn không thread-safe
 * 
 * Interview Question: "volatile vs synchronized?"
 * Answer: volatile - chỉ visibility, lightweight
 *         synchronized - cả visibility và atomicity, heavier
 */
public class VolatileDemo {

    // ============================================
    // PROBLEM: WITHOUT VOLATILE
    // ============================================
    /**
     * Thread có thể cache variable trong CPU cache
     * Changes không visible cho threads khác
     */
    static class WithoutVolatile {
        private boolean running = true; // NO volatile

        public void start() {
            new Thread(() -> {
                System.out.println("  [Worker] Started, waiting for stop signal...");
                int count = 0;
                // Thread có thể cache 'running' → infinite loop
                while (running) {
                    count++;
                }
                System.out.println("  [Worker] Stopped after " + count + " iterations");
            }, "Worker").start();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("  [Main] Sending stop signal...");
            running = false; // Change có thể KHÔNG visible cho Worker thread
            System.out.println("  [Main] Stop signal sent (may not work without volatile!)");
        }
    }

    // ============================================
    // SOLUTION: WITH VOLATILE
    // ============================================
    /**
     * volatile đảm bảo changes visible ngay lập tức
     */
    static class WithVolatile {
        private volatile boolean running = true; // WITH volatile

        public void start() {
            new Thread(() -> {
                System.out.println("  [Worker] Started, waiting for stop signal...");
                int count = 0;
                // volatile → always read from main memory
                while (running) {
                    count++;
                }
                System.out.println("  [Worker] Stopped after " + count + " iterations");
            }, "Worker").start();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("  [Main] Sending stop signal...");
            running = false; // Change visible ngay lập tức
            System.out.println("  [Main] Stop signal sent (works with volatile!)");
        }
    }

    // ============================================
    // VOLATILE KHÔNG ĐẢM BẢO ATOMICITY
    // ============================================
    /**
     * volatile KHÔNG giải quyết race condition cho compound operations
     */
    static class VolatileNotAtomic {
        private volatile int count = 0; // volatile nhưng vẫn không thread-safe

        public void increment() {
            count++; // NOT atomic! Read-Modify-Write
            // Equivalent to:
            // int temp = count;  // Read
            // temp = temp + 1;   // Modify
            // count = temp;      // Write
        }

        public int getCount() {
            return count;
        }
    }

    // ============================================
    // KHI NÀO DÙNG VOLATILE
    // ============================================
    /**
     * Use cases phù hợp cho volatile:
     * 1. Status flags (boolean)
     * 2. One writer, multiple readers
     * 3. Independent variables (không phụ thuộc giá trị cũ)
     */
    static class VolatileUseCases {
        // ✓ GOOD: Status flag
        private volatile boolean initialized = false;
        
        // ✓ GOOD: One writer, multiple readers
        private volatile int latestValue = 0;
        
        // ✗ BAD: Compound operation (cần synchronized hoặc AtomicInteger)
        private volatile int counter = 0;

        public void initialize() {
            // Do initialization
            initialized = true; // Simple write - OK
        }

        public boolean isInitialized() {
            return initialized; // Simple read - OK
        }

        public void updateValue(int value) {
            latestValue = value; // Simple write - OK
        }

        public int getLatestValue() {
            return latestValue; // Simple read - OK
        }

        public void incrementCounter() {
            counter++; // Compound operation - NOT SAFE!
        }
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== VOLATILE KEYWORD DEMO ===\n");

        // Demo 1: Without volatile (may hang)
        System.out.println("--- Demo 1: WITHOUT volatile ---");
        System.out.println("(May not stop - visibility issue)");
        WithoutVolatile demo1 = new WithoutVolatile();
        demo1.start();
        Thread.sleep(3000);

        // Demo 2: With volatile (works correctly)
        System.out.println("\n--- Demo 2: WITH volatile ---");
        System.out.println("(Should stop correctly)");
        WithVolatile demo2 = new WithVolatile();
        demo2.start();
        Thread.sleep(3000);

        // Demo 3: volatile không đảm bảo atomicity
        System.out.println("\n--- Demo 3: volatile NOT atomic ---");
        VolatileNotAtomic demo3 = new VolatileNotAtomic();
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    demo3.increment();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("Expected: 10000");
        System.out.println("Actual:   " + demo3.getCount());
        System.out.println("Result:   " + (demo3.getCount() == 10000 ? "✓" : "✗ Race condition!"));

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: volatile vs synchronized?");
        System.out.println("A: volatile - chỉ visibility, không lock, lightweight");
        System.out.println("   synchronized - visibility + atomicity, có lock, heavier");
        
        System.out.println("\nQ: Khi nào dùng volatile?");
        System.out.println("A: ✓ Status flags (boolean)");
        System.out.println("   ✓ One writer, multiple readers");
        System.out.println("   ✓ Simple read/write (không phụ thuộc giá trị cũ)");
        System.out.println("   ✗ Compound operations (count++, check-then-act)");
        
        System.out.println("\nQ: volatile có đảm bảo atomicity?");
        System.out.println("A: KHÔNG! Chỉ đảm bảo visibility");
        System.out.println("   count++ vẫn cần synchronized hoặc AtomicInteger");
        
        System.out.println("\nQ: Performance của volatile?");
        System.out.println("A: Nhanh hơn synchronized (no lock)");
        System.out.println("   Nhưng chậm hơn normal variable (memory barrier)");
        
        System.out.println("\nQ: Happens-before relationship?");
        System.out.println("A: Write to volatile → happens-before → Read from volatile");
        System.out.println("   Đảm bảo ordering, prevent reordering");

        System.out.println("\n=== DECISION TREE ===");
        System.out.println("Need thread-safety?");
        System.out.println("├─ Simple read/write? → volatile");
        System.out.println("├─ Compound operation? → synchronized / Lock");
        System.out.println("├─ Numeric counter? → AtomicInteger");
        System.out.println("└─ Complex state? → synchronized / Lock");
    }
}
