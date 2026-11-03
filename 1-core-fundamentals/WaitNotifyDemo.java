import java.util.LinkedList;
import java.util.Queue;

/**
 * WAIT/NOTIFY - INTER-THREAD COMMUNICATION
 * 
 * wait() - Release lock và chờ notification
 * notify() - Đánh thức 1 thread đang wait
 * notifyAll() - Đánh thức TẤT CẢ threads đang wait
 * 
 * RULES:
 * 1. Phải gọi trong synchronized block/method
 * 2. wait() release lock, sleep() KHÔNG release
 * 3. Luôn dùng while loop, không dùng if (spurious wakeup)
 * 
 * Interview Question: "Tại sao wait() phải trong synchronized?"
 * Answer: Để tránh race condition giữa check condition và wait
 */
public class WaitNotifyDemo {

    // ============================================
    // PRODUCER-CONSUMER PATTERN
    // ============================================
    /**
     * Classic example: Producer tạo data, Consumer xử lý data
     * Queue có capacity limit
     */
    static class ProducerConsumer {
        private final Queue<Integer> queue = new LinkedList<>();
        private final int CAPACITY = 5;

        /**
         * Producer thêm item vào queue
         * Nếu queue full → wait()
         */
        public void produce() throws InterruptedException {
            int value = 0;
            while (true) {
                synchronized (this) {
                    // IMPORTANT: Dùng WHILE, không dùng IF (spurious wakeup)
                    while (queue.size() == CAPACITY) {
                        System.out.println("  [Producer] Queue full, waiting...");
                        wait(); // Release lock và chờ
                    }

                    System.out.println("  [Producer] Producing: " + value);
                    queue.add(value++);
                    
                    // Notify consumer có data mới
                    notify(); // hoặc notifyAll()
                    
                    Thread.sleep(500);
                }
            }
        }

        /**
         * Consumer lấy item từ queue
         * Nếu queue empty → wait()
         */
        public void consume() throws InterruptedException {
            while (true) {
                synchronized (this) {
                    // IMPORTANT: Dùng WHILE loop
                    while (queue.isEmpty()) {
                        System.out.println("  [Consumer] Queue empty, waiting...");
                        wait();
                    }

                    int value = queue.poll();
                    System.out.println("  [Consumer] Consuming: " + value);
                    
                    // Notify producer có space
                    notify();
                    
                    Thread.sleep(1000);
                }
            }
        }
    }

    // ============================================
    // DEMO: NOTIFY VS NOTIFYALL
    // ============================================
    static class NotifyVsNotifyAll {
        private boolean dataReady = false;

        public synchronized void waitForData(String threadName) throws InterruptedException {
            System.out.println("  [" + threadName + "] Waiting for data...");
            while (!dataReady) {
                wait();
            }
            System.out.println("  [" + threadName + "] Got data!");
        }

        public synchronized void produceDataWithNotify() {
            System.out.println("  [Producer] Producing data...");
            dataReady = true;
            notify(); // Chỉ đánh thức 1 thread
            System.out.println("  [Producer] Called notify() - only 1 thread wakes up");
        }

        public synchronized void produceDataWithNotifyAll() {
            System.out.println("  [Producer] Producing data...");
            dataReady = true;
            notifyAll(); // Đánh thức TẤT CẢ threads
            System.out.println("  [Producer] Called notifyAll() - all threads wake up");
        }
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== WAIT/NOTIFY DEMO ===\n");

        // Demo 1: Producer-Consumer
        System.out.println("--- Demo 1: Producer-Consumer Pattern ---");
        ProducerConsumer pc = new ProducerConsumer();

        Thread producer = new Thread(() -> {
            try {
                pc.produce();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Producer");

        Thread consumer = new Thread(() -> {
            try {
                pc.consume();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Consumer");

        producer.start();
        consumer.start();

        // Let it run for 5 seconds
        Thread.sleep(5000);
        producer.interrupt();
        consumer.interrupt();

        // Demo 2: notify() vs notifyAll()
        System.out.println("\n--- Demo 2: notify() vs notifyAll() ---");
        
        // Test notify() - chỉ 1 thread được wake up
        System.out.println("\nTest with notify():");
        NotifyVsNotifyAll demo1 = new NotifyVsNotifyAll();
        
        Thread t1 = new Thread(() -> {
            try {
                demo1.waitForData("Thread-1");
            } catch (InterruptedException e) {}
        });
        
        Thread t2 = new Thread(() -> {
            try {
                demo1.waitForData("Thread-2");
            } catch (InterruptedException e) {}
        });
        
        t1.start();
        t2.start();
        Thread.sleep(1000);
        
        demo1.produceDataWithNotify(); // Chỉ 1 thread wake up
        Thread.sleep(2000);

        // Test notifyAll() - tất cả threads được wake up
        System.out.println("\nTest with notifyAll():");
        NotifyVsNotifyAll demo2 = new NotifyVsNotifyAll();
        
        Thread t3 = new Thread(() -> {
            try {
                demo2.waitForData("Thread-3");
            } catch (InterruptedException e) {}
        });
        
        Thread t4 = new Thread(() -> {
            try {
                demo2.waitForData("Thread-4");
            } catch (InterruptedException e) {}
        });
        
        t3.start();
        t4.start();
        Thread.sleep(1000);
        
        demo2.produceDataWithNotifyAll(); // Tất cả threads wake up
        Thread.sleep(2000);

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Tại sao wait() phải trong synchronized?");
        System.out.println("A: Tránh race condition giữa check và wait");
        System.out.println("   Thread có thể bị notify ngay trước khi wait → miss notification");
        
        System.out.println("\nQ: Tại sao dùng while thay vì if?");
        System.out.println("A: Spurious wakeup - thread có thể wake up không có notify");
        System.out.println("   Phải re-check condition sau khi wake up");
        
        System.out.println("\nQ: wait() vs sleep()?");
        System.out.println("A: wait() - RELEASE lock, trong synchronized, cần notify");
        System.out.println("   sleep() - KHÔNG release lock, tự wake up sau timeout");
        
        System.out.println("\nQ: notify() vs notifyAll()?");
        System.out.println("A: notify() - wake 1 thread (không biết thread nào)");
        System.out.println("   notifyAll() - wake tất cả (safer, prefer this)");
        
        System.out.println("\nQ: wait() có timeout?");
        System.out.println("A: CÓ! wait(timeout) - auto wake up sau timeout");
        System.out.println("   Vẫn cần re-check condition trong while loop");

        System.out.println("\n=== COMMON PATTERN ===");
        System.out.println("synchronized (lock) {");
        System.out.println("    while (!condition) {  // WHILE, not IF!");
        System.out.println("        lock.wait();");
        System.out.println("    }");
        System.out.println("    // Do work");
        System.out.println("    lock.notifyAll();  // Prefer notifyAll()");
        System.out.println("}");

        System.exit(0);
    }
}
