import java.util.concurrent.*;

/**
 * CÁC CÁCH TẠO THREAD / TASK TRONG JAVA
 *
 * Interview Question: "Sự khác biệt giữa Thread, Runnable, Callable?"
 * Answer:
 * 1. extends Thread : trộn "việc cần làm" với "cơ chế chạy"; không extend class khác được (single inheritance)
 * 2. Runnable       : chỉ mô tả TASK (void run()), tách khỏi thread → tái sử dụng, đưa vào executor được
 * 3. Callable<V>    : như Runnable nhưng TRẢ KẾT QUẢ và được ném checked exception (V call() throws Exception)
 *
 * Ý quan trọng ở level Senior/Tech Lead:
 *   Trong code production gần như KHÔNG tự new Thread(). Ta định nghĩa task (Runnable/Callable)
 *   rồi giao cho ExecutorService (giới hạn tài nguyên, đặt tên thread, shutdown có kiểm soát).
 *   Java 21+: với việc I/O-bound có thể dùng virtual thread (xem 2-advanced-concurrency/VirtualThreadsDemo).
 */
public class ThreadCreationExamples {

    // ============================================
    // CÁCH 1: EXTEND THREAD CLASS
    // ============================================
    /** Nhược điểm: task gắn chặt với Thread, không đưa vào executor một cách tự nhiên được. */
    static class MyThread extends Thread {
        private final String taskName;

        MyThread(String taskName) {
            super("MyThread-" + taskName);
            this.taskName = taskName;
        }

        @Override
        public void run() {
            System.out.println("  [extends Thread] " + taskName + " chạy trên " + Thread.currentThread().getName());
        }
    }

    // ============================================
    // CÁCH 2: IMPLEMENT RUNNABLE (task không trả kết quả)
    // ============================================
    static class MyRunnable implements Runnable {
        private final String taskName;

        MyRunnable(String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void run() {
            System.out.println("  [Runnable] " + taskName + " chạy trên " + Thread.currentThread().getName());
        }
    }

    // ============================================
    // CÁCH 3: IMPLEMENT CALLABLE (task có kết quả)
    // ============================================
    static class MyCallable implements Callable<String> {
        private final String taskName;
        private final int computeMs;

        MyCallable(String taskName, int computeMs) {
            this.taskName = taskName;
            this.computeMs = computeMs;
        }

        @Override
        public String call() throws Exception {      // được phép ném checked exception
            Thread.sleep(computeMs);
            return taskName + " xong sau " + computeMs + "ms trên " + Thread.currentThread().getName();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== JAVA THREAD CREATION ===\n");

        System.out.println("--- Cách 1: extends Thread ---");
        MyThread t1 = new MyThread("Task-1");
        t1.start();
        t1.join();

        System.out.println("\n--- Cách 2: Runnable (class và lambda) ---");
        Thread t2 = new Thread(new MyRunnable("Task-2"), "Worker-2");
        Thread t3 = new Thread(() -> System.out.println("  [Lambda] Task-3 chạy trên " + Thread.currentThread().getName()),
                "Worker-3");
        t2.start();
        t3.start();
        t2.join();
        t3.join();

        System.out.println("\n--- Cách 3: Callable + ExecutorService ---");
        // ExecutorService là AutoCloseable từ Java 19: close() = shutdown() + chờ task xong
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> f1 = executor.submit(new MyCallable("Task-4", 300));
            Future<String> f2 = executor.submit(new MyCallable("Task-5", 500));
            System.out.println("  main không bị chặn khi submit; get() mới chặn:");
            System.out.println("  " + f1.get(2, TimeUnit.SECONDS));   // production: luôn get có timeout
            System.out.println("  " + f2.get(2, TimeUnit.SECONDS));
        }

        // ============================================
        // BẪY 1: gọi run() thay vì start()
        // ============================================
        System.out.println("\n--- Bẫy 1: run() KHÔNG tạo thread mới ---");
        Thread t4 = new Thread(() -> System.out.println("  run() thực thi trên: " + Thread.currentThread().getName()),
                "Should-Be-New-Thread");
        t4.run();                                       // chạy trên chính thread 'main'
        t4.start();                                     // chạy trên 'Should-Be-New-Thread'
        t4.join();

        // ============================================
        // BẪY 2: start() hai lần
        // ============================================
        System.out.println("\n--- Bẫy 2: start() hai lần ---");
        try {
            t4.start();                                 // t4 đã TERMINATED
        } catch (IllegalThreadStateException e) {
            System.out.println("  ✓ IllegalThreadStateException: một Thread object chỉ start() được một lần");
        }

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Nên dùng cách nào?");
        System.out.println("A: Định nghĩa task bằng Runnable/Callable và giao cho ExecutorService.");
        System.out.println("   'Thread hay Runnable' thực chất là câu hỏi về tách TASK khỏi CƠ CHẾ CHẠY.");

        System.out.println("\nQ: start() vs run()?");
        System.out.println("A: start() yêu cầu JVM tạo thread mới rồi gọi run() trên thread đó.");
        System.out.println("   Gọi run() trực tiếp chỉ là gọi method bình thường trên thread hiện tại.");

        System.out.println("\nQ: Runnable vs Callable?");
        System.out.println("A: Callable trả về kết quả (qua Future) và được ném checked exception; Runnable thì không.");

        System.out.println("\nQ: Vì sao production không nên new Thread() tuỳ tiện?");
        System.out.println("A: Mỗi platform thread tốn ~1MB stack + syscall; không giới hạn được số lượng; khó đặt tên,");
        System.out.println("   khó shutdown, exception dễ bị mất. Executor giải quyết các vấn đề đó.");
    }
}
