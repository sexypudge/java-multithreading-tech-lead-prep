import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * COUNTDOWNLATCH, CYCLICBARRIER, SEMAPHORE — các "synchronizer" (công cụ phối hợp thread)
 *
 *   CountDownLatch : "Chờ N sự kiện xảy ra rồi mới đi tiếp."     DÙNG 1 LẦN (đếm về 0 là xong, không reset)
 *   CyclicBarrier  : "N thread chờ NHAU ở một điểm hẹn."          TÁI SỬ DỤNG được (nhiều vòng)
 *   Semaphore      : "Tối đa N thread cùng vào vùng này."         giữ N 'giấy phép' (permit)
 *
 * Phân biệt nhanh:
 *   Latch   : thread này CHỜ thread khác (bên chờ ≠ bên countDown)
 *   Barrier : các thread chờ LẪN NHAU (cùng là bên chờ)
 *   Semaphore : không chờ ai cả, chỉ giới hạn số lượng đồng thời
 *
 * Chạy: java SynchronizersDemo.java
 */
public class SynchronizersDemo {

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ============================================
    // DEMO 1: CountDownLatch
    // ============================================
    static void demo1CountDownLatch() throws InterruptedException {
        // (a) "Chờ nhiều worker xong": main await() cho đến khi count về 0
        int workers = 3;
        CountDownLatch done = new CountDownLatch(workers);
        for (int i = 1; i <= workers; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    sleep(100L * id);
                    System.out.println("  [Worker-" + id + "] xong");
                } finally {
                    done.countDown();       // đặt trong finally: worker lỗi cũng phải đếm, nếu không main chờ mãi
                }
            }, "Worker-" + id).start();
        }
        boolean ok = done.await(5, TimeUnit.SECONDS);   // luôn dùng await có timeout ở production
        System.out.println("  [Main] tất cả worker xong? " + ok);

        // (b) "Cổng xuất phát": nhiều thread chờ 1 tín hiệu rồi cùng chạy (hay dùng để test đồng thời)
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(3);
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    startGate.await();                              // tất cả đứng chờ ở đây
                    System.out.println("  [Racer-" + id + "] chạy!");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { finished.countDown(); }
            }).start();
        }
        sleep(200);
        System.out.println("  [Main] bắn súng lệnh!");
        startGate.countDown();                                       // count 1 → 0, thả cả 3 thread
        finished.await();
        System.out.println("  ⚠ Latch dùng 1 lần: count đã về 0 thì await() sau đó trả về ngay, không thể reset.");
    }

    // ============================================
    // DEMO 2: CyclicBarrier
    // ============================================
    static void demo2CyclicBarrier() throws InterruptedException {
        int parties = 3;
        // barrierAction: chạy bởi thread cuối cùng đến barrier, TRƯỚC khi thả mọi thread
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> System.out.println("  --- cả 3 đã tới barrier → thả tất cả đi tiếp ---"));

        Thread[] ts = new Thread[parties];
        for (int i = 0; i < parties; i++) {
            final int id = i + 1;
            ts[i] = new Thread(() -> {
                try {
                    for (int round = 1; round <= 2; round++) {          // barrier dùng lại cho 2 vòng
                        sleep(100L * id);                               // mỗi thread làm việc với tốc độ khác nhau
                        System.out.println("  [P" + id + "] xong phần việc vòng " + round + ", chờ các bạn");
                        barrier.await();                                // chờ đủ 3 thread
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    System.out.println("  [P" + id + "] barrier bị hỏng: " + e);
                }
            }, "P" + id);
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        System.out.println("  Dùng cho: tính toán song song theo pha (mô phỏng, xử lý ma trận, test cần đồng bộ nhiều bước).");
        System.out.println("  ⚠ Nếu 1 thread chết/timeout/bị interrupt → barrier 'broken', các thread còn lại nhận BrokenBarrierException.");
    }

    // ============================================
    // DEMO 3: Semaphore
    // ============================================
    static void demo3Semaphore() throws InterruptedException {
        // Giả lập: connection pool/API bên ngoài chỉ chịu được 3 request đồng thời
        Semaphore permits = new Semaphore(3);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();

        Thread[] ts = new Thread[8];
        for (int i = 0; i < ts.length; i++) {
            final int id = i + 1;
            ts[i] = new Thread(() -> {
                try {
                    permits.acquire();                                  // hết permit thì CHỜ
                    try {                                               // release PHẢI nằm trong finally
                        int now = inside.incrementAndGet();
                        maxInside.accumulateAndGet(now, Math::max);
                        System.out.println("  [T" + id + "] vào (đang trong vùng: " + now + ")");
                        sleep(300);
                        inside.decrementAndGet();
                    } finally {
                        permits.release();
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "T" + id);
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        System.out.println("  8 thread, 3 permit → số thread đồng thời tối đa quan sát được = " + maxInside.get() + "  (≤ 3 ✓)");

        // tryAcquire: không muốn chờ vô hạn → load shedding (từ chối bớt khi quá tải)
        Semaphore one = new Semaphore(1);
        one.acquire();
        System.out.println("  tryAcquire(100ms) khi hết permit → " + one.tryAcquire(100, TimeUnit.MILLISECONDS) + "  (từ chối thay vì chờ mãi)");
        one.release();

        System.out.println("  Semaphore(1) ≈ mutex, nhưng KHÔNG gắn với thread: thread A acquire, thread B có thể release.");
        System.out.println("  → Không 'reentrant' và không có chủ sở hữu → dễ quên release gây rò permit; dùng try/finally.");
    }

    // ============================================
    // DEMO 4: Phaser (P2 - biết để so sánh)
    // ============================================
    static void demo4Phaser() throws InterruptedException {
        // Phaser = CyclicBarrier "linh hoạt": số bên tham gia THAY ĐỔI được (register / arriveAndDeregister)
        Phaser phaser = new Phaser(1);                          // main đăng ký để điều khiển các pha
        Thread[] ts = new Thread[3];
        for (int i = 0; i < ts.length; i++) {
            final int id = i + 1;
            phaser.register();                                  // thêm 1 bên tham gia lúc runtime
            ts[i] = new Thread(() -> {
                System.out.println("  [W" + id + "] pha " + phaser.getPhase() + ": tải dữ liệu");
                phaser.arriveAndAwaitAdvance();                 // chờ mọi bên xong pha 0
                if (id == 3) {
                    System.out.println("  [W3] không cần pha sau → rời nhóm");
                    phaser.arriveAndDeregister();               // số bên giảm, barrier không chờ W3 nữa
                    return;
                }
                System.out.println("  [W" + id + "] pha " + phaser.getPhase() + ": xử lý");
                phaser.arriveAndDeregister();
            }, "W" + id);
            ts[i].start();
        }
        phaser.arriveAndAwaitAdvance();                         // main cũng tham gia pha 0
        phaser.arriveAndDeregister();
        for (Thread t : ts) t.join();
        System.out.println("  Ít gặp trong code nghiệp vụ; biết để trả lời 'Phaser khác CyclicBarrier thế nào'.");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== SYNCHRONIZERS DEMO ===\n");

        System.out.println("--- Demo 1: CountDownLatch ---");
        demo1CountDownLatch();

        System.out.println("\n--- Demo 2: CyclicBarrier ---");
        demo2CyclicBarrier();

        System.out.println("\n--- Demo 3: Semaphore ---");
        demo3Semaphore();

        System.out.println("\n--- Demo 4: Phaser ---");
        demo4Phaser();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: CountDownLatch khác CyclicBarrier?");
        System.out.println("A: Latch: một hoặc nhiều thread chờ N sự kiện từ thread khác, dùng 1 lần. Barrier: N thread chờ NHAU");
        System.out.println("   tại điểm hẹn, tái sử dụng nhiều vòng, có barrierAction.");

        System.out.println("\nQ: Semaphore dùng khi nào?");
        System.out.println("A: Giới hạn số thread đồng thời truy cập tài nguyên (connection pool, API có rate limit, bulkhead).");
        System.out.println("   Luôn release trong finally.");

        System.out.println("\nQ: Semaphore(1) khác ReentrantLock thế nào?");
        System.out.println("A: Lock có chủ sở hữu và reentrant; Semaphore thì không, thread nào cũng release được.");

        System.out.println("\nQ: Nếu quên countDown() trong CountDownLatch thì sao?");
        System.out.println("A: await() chờ mãi (treo). Đặt countDown trong finally và dùng await có timeout.");

        System.out.println("\nQ: Ví dụ thực tế?");
        System.out.println("A: Latch: chờ N service khởi động xong / gọi N API song song rồi tổng hợp. Barrier: tính toán theo pha.");
        System.out.println("   Semaphore: giới hạn 10 request đồng thời tới dịch vụ bên thứ ba.");

        System.out.println("\nQ: Phaser khác CyclicBarrier?");
        System.out.println("A: Phaser cho phép số bên tham gia thay đổi lúc runtime và nhiều pha có đánh số;");
        System.out.println("   CyclicBarrier cố định số bên ngay từ constructor.");
    }
}
