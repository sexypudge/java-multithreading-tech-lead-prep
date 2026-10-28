/**
 * DEADLOCK - THREADS CHỜ NHAU MÃI MÃI
 * 
 * Deadlock xảy ra khi:
 * 1. Mutual Exclusion - resources không thể share
 * 2. Hold and Wait - thread hold lock và chờ lock khác
 * 3. No Preemption - không thể force release lock
 * 4. Circular Wait - T1 chờ T2, T2 chờ T1
 * 
 * Interview Question: "Làm sao detect và fix deadlock?"
 * Answer: 
 * - Detect: Thread dump, JConsole, VisualVM
 * - Fix: Lock ordering, tryLock with timeout, avoid nested locks
 */
public class DeadlockDemo {

    // ============================================
    // DEADLOCK EXAMPLE
    // ============================================
    /**
     * Classic deadlock: 2 threads, 2 locks, opposite order
     */
    static class BankAccount {
        private final String accountId;
        private int balance;

        public BankAccount(String accountId, int balance) {
            this.accountId = accountId;
            this.balance = balance;
        }

        public String getAccountId() {
            return accountId;
        }

        public int getBalance() {
            return balance;
        }

        public void withdraw(int amount) {
            balance -= amount;
        }

        public void deposit(int amount) {
            balance += amount;
        }
    }

