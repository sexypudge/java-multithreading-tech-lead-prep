import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BÀI TẬP: Hai thread in số xen kẽ: thread "odd" in 1,3,5..., thread "even" in 2,4,6... → output 1..N đúng thứ tự.
 *
 * Câu hỏi coding kinh điển. Interviewer muốn thấy:
 *   - Có TRẠNG THÁI DÙNG CHUNG rõ ràng (số hiện tại / lượt của ai) được bảo vệ bởi một lock
 *   - wait() trong WHILE, notify sau khi đổi trạng thái
 *   - Kết thúc sạch (không thread nào chờ mãi ở cuối)
 *   - Biết nhiều cách: wait/notify, Lock+Condition, Semaphore
 *
 * Hãy tự viết trước khi đọc lời giải. Chạy: java EvenOddPrinter.java
 */
public class EvenOddPrinter {

    static final int N = 20;

    // ============================================
    // CÁCH 1: wait / notifyAll
    // ============================================
    static class WaitNotifyPrinter {
        private final Object lock = new Object();
        private int next = 1;                                  // trạng thái dùng chung, bảo vệ bởi lock

        void print(boolean odd, List<Integer> out) throws InterruptedException {
            while (true) {
                synchronized (lock) {
                    while (next <= N && (next % 2 == 1) != odd) {   // chưa tới lượt mình → chờ
                        lock.wait();
                    }
                    if (next > N) {                            // điều kiện dừng kiểm tra SAU khi thức dậy
                        lock.notifyAll();                      // đánh thức thread kia để nó cũng thoát
                        return;
                    }
                    out.add(next++);
                    lock.notifyAll();
                }
            }
        }
    }

    // ============================================
    // CÁCH 2: ReentrantLock + Condition (signal đúng thread)
    // ============================================
    static class ConditionPrinter {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition oddTurn = lock.newCondition();
        private final Condition evenTurn = lock.newCondition();
        private int next = 1;

        void print(boolean odd, List<Integer> out) throws InterruptedException {
            Condition myTurn = odd ? oddTurn : evenTurn;
            Condition otherTurn = odd ? evenTurn : oddTurn;
            lock.lock();
            try {
                while (next <= N) {
                    if ((next % 2 == 1) != odd) {
                        myTurn.await();                        // vòng while bên ngoài kiểm tra lại
                        continue;
                    }
                    out.add(next++);
                    otherTurn.signal();                        // chỉ đánh thức thread kia
                }
                otherTurn.signal();                            // đảm bảo thread kia không kẹt ở await() cuối
            } finally {
                lock.unlock();
            }
        }
    }

    // ============================================
    // CÁCH 3: Semaphore "chuyền bóng"
    // ============================================
    static class SemaphorePrinter {
        private final Semaphore oddPermit = new Semaphore(1);  // odd đi trước
        private final Semaphore evenPermit = new Semaphore(0);

        void printOdd(List<Integer> out) throws InterruptedException {
            for (int i = 1; i <= N; i += 2) {
                oddPermit.acquire();
                out.add(i);
                evenPermit.release();                          // chuyền lượt cho even
            }
        }

        void printEven(List<Integer> out) throws InterruptedException {
            for (int i = 2; i <= N; i += 2) {
                evenPermit.acquire();
                out.add(i);
                oddPermit.release();
            }
        }
        // Semaphore acquire/release tạo happens-before → out.add() của thread này visible với thread kia
    }

    interface Body { void run() throws InterruptedException; }

    static List<Integer> runPair(Body oddBody, Body evenBody, List<Integer> out) throws InterruptedException {
        Thread odd = new Thread(() -> { try { oddBody.run(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }, "odd");
        Thread even = new Thread(() -> { try { evenBody.run(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }, "even");
        even.start();                                          // cố ý start even trước: lời giải vẫn phải đúng
        odd.start();
        odd.join(2000);
        even.join(2000);
        if (odd.isAlive() || even.isAlive()) throw new AssertionError("TREO: có thread không kết thúc");
        return out;
    }

    static void check(String name, List<Integer> out) {
        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= N; i++) expected.add(i);
        System.out.println("  " + name + " → " + out + (out.equals(expected) ? "  ✓" : "  ✗"));
        if (!out.equals(expected)) throw new AssertionError(name + " sai thứ tự");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== EVEN/ODD PRINTER ===\n");

        WaitNotifyPrinter p1 = new WaitNotifyPrinter();
        List<Integer> o1 = Collections.synchronizedList(new ArrayList<>());
        check("wait/notifyAll   ", runPair(() -> p1.print(true, o1), () -> p1.print(false, o1), o1));

        ConditionPrinter p2 = new ConditionPrinter();
        List<Integer> o2 = Collections.synchronizedList(new ArrayList<>());
        check("Lock + Condition ", runPair(() -> p2.print(true, o2), () -> p2.print(false, o2), o2));

        SemaphorePrinter p3 = new SemaphorePrinter();
        List<Integer> o3 = Collections.synchronizedList(new ArrayList<>());
        check("Semaphore        ", runPair(() -> p3.printOdd(o3), () -> p3.printEven(o3), o3));

        System.out.println("\nCâu hỏi follow-up:");
        System.out.println("  - Mở rộng cho 3 thread in 1,2,3,1,2,3...? → next % 3 == myIndex, hoặc 3 Semaphore xoay vòng.");
        System.out.println("  - Vì sao không dùng volatile + busy-wait? → đốt CPU; chỉ chấp nhận được với Thread.onSpinWait() và thời gian chờ cực ngắn.");
        System.out.println("  - Bài này có ý nghĩa thực tế? → Không nhiều; nó kiểm tra bạn hiểu monitor, điều kiện chờ và kết thúc sạch.");
    }
}
