import java.util.concurrent.*;

/**
 * BA CÁCH TẠO THREAD TRONG JAVA
 * 
 * Interview Question: "Sự khác biệt giữa 3 cách này?"
 * Answer:
 * 1. Thread class: Đơn giản nhưng không thể extend class khác (Java single inheritance)
 * 2. Runnable: Best practice, flexible, có thể implement nhiều interfaces
 * 3. Callable: Giống Runnable nhưng có return value và throw exception
 */
public class ThreadCreationExamples {

    // ============================================
    // CÁCH 1: EXTEND THREAD CLASS
    // ============================================
    /**
     * Ưu điểm: Đơn giản, dễ hiểu
     * Nhược điểm: Không thể extend class khác, tight coupling
     * Use case: Demo, prototype, simple tasks
     */
    static class MyThread extends Thread {
        private String taskName;

        public MyThread(String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void run() {
            System.out.println("[Thread Class] " + taskName + " started by " + Thread.currentThread().getName());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("[Thread Class] " + taskName + " completed");
        }
    }

    // ============================================
    // CÁCH 2: IMPLEMENT RUNNABLE (BEST PRACTICE)
    // ============================================
    /**
     * Ưu điểm: Flexible, có thể extend class khác, reusable
     * Nhược điểm: Verbose hơn một chút
     * Use case: Production code, best practice
     */
    static class MyRunnable implements Runnable {
        private String taskName;

        public MyRunnable(String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void run() {
            System.out.println("[Runnable] " + taskName + " started by " + Thread.currentThread().getName());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("[Runnable] " + taskName + " completed");
        }
    }

    // ============================================
    // CÁCH 3: IMPLEMENT CALLABLE (WITH RETURN VALUE)
    // ============================================
    /**
     * Ưu điểm: Có return value, có thể throw checked exception
     * Nhược điểm: Phải dùng ExecutorService, không thể dùng trực tiếp với Thread
     * Use case: Khi cần kết quả từ thread, async computation
     */
    static class MyCallable implements Callable<String> {
        private String taskName;
        private int computeTime;

        public MyCallable(String taskName, int computeTime) {
            this.taskName = taskName;
            this.computeTime = computeTime;
        }

        @Override
        public String call() throws Exception {
            System.out.println("[Callable] " + taskName + " started by " + Thread.currentThread().getName());
            Thread.sleep(computeTime);
            String result = taskName + " result after " + computeTime + "ms";
            System.out.println("[Callable] " + taskName + " completed");
            return result;
        }
    }

    // ============================================
    // DEMO VÀ SO SÁNH
    // ============================================
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("=== JAVA THREAD CREATION - 3 WAYS ===\n");

        // CÁCH 1: Thread class
        System.out.println("--- Cách 1: Extend Thread ---");
        MyThread thread1 = new MyThread("Task-1");
        thread1.start(); // Gọi start(), KHÔNG gọi run() trực tiếp!
        
        // CÁCH 2: Runnable (Best Practice)
        System.out.println("\n--- Cách 2: Implement Runnable ---");
        Thread thread2 = new Thread(new MyRunnable("Task-2"));
        thread2.start();

        // Runnable với Lambda (Java 8+) - Modern way
        System.out.println("\n--- Cách 2b: Runnable với Lambda ---");
        Thread thread3 = new Thread(() -> {
            System.out.println("[Lambda] Task-3 started by " + Thread.currentThread().getName());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("[Lambda] Task-3 completed");
        });
        thread3.start();

        // CÁCH 3: Callable với ExecutorService
        System.out.println("\n--- Cách 3: Implement Callable ---");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Submit callable tasks
        Future<String> future1 = executor.submit(new MyCallable("Task-4", 1500));
        Future<String> future2 = executor.submit(new MyCallable("Task-5", 2000));

        // Get results (blocking call)
        System.out.println("Waiting for results...");
        String result1 = future1.get(); // Block until done
        String result2 = future2.get();
        
        System.out.println("\nResults:");
        System.out.println("  " + result1);
        System.out.println("  " + result2);

        executor.shutdown();

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Cách nào tốt nhất?");
        System.out.println("A: Runnable (cách 2) - flexible, best practice");
        System.out.println("   Callable (cách 3) - khi cần return value");
        System.out.println("\nQ: Tại sao gọi start() chứ không phải run()?");
        System.out.println("A: start() tạo thread mới và gọi run() trong thread đó");
        System.out.println("   run() chỉ gọi method bình thường trong current thread");
        System.out.println("\nQ: Có thể start() một thread nhiều lần?");
        System.out.println("A: KHÔNG! Sẽ throw IllegalThreadStateException");
    }
}
