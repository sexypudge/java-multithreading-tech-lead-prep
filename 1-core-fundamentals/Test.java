import java.util.concurrent.*;

public class Test {

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

    private static void printThreadState(Thread thread, String action) {
        System.out.println(action + " -> State: " + thread.getState());
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Object lock = new Object();

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
    }
}
