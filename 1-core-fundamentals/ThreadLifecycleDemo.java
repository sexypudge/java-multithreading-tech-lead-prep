/**
 * THREAD LIFECYCLE & STATES
 * 
 * Thread States trong Java:
 * 1. NEW - Thread được tạo nhưng chưa start()
 * 2. RUNNABLE - Thread đang chạy hoặc sẵn sàng chạy
 * 3. BLOCKED - Thread đang chờ monitor lock
 * 4. WAITING - Thread đang chờ vô thời hạn (wait, join)
 * 5. TIMED_WAITING - Thread đang chờ có thời hạn (sleep, wait with timeout)
 * 6. TERMINATED - Thread đã hoàn thành
 * 
 * Interview Question: "Thread ở state nào khi gọi sleep()?"
 * Answer: TIMED_WAITING
 */
public class ThreadLifecycleDemo {

    /**
     * Helper method để print thread state
     */
    private static void printThreadState(Thread thread, String action) {
        System.out.println(action + " -> State: " + thread.getState());
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== THREAD LIFECYCLE DEMO ===\n");

        // ============================================
        // STATE 1: NEW
        // ============================================
        Thread thread = new Thread(() -> {
            try {
                System.out.println("  [Inside thread] Starting work...");
                
                // Sẽ vào TIMED_WAITING
                Thread.sleep(2000);
                
                System.out.println("  [Inside thread] Work completed!");
            } catch (InterruptedException e) {
                System.out.println("  [Inside thread] Interrupted!");
            }
        }, "Worker-Thread");

        printThreadState(thread, "After creation (NEW)");

        // ============================================
        // STATE 2: RUNNABLE
        // ============================================
        thread.start();
        printThreadState(thread, "After start() (RUNNABLE)");
        
        // Small delay để thread chạy
        Thread.sleep(100);

        // ============================================
        // STATE 3: TIMED_WAITING
        // ============================================
        // Thread đang sleep(2000) bên trong
        printThreadState(thread, "While sleeping (TIMED_WAITING)");

        // ============================================
        // DEMO: BLOCKED STATE
        // ============================================
        System.out.println("\n--- BLOCKED State Demo ---");
        Object lock = new Object();

        Thread t1 = new Thread(() -> {
            synchronized (lock) {
                System.out.println("  [T1] Got lock, sleeping...");
                try {
                    Thread.sleep(3000); // Hold lock for 3 seconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("  [T1] Releasing lock");
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(500); // Đảm bảo T1 lấy lock trước
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("  [T2] Trying to get lock...");
            synchronized (lock) {
                System.out.println("  [T2] Got lock!");
            }
        }, "Thread-2");

        t1.start();
        t2.start();

        Thread.sleep(1000); // Wait để T2 bị BLOCKED
        printThreadState(t2, "T2 waiting for lock (BLOCKED)");

        // ============================================
        // DEMO: WAITING STATE
        // ============================================
        System.out.println("\n--- WAITING State Demo ---");
        Thread t3 = new Thread(() -> {
            synchronized (lock) {
                try {
                    System.out.println("  [T3] Calling wait()...");
                    lock.wait(); // Vào WAITING state
                    System.out.println("  [T3] Woke up from wait()");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Thread-3");

        t3.start();
        Thread.sleep(500);
        printThreadState(t3, "T3 after wait() (WAITING)");

        // Wake up T3
        synchronized (lock) {
            lock.notify();
        }

        // ============================================
        // STATE 4: TERMINATED
        // ============================================
        thread.join(); // Wait for thread to complete
        printThreadState(thread, "After completion (TERMINATED)");

        // Wait for all threads
        t1.join();
        t2.join();
        t3.join();

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Sự khác biệt giữa BLOCKED và WAITING?");
        System.out.println("A: BLOCKED - chờ monitor lock (synchronized)");
        System.out.println("   WAITING - chờ notification từ thread khác (wait, join)");
        
        System.out.println("\nQ: Thread có thể quay lại RUNNABLE từ TERMINATED?");
        System.out.println("A: KHÔNG! Thread chỉ có thể terminate một lần");
        
        System.out.println("\nQ: sleep() vs wait()?");
        System.out.println("A: sleep() - KHÔNG release lock, TIMED_WAITING");
        System.out.println("   wait() - RELEASE lock, WAITING, phải trong synchronized");
        
        System.out.println("\nQ: Khi nào dùng join()?");
        System.out.println("A: Khi cần đợi thread khác hoàn thành trước khi tiếp tục");
        System.out.println("   Example: Main thread đợi worker threads xử lý xong");

        // ============================================
        // LIFECYCLE DIAGRAM
        // ============================================
        System.out.println("\n=== THREAD LIFECYCLE ===");
        System.out.println("NEW");
        System.out.println(" |");
        System.out.println(" | start()");
        System.out.println(" v");
        System.out.println("RUNNABLE <----------+");
        System.out.println(" |                  |");
        System.out.println(" | sleep()/wait()   | timeout/notify()");
        System.out.println(" v                  |");
        System.out.println("TIMED_WAITING/WAITING");
        System.out.println(" |");
        System.out.println(" | synchronized block");
        System.out.println(" v");
        System.out.println("BLOCKED");
        System.out.println(" |");
        System.out.println(" | run() completes");
        System.out.println(" v");
        System.out.println("TERMINATED");
    }
}
