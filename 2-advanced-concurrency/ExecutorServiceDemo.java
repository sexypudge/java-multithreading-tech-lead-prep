import java.util.concurrent.*;
import java.util.*;

/**
 * EXECUTOR SERVICE - THREAD POOL MANAGEMENT
 * 
 * Tại sao dùng ExecutorService thay vì tạo Thread thủ công?
 * ✓ Thread reuse → giảm overhead
 * ✓ Resource management → control số threads
 * ✓ Task queue → handle load spikes
 * ✓ Easier to tune performance
 * 
 * Interview Question: "Làm sao tune thread pool size?"
 * Answer: 
 * - CPU-bound: cores + 1
 * - I/O-bound: cores * (1 + wait_time/compute_time)
 * - Monitor và adjust based on metrics
 */
public class ExecutorServiceDemo {

    // ============================================
    // 1. FIXED THREAD POOL
    // ============================================
    /**
     * Fixed số threads, task queue unbounded
     * Use case: Known workload, stable load
     */
    static void fixedThreadPoolDemo() {
        System.out.println("--- Fixed Thread Pool ---");
        ExecutorService executor = Executors.newFixedThreadPool(3);

        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("  Task " + taskId + " by " + Thread.currentThread().getName());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        shutdownExecutor(executor);
    }

    // ============================================
    // 2. CACHED THREAD POOL
    // ============================================
    /**
     * Tạo threads on-demand, reuse idle threads
     * Use case: Many short-lived tasks, unpredictable load
     */
    static void cachedThreadPoolDemo() {
        System.out.println("\n--- Cached Thread Pool ---");
        ExecutorService executor = Executors.newCachedThreadPool();

        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("  Task " + taskId + " by " + Thread.currentThread().getName());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        shutdownExecutor(executor);
    }

    // ============================================
    // 3. SINGLE THREAD EXECUTOR
    // ============================================
    /**
     * Chỉ 1 thread, tasks execute sequentially
     * Use case: Tasks phải execute theo thứ tự
     */
    static void singleThreadExecutorDemo() {
        System.out.println("\n--- Single Thread Executor ---");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("  Task " + taskId + " executing sequentially");
            });
        }

        shutdownExecutor(executor);
    }

    // ============================================
    // 4. SCHEDULED THREAD POOL
    // ============================================
    /**
     * Schedule tasks với delay hoặc periodic
     * Use case: Cron jobs, periodic tasks
     */
    static void scheduledThreadPoolDemo() throws InterruptedException {
        System.out.println("\n--- Scheduled Thread Pool ---");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

        // Schedule once với delay
        executor.schedule(() -> {
            System.out.println("  Task executed after 1 second delay");
        }, 1, TimeUnit.SECONDS);

        // Schedule periodic (fixed rate)
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            System.out.println("  Periodic task at " + System.currentTimeMillis());
        }, 0, 2, TimeUnit.SECONDS);

        // Let it run for 5 seconds
        Thread.sleep(5000);
        future.cancel(false);

        shutdownExecutor(executor);
    }

    // ============================================
    // 5. SUBMIT VS EXECUTE
    // ============================================
    /**
     * execute() - fire and forget, no return value
     * submit() - returns Future, can get result/exception
     */
    static void submitVsExecuteDemo() throws ExecutionException, InterruptedException {
        System.out.println("\n--- Submit vs Execute ---");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // execute() - no return value
        executor.execute(() -> {
            System.out.println("  execute() - fire and forget");
        });

        // submit() - returns Future
        Future<String> future = executor.submit(() -> {
            Thread.sleep(1000);
            return "Result from submit()";
        });

        System.out.println("  Waiting for result...");
        String result = future.get(); // Blocking call
        System.out.println("  " + result);

        shutdownExecutor(executor);
    }

    // ============================================
    // 6. INVOKE ALL - BATCH EXECUTION
    // ============================================
    /**
     * Execute multiple tasks và wait for all
     */
    static void invokeAllDemo() throws InterruptedException, ExecutionException {
        System.out.println("\n--- invokeAll() Demo ---");
        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<String>> tasks = Arrays.asList(
            () -> { Thread.sleep(1000); return "Task 1"; },
            () -> { Thread.sleep(2000); return "Task 2"; },
            () -> { Thread.sleep(1500); return "Task 3"; }
        );

        System.out.println("  Executing all tasks...");
        List<Future<String>> futures = executor.invokeAll(tasks);

        for (Future<String> future : futures) {
            System.out.println("  Result: " + future.get());
        }

        shutdownExecutor(executor);
    }

    // ============================================
    // 7. CUSTOM THREAD POOL
    // ============================================
    /**
     * Fine-grained control với ThreadPoolExecutor
     */
    static void customThreadPoolDemo() {
        System.out.println("\n--- Custom Thread Pool ---");
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,                      // corePoolSize
            4,                      // maximumPoolSize
            60L,                    // keepAliveTime
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10), // workQueue
            new ThreadPoolExecutor.CallerRunsPolicy() // rejectionPolicy
        );

        // Monitor pool
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("  Task " + taskId + " - Active: " + executor.getActiveCount() + 
                                 ", Pool size: " + executor.getPoolSize());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        shutdownExecutor(executor);
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws Exception {
        System.out.println("=== EXECUTOR SERVICE DEMO ===\n");

        fixedThreadPoolDemo();
        cachedThreadPoolDemo();
        singleThreadExecutorDemo();
        scheduledThreadPoolDemo();
        submitVsExecuteDemo();
        invokeAllDemo();
        customThreadPoolDemo();

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Làm sao tune thread pool size?");
        System.out.println("A: CPU-bound: cores + 1");
        System.out.println("   I/O-bound: cores * (1 + wait/compute)");
        System.out.println("   Example: 4 cores, 90% I/O → 4 * (1 + 9) = 40 threads");
        
        System.out.println("\nQ: Fixed vs Cached thread pool?");
        System.out.println("A: Fixed - stable load, bounded threads");
        System.out.println("   Cached - unpredictable load, many short tasks");
        
        System.out.println("\nQ: submit() vs execute()?");
        System.out.println("A: submit() - returns Future, can get result/exception");
        System.out.println("   execute() - fire and forget, no return");
        
        System.out.println("\nQ: Shutdown strategies?");
        System.out.println("A: shutdown() - graceful, wait for tasks");
        System.out.println("   shutdownNow() - interrupt running tasks");
        System.out.println("   awaitTermination() - wait with timeout");
        
        System.out.println("\nQ: Rejection policies?");
        System.out.println("A: AbortPolicy - throw exception (default)");
        System.out.println("   CallerRunsPolicy - run in caller thread");
        System.out.println("   DiscardPolicy - silently discard");
        System.out.println("   DiscardOldestPolicy - discard oldest in queue");

        System.out.println("\n=== THREAD POOL SIZING FORMULA ===");
        System.out.println("CPU-bound: threads = cores + 1");
        System.out.println("I/O-bound: threads = cores * (1 + WT/CT)");
        System.out.println("  WT = wait time");
        System.out.println("  CT = compute time");
        System.out.println("\nAlways monitor and adjust!");
    }

    // Helper method
    private static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
