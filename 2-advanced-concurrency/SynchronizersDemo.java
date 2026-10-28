import java.util.concurrent.*;

/**
 * SYNCHRONIZERS - HIGH-LEVEL COORDINATION
 * 
 * CountDownLatch - Wait for events
 * CyclicBarrier - Wait for each other
 * Semaphore - Limit concurrent access
 * Phaser - Flexible barrier
 * 
 * Interview Question: "CountDownLatch vs CyclicBarrier?"
 * Answer: CountDownLatch - one-time, threads wait for events
 *         CyclicBarrier - reusable, threads wait for each other
 */
public class SynchronizersDemo {

    // ============================================
    // 1. COUNTDOWNLATCH
    // ============================================
    static void countDownLatchDemo() throws InterruptedException {
        System.out.println("--- CountDownLatch ---");
        
        CountDownLatch latch = new CountDownLatch(3);
        
        // Workers
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                System.out.println("  Worker " + id + " working...");
                try {
                    Thread.sleep(1000 * id);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("  Worker " + id + " done");
                latch.countDown();
            }).start();
        }
        
        System.out.println("  Main waiting for workers...");
        latch.await(); // Block until count = 0
        System.out.println("  All workers done!");
    }

    // ============================================
    // 2. CYCLICBARRIER
    // ============================================
    static void cyclicBarrierDemo() throws InterruptedException {
        System.out.println("\n--- CyclicBarrier ---");
        
        CyclicBarrier barrier = new CyclicBarrier(3, () -> {
            System.out.println("  *** All threads reached barrier! ***");
        });
        
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    System.out.println("  Thread " + id + " working...");
                    Thread.sleep(1000 * id);
                    System.out.println("  Thread " + id + " waiting at barrier");
                    barrier.await(); // Wait for others
                    System.out.println("  Thread " + id + " continuing...");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        
        Thread.sleep(5000);
    }

    // ============================================
    // 3. SEMAPHORE
    // ============================================
    static void semaphoreDemo() throws InterruptedException {
        System.out.println("\n--- Semaphore ---");
        
        Semaphore semaphore = new Semaphore(2); // Max 2 concurrent
        
        for (int i = 1; i <= 5; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    System.out.println("  Thread " + id + " waiting for permit...");
                    semaphore.acquire();
                    System.out.println("  Thread " + id + " got permit, working...");
                    Thread.sleep(2000);
                    System.out.println("  Thread " + id + " releasing permit");
                    semaphore.release();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        
        Thread.sleep(12000);
    }

    // ============================================
    // 4. PHASER
    // ============================================
    static void phaserDemo() throws InterruptedException {
        System.out.println("\n--- Phaser ---");
        
        Phaser phaser = new Phaser(1); // Register main
        
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            phaser.register(); // Register thread
            new Thread(() -> {
                System.out.println("  Thread " + id + " phase 1");
                phaser.arriveAndAwaitAdvance(); // Wait for phase 1
                
                System.out.println("  Thread " + id + " phase 2");
                phaser.arriveAndAwaitAdvance(); // Wait for phase 2
                
                System.out.println("  Thread " + id + " done");
                phaser.arriveAndDeregister();
            }).start();
        }
        
        Thread.sleep(1000);
        System.out.println("  Main advancing phase 1");
        phaser.arriveAndAwaitAdvance();
        
        Thread.sleep(1000);
        System.out.println("  Main advancing phase 2");
        phaser.arriveAndAwaitAdvance();
        
        phaser.arriveAndDeregister();
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== SYNCHRONIZERS DEMO ===\n");
        
        countDownLatchDemo();
        cyclicBarrierDemo();
        semaphoreDemo();
        phaserDemo();
        
        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: CountDownLatch vs CyclicBarrier?");
        System.out.println("A: CountDownLatch - one-time, wait for events");
        System.out.println("   CyclicBarrier - reusable, threads wait for each other");
        
        System.out.println("\nQ: Semaphore use cases?");
        System.out.println("A: - Limit concurrent access (connection pool)");
        System.out.println("   - Resource throttling");
        System.out.println("   - Rate limiting");
        
        System.out.println("\nQ: Phaser vs CyclicBarrier?");
        System.out.println("A: Phaser - dynamic parties, multiple phases");
        System.out.println("   CyclicBarrier - fixed parties, simpler");
        
        System.out.println("\n=== USE CASES ===");
        System.out.println("CountDownLatch: Service startup, batch processing");
        System.out.println("CyclicBarrier: Parallel algorithms, simulations");
        System.out.println("Semaphore: Connection pools, rate limiting");
        System.out.println("Phaser: Multi-phase algorithms, dynamic parties");
    }
}
