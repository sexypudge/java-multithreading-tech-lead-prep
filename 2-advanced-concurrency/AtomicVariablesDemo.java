import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * ATOMICINTEGER & CAS (Compare-And-Swap)
 *
 * Nối tiếp bài volatile: volatile lo VISIBILITY + ORDERING, nhưng KHÔNG lo ATOMICITY.
 *   count++ = 3 bước: đọc → cộng 1 → ghi. Hai thread xen vào giữa → mất 1 lần cộng (lost update).
 *
 * CAS là gì?
 *   Một lệnh CPU nguyên tử:  compareAndSet(expected, newValue)
 *     "Nếu giá trị hiện tại == expected thì đổi thành newValue và trả true;
 *      ngược lại không làm gì và trả false."
 *   Không khoá (lock-free): thất bại thì THỬ LẠI (vòng lặp CAS), không ai bị chặn.
 *
 *   do {
 *       old = value.get();
 *       next = f(old);
 *   } while (!value.compareAndSet(old, next));   // ai đó xen vào → old đã cũ → thử lại
 *
 * Atomic* dùng volatile (visibility) + CAS (atomicity) bên trong.
 *
 * Chạy: java AtomicVariablesDemo.java
 */
public class AtomicVariablesDemo {

    static final int THREADS = 4;
    static final int PER_THREAD = 250_000;
    static final int EXPECTED = THREADS * PER_THREAD;