    /**
     * DEADLOCK: Transfer giữa 2 accounts với lock order khác nhau
     */
    static void transferWithDeadlock(BankAccount from, BankAccount to, int amount) {
        synchronized (from) {
            System.out.println("  [" + Thread.currentThread().getName() + "] Locked " + from.getAccountId());
            
            try {
                Thread.sleep(100); // Simulate work, increase deadlock chance
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("  [" + Thread.currentThread().getName() + "] Waiting for " + to.getAccountId());
            synchronized (to) {
                System.out.println("  [" + Thread.currentThread().getName() + "] Locked " + to.getAccountId());
                from.withdraw(amount);
                to.deposit(amount);
                System.out.println("  [" + Thread.currentThread().getName() + "] Transfer completed");
            }
        }
    }

    // ============================================
    // SOLUTION 1: LOCK ORDERING
    // ============================================
    /**
     * Fix: Luôn acquire locks theo thứ tự nhất định
     */
    static void transferWithLockOrdering(BankAccount from, BankAccount to, int amount) {
        // Determine lock order by account ID
        BankAccount first = from.getAccountId().compareTo(to.getAccountId()) < 0 ? from : to;
        BankAccount second = first == from ? to : from;

        synchronized (first) {
            System.out.println("  [" + Thread.currentThread().getName() + "] Locked " + first.getAccountId());
            synchronized (second) {
                System.out.println("  [" + Thread.currentThread().getName() + "] Locked " + second.getAccountId());
                from.withdraw(amount);
                to.deposit(amount);
                System.out.println("  [" + Thread.currentThread().getName() + "] Transfer completed");
            }
        }
    }

    // ============================================
    // SOLUTION 2: TRYLOCK WITH TIMEOUT
    // ============================================
    /**
     * Fix: Dùng ReentrantLock với tryLock()
     */
    static class BankAccountWithLock {
        private final String accountId;
        private int balance;
        private final java.util.concurrent.locks.Lock lock = new java.util.concurrent.locks.ReentrantLock();

        public BankAccountWithLock(String accountId, int balance) {
            this.accountId = accountId;
            this.balance = balance;
        }

        public String getAccountId() {
            return accountId;
        }

        public java.util.concurrent.locks.Lock getLock() {
            return lock;
        }

        public void withdraw(int amount) {
            balance -= amount;
        }

        public void deposit(int amount) {
            balance += amount;
        }
    }

    static boolean transferWithTryLock(BankAccountWithLock from, BankAccountWithLock to, int amount) {
        try {
            // Try to acquire both locks with timeout
            if (from.getLock().tryLock(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                try {
                    if (to.getLock().tryLock(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        try {
                            from.withdraw(amount);
                            to.deposit(amount);
                            System.out.println("  [" + Thread.currentThread().getName() + "] Transfer completed");
                            return true;
                        } finally {
                            to.getLock().unlock();
                        }
                    }
                } finally {
                    from.getLock().unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("  [" + Thread.currentThread().getName() + "] Transfer failed - timeout");
        return false;
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== DEADLOCK DEMO ===\n");

        // Demo 1: Create deadlock
        System.out.println("--- Demo 1: DEADLOCK (will hang) ---");
        System.out.println("Creating deadlock... (will timeout after 5 seconds)");
        
        BankAccount acc1 = new BankAccount("ACC-001", 1000);
        BankAccount acc2 = new BankAccount("ACC-002", 1000);

        Thread t1 = new Thread(() -> {
            transferWithDeadlock(acc1, acc2, 100);
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            transferWithDeadlock(acc2, acc1, 200);
        }, "Thread-2");

        t1.start();
        t2.start();

        // Wait 5 seconds
        t1.join(5000);
        t2.join(5000);

        if (t1.isAlive() || t2.isAlive()) {
            System.out.println("✗ DEADLOCK DETECTED! Threads are stuck.");
            System.out.println("  Thread-1 state: " + t1.getState());
            System.out.println("  Thread-2 state: " + t2.getState());
            
            // Interrupt to stop
            t1.interrupt();
            t2.interrupt();
        }

        Thread.sleep(1000);

        // Demo 2: Fix with lock ordering
        System.out.println("\n--- Demo 2: FIX with Lock Ordering ---");
        BankAccount acc3 = new BankAccount("ACC-003", 1000);
        BankAccount acc4 = new BankAccount("ACC-004", 1000);

        Thread t3 = new Thread(() -> {
            transferWithLockOrdering(acc3, acc4, 100);
        }, "Thread-3");

        Thread t4 = new Thread(() -> {
            transferWithLockOrdering(acc4, acc3, 200);
        }, "Thread-4");

        t3.start();
        t4.start();
        t3.join();
        t4.join();

        System.out.println("✓ No deadlock! Both transfers completed.");

        // Demo 3: Fix with tryLock
        System.out.println("\n--- Demo 3: FIX with tryLock ---");
        BankAccountWithLock acc5 = new BankAccountWithLock("ACC-005", 1000);
        BankAccountWithLock acc6 = new BankAccountWithLock("ACC-006", 1000);

        Thread t5 = new Thread(() -> {
            transferWithTryLock(acc5, acc6, 100);
        }, "Thread-5");

        Thread t6 = new Thread(() -> {
            transferWithTryLock(acc6, acc5, 200);
        }, "Thread-6");

        t5.start();
        t6.start();
        t5.join();
        t6.join();

        System.out.println("✓ No deadlock with tryLock!");

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: 4 điều kiện của deadlock?");
        System.out.println("A: 1. Mutual Exclusion");
        System.out.println("   2. Hold and Wait");
        System.out.println("   3. No Preemption");
        System.out.println("   4. Circular Wait");
        
        System.out.println("\nQ: Làm sao detect deadlock?");
        System.out.println("A: - Thread dump (jstack, kill -3)");
        System.out.println("   - JConsole / VisualVM");
        System.out.println("   - ThreadMXBean.findDeadlockedThreads()");
        
        System.out.println("\nQ: Cách tránh deadlock?");
        System.out.println("A: 1. Lock ordering - luôn acquire theo thứ tự");
        System.out.println("   2. Lock timeout - tryLock() với timeout");
        System.out.println("   3. Avoid nested locks - minimize lock scope");
        System.out.println("   4. Use higher-level concurrency utilities");
        
        System.out.println("\nQ: Deadlock vs Livelock?");
        System.out.println("A: Deadlock - threads blocked, không progress");
        System.out.println("   Livelock - threads active nhưng không progress");
        
        System.out.println("\nQ: Production experience?");
        System.out.println("A: - Monitor thread dumps regularly");
        System.out.println("   - Use timeout for all locks");
        System.out.println("   - Logging để trace lock acquisition");
        System.out.println("   - Code review cho nested locks");

        System.out.println("\n=== HOW TO DETECT IN PRODUCTION ===");
        System.out.println("1. jstack <pid> > thread_dump.txt");
        System.out.println("2. Look for 'Found one Java-level deadlock'");
        System.out.println("3. Check waiting threads and locks they hold");
        System.out.println("4. Fix by reordering lock acquisition");
    }
}