    static void runInThreads(Runnable task) throws InterruptedException {
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) (ts[i] = new Thread(task)).start();
        for (Thread t : ts) t.join();
    }

    // ============================================
    // DEMO 1: volatile count++ SAI, synchronized/Atomic ĐÚNG
    // ============================================
    static volatile int volatileCount = 0;
    static int syncCount = 0;
    static final Object LOCK = new Object();
    static final AtomicInteger atomicCount = new AtomicInteger();

    static void demo1LostUpdate() throws InterruptedException {
        runInThreads(() -> { for (int i = 0; i < PER_THREAD; i++) volatileCount++; });
        System.out.printf("  volatile int count++   : %,d / %,d  %s%n", volatileCount, EXPECTED,
                volatileCount == EXPECTED ? "(may mắn đúng - vẫn là code SAI)" : "✗ mất " + (EXPECTED - volatileCount) + " lần cộng");

        runInThreads(() -> { for (int i = 0; i < PER_THREAD; i++) synchronized (LOCK) { syncCount++; } });
        System.out.printf("  synchronized           : %,d / %,d  ✓%n", syncCount, EXPECTED);

        runInThreads(() -> { for (int i = 0; i < PER_THREAD; i++) atomicCount.incrementAndGet(); });
        System.out.printf("  AtomicInteger          : %,d / %,d  ✓%n", atomicCount.get(), EXPECTED);
        System.out.println("  → volatile thấy được giá trị mới nhất, nhưng 'đọc-cộng-ghi' vẫn bị xen ngang.");
    }

    // ============================================
    // DEMO 2: Tự viết vòng lặp CAS
    // ============================================
    /** Bộ đếm có TRẦN: chỉ tăng nếu < limit. incrementAndGet() không làm được điều kiện này. */
    static class BoundedCounter {
        private final AtomicInteger value = new AtomicInteger();
        private final int limit;
        BoundedCounter(int limit) { this.limit = limit; }

        boolean tryIncrement() {
            while (true) {
                int current = value.get();                    // 1. đọc
                if (current >= limit) return false;           // 2. kiểm tra điều kiện
                if (value.compareAndSet(current, current + 1)) // 3. chỉ ghi nếu chưa ai đổi
                    return true;
                // CAS thất bại = có thread khác vừa đổi → vòng lại đọc giá trị mới
            }
        }
        int get() { return value.get(); }
    }

    static void demo2CasLoop() throws InterruptedException {
        BoundedCounter counter = new BoundedCounter(1000);
        AtomicInteger accepted = new AtomicInteger();
        runInThreads(() -> {
            for (int i = 0; i < 2000; i++) if (counter.tryIncrement()) accepted.incrementAndGet();
        });
        System.out.println("  8000 lượt thử, limit=1000 → giá trị = " + counter.get() + ", chấp nhận = " + accepted.get()
                + "  (luôn đúng 1000, không vượt trần)");

        // Cách ngắn gọn: updateAndGet / accumulateAndGet (bên trong chính là vòng lặp CAS)
        AtomicInteger max = new AtomicInteger(Integer.MIN_VALUE);
        runInThreads(() -> { for (int i = 0; i < 1000; i++) max.accumulateAndGet(i, Math::max); });
        System.out.println("  accumulateAndGet(i, Math::max) → max = " + max.get());
        System.out.println("  ⚠ Hàm truyền vào updateAndGet có thể bị GỌI LẠI nhiều lần → phải không có side effect.");
    }

    // ============================================
    // DEMO 3: AtomicLong vs LongAdder dưới tranh chấp cao
    // ============================================
    static void demo3LongAdder() throws InterruptedException {
        AtomicLong al = new AtomicLong();
        long t0 = System.nanoTime();
        runInThreads(() -> { for (int i = 0; i < PER_THREAD * 4; i++) al.incrementAndGet(); });
        long atomicMs = (System.nanoTime() - t0) / 1_000_000;

        LongAdder adder = new LongAdder();
        t0 = System.nanoTime();
        runInThreads(() -> { for (int i = 0; i < PER_THREAD * 4; i++) adder.increment(); });
        long adderMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("  AtomicLong: %d ms (tổng %,d) | LongAdder: %d ms (tổng %,d)%n", atomicMs, al.get(), adderMs, adder.sum());
        System.out.println("  Nhiều thread cùng CAS 1 biến → thất bại/thử lại liên tục. LongAdder chia thành nhiều 'ô' (cell),");
        System.out.println("  mỗi thread cộng vào ô riêng, sum() mới cộng dồn. Nhanh hơn khi NHIỀU CORE tranh chấp thật sự");
        System.out.println("  (máy 1-2 core có thể thấy ngược lại, vì gần như không có tranh chấp - kết quả tuỳ máy),");
        System.out.println("  nhưng sum() không phải ảnh chụp tức thời → dùng cho metric/counter, KHÔNG dùng cho logic cần giá trị chính xác.");
    }

    // ============================================
    // DEMO 4: Vấn đề ABA
    // ============================================
    static void demo4Aba() {
        /*
         * CAS chỉ so sánh GIÁ TRỊ. Nếu giá trị đi A → B → A thì CAS tưởng "chưa có gì xảy ra".
         * Thread 1 đọc A. Thread 2 đổi A→B rồi B→A. Thread 1 CAS(A→C) vẫn thành công.
         * Với số nguyên thường vô hại; với con trỏ (stack/queue lock-free) có thể gây hỏng cấu trúc.
         */
        AtomicReference<String> ref = new AtomicReference<>("A");
        String seen = ref.get();           // "Thread 1" đọc thấy A
        ref.set("B");                      // "Thread 2" đổi A → B
        ref.set("A");                      //            rồi B → A
        boolean ok = ref.compareAndSet(seen, "C");
        System.out.println("  AtomicReference     : CAS(A→C) = " + ok + "   ← thành công dù giá trị đã bị đổi rồi trả lại");

        // Sửa: AtomicStampedReference gắn thêm 'stamp' (số phiên bản) vào mỗi lần đổi
        AtomicStampedReference<String> sref = new AtomicStampedReference<>("A", 0);
        int[] holder = new int[1];
        String seen2 = sref.get(holder);
        int stamp = holder[0];                                  // Thread 1 đọc (A, stamp 0)
        sref.compareAndSet("A", "B", 0, 1);                     // Thread 2: A→B, stamp 1
        sref.compareAndSet("B", "A", 1, 2);                     //           B→A, stamp 2
        boolean ok2 = sref.compareAndSet(seen2, "C", stamp, stamp + 1);
        System.out.println("  AtomicStampedReference: CAS(A→C, stamp 0) = " + ok2 + "  ← từ chối vì stamp đã là 2");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== ATOMIC & CAS DEMO ===\n");

        System.out.println("--- Demo 1: volatile count++ vs synchronized vs AtomicInteger ---");
        demo1LostUpdate();

        System.out.println("\n--- Demo 2: Vòng lặp CAS tự viết ---");
        demo2CasLoop();

        System.out.println("\n--- Demo 3: AtomicLong vs LongAdder ---");
        demo3LongAdder();

        System.out.println("\n--- Demo 4: Vấn đề ABA ---");
        demo4Aba();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: volatile int count; count++ có thread-safe không?");
        System.out.println("A: Không. count++ là đọc-cộng-ghi (3 bước), volatile chỉ đảm bảo visibility/ordering,");
        System.out.println("   không đảm bảo atomicity. Dùng AtomicInteger hoặc synchronized.");

        System.out.println("\nQ: CAS là gì? AtomicInteger hoạt động ra sao?");
        System.out.println("A: Lệnh CPU nguyên tử 'nếu giá trị == expected thì đổi thành new'. AtomicInteger lặp: đọc → tính → CAS,");
        System.out.println("   thất bại thì thử lại. Lock-free: không chặn thread, nhưng dưới tranh chấp cao thì tốn CPU quay vòng.");

        System.out.println("\nQ: Atomic hay synchronized?");
        System.out.println("A: Atomic cho MỘT biến, thao tác đơn giản. Cần bất biến liên quan NHIỀU biến");
        System.out.println("   (vd: trừ tiền A, cộng tiền B cùng lúc) thì phải dùng lock.");

        System.out.println("\nQ: ABA là gì?");
        System.out.println("A: Giá trị đi A→B→A nên CAS tưởng không đổi. Khắc phục bằng AtomicStampedReference (thêm version).");

        System.out.println("\nQ: Khi nào dùng LongAdder?");
        System.out.println("A: Counter/metric ghi nhiều đọc ít, tranh chấp cao. Không dùng khi cần giá trị chính xác để ra quyết định.");
    }
}
