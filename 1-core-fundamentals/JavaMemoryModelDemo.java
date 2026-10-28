import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * JAVA MEMORY MODEL (JMM) - HAPPENS-BEFORE
 *
 * Vì sao cần JMM? Vì CPU và compiler được phép:
 *   - Cache giá trị trong thanh ghi / L1 cache của từng core  → vấn đề VISIBILITY
 *   - Sắp xếp lại (reorder) lệnh miễn là kết quả trong 1 thread không đổi → vấn đề ORDERING
 *
 * JMM định nghĩa quy tắc HAPPENS-BEFORE (HB):
 *   Nếu A happens-before B thì mọi thay đổi do A tạo ra ĐƯỢC THẤY bởi B,
 *   và A không bị reorder ra sau B.
 *
 * CÁC QUY TẮC HB QUAN TRỌNG:
 *   1. Program order    : trong 1 thread, lệnh trước HB lệnh sau
 *   2. Monitor lock     : unlock monitor M HB lock M tiếp theo
 *   3. Volatile         : ghi volatile V HB đọc volatile V sau đó
 *   4. Thread start     : Thread.start() HB mọi lệnh trong thread được start
 *   5. Thread join      : mọi lệnh trong thread HB join() trả về ở thread chờ
 *   6. Transitivity     : A HB B và B HB C thì A HB C
 *   7. final fields     : đảm bảo an toàn đặc biệt khi publish (không để 'this' thoát ra trong constructor)
 *
 * Interview Question: "Happens-before là gì? Cho ví dụ."
 * Answer: Là quan hệ đảm bảo visibility + ordering giữa 2 hành động. Ví dụ ghi volatile
 *         rồi đọc volatile cùng biến: mọi ghi thường trước đó cũng được thấy.
 *
 * LƯU Ý: Một số demo phụ thuộc JIT/CPU nên kết quả "lỗi" có thể không xuất hiện
 *        trên mọi máy. Không thấy lỗi KHÔNG có nghĩa là code đúng.
 */
public class JavaMemoryModelDemo {

    // ============================================
    // DEMO 1: VISIBILITY - biến không volatile
    // ============================================
    static class PlainFlag {
        boolean stop = false; // KHÔNG volatile
    }

    static void demo1Visibility() throws InterruptedException {
        PlainFlag flag = new PlainFlag();
        Thread t = new Thread(() -> {
            long n = 0;
            while (!flag.stop) { // JIT có thể hoist việc đọc ra ngoài vòng lặp
                n++;
            }
            System.out.println("  [Reader] Thấy stop=true sau " + n + " vòng");
        }, "PlainReader");
        t.setDaemon(true); // để JVM vẫn thoát được nếu thread kẹt vô hạn
        t.start();

        Thread.sleep(500);
        flag.stop = true;
        t.join(1000);
        if (t.isAlive()) {
            System.out.println("  ✗ Reader KHÔNG thấy thay đổi sau 1 giây → lỗi visibility (không có HB)");
        } else {
            System.out.println("  (Lần này reader thoát - JIT chưa tối ưu. Vẫn là code SAI theo JMM.)");
        }
    }

    // ============================================
    // DEMO 2: SAFE PUBLICATION bằng volatile
    // ============================================
    static class Publisher {
        int data = 0;                 // biến thường
        volatile boolean ready = false; // volatile làm "cầu nối" happens-before

        void write() {
            data = 42;      // (1) ghi thường
            ready = true;   // (2) ghi volatile  → HB → (3)
        }

        int read() {
            while (!ready) {          // (3) đọc volatile
                Thread.onSpinWait();
            }
            return data;              // (4) đảm bảo thấy 42 nhờ transitivity: (1) HB (2) HB (3) HB (4)
        }
    }

    static void demo2VolatilePublication() throws InterruptedException {
        Publisher p = new Publisher();
        Thread reader = new Thread(() -> System.out.println("  [Reader] data = " + p.read() + "  ✓ luôn là 42"));
        reader.start();
        Thread.sleep(100);
        p.write();
        reader.join();
    }

    // ============================================
    // DEMO 3: Thread.start() và join() tạo happens-before
    // ============================================
    static int sharedBeforeStart = 0;
    static int sharedByWorker = 0;

    static void demo3StartJoin() throws InterruptedException {
        sharedBeforeStart = 10; // ghi trước start()
        Thread t = new Thread(() -> {
            // start() HB mọi lệnh trong thread → chắc chắn thấy 10
            System.out.println("  [Worker] thấy sharedBeforeStart = " + sharedBeforeStart);
            sharedByWorker = 20; // ghi thường, không volatile
        });
        t.start();
        t.join();
        // join() trả về: mọi lệnh trong worker HB lệnh sau join → chắc chắn thấy 20
        System.out.println("  [Main] sau join thấy sharedByWorker = " + sharedByWorker);
    }

    // ============================================
    // DEMO 4: REORDERING (litmus test kinh điển - store buffer)
    // ============================================
    /**
     *   Thread 1: x = 1; r1 = y;      Thread 2: y = 1; r2 = x;
     *   Theo "trực giác tuần tự", không thể có r1 == 0 && r2 == 0.
     *   Nhưng CPU có store buffer → ghi chưa kịp thấy → CÓ THỂ xảy ra (kể cả trên x86).
     *   Với volatile: không xảy ra.
     */
    interface Litmus {
        void reset();
        void t1();
        void t2();
        boolean weird();
    }

    static class PlainLitmus implements Litmus {
        int x, y, r1, r2;
        public void reset() { x = 0; y = 0; r1 = -1; r2 = -1; }
        public void t1() { x = 1; r1 = y; }
        public void t2() { y = 1; r2 = x; }
        public boolean weird() { return r1 == 0 && r2 == 0; }
    }

    static class VolatileLitmus implements Litmus {
        volatile int x, y;
        int r1, r2;
        public void reset() { x = 0; y = 0; r1 = -1; r2 = -1; }
        public void t1() { x = 1; r1 = y; }
        public void t2() { y = 1; r2 = x; }
        public boolean weird() { return r1 == 0 && r2 == 0; }
    }

    static int runLitmus(Litmus l, int iterations) throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        CyclicBarrier end = new CyclicBarrier(3);

        Runnable r1 = () -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    start.await();
                    l.t1();
                    end.await();
                }
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        };
        Runnable r2 = () -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    start.await();
                    l.t2();
                    end.await();
                }
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        };

        Thread a = new Thread(r1, "T1");
        Thread b = new Thread(r2, "T2");
        a.start();
        b.start();

        int weird = 0;
        for (int i = 0; i < iterations; i++) {
            l.reset();
            start.await();
            end.await();
            if (l.weird()) weird++;
        }
        a.join();
        b.join();
        return weird;
    }

    static void demo4Reordering() throws Exception {
        final int N = 30_000;
        int plain = runLitmus(new PlainLitmus(), N);
        System.out.println("  Biến thường : r1==0 && r2==0 xuất hiện " + plain + "/" + N + " lần"
                + (plain > 0 ? "  ✗ reordering quan sát được!" : "  (0 lần - tuỳ máy, không chứng minh được là an toàn)"));
        int vol = runLitmus(new VolatileLitmus(), N);
        System.out.println("  Biến volatile: r1==0 && r2==0 xuất hiện " + vol + "/" + N + " lần  ✓ phải luôn là 0");
    }

    // ============================================
    // DEMO 5: SINGLETON - DCL, Holder, Enum
    // ============================================
    /**
     * DCL KHÔNG volatile là SAI vì 'instance = new X()' gồm 3 bước:
     *   1. allocate memory
     *   2. chạy constructor (khởi tạo field)
     *   3. gán reference vào 'instance'
     * Bước 2 và 3 có thể bị reorder → thread khác thấy instance != null
     * nhưng object CHƯA khởi tạo xong. volatile cấm reorder này.
     */
    static class DclSingleton {
        private static volatile DclSingleton instance; // volatile là BẮT BUỘC
        private final int value;

        private DclSingleton() { this.value = 100; }

        static DclSingleton getInstance() {
            DclSingleton local = instance;       // đọc volatile 1 lần (tối ưu nhỏ)
            if (local == null) {
                synchronized (DclSingleton.class) {
                    local = instance;
                    if (local == null) {
                        instance = local = new DclSingleton();
                    }
                }
            }
            return local;
        }
    }

    /** Static holder: JVM đảm bảo class init thread-safe và lazy. ĐƠN GIẢN NHẤT, thường nên dùng. */
    static class HolderSingleton {
        private HolderSingleton() {}
        private static class Holder {
            static final HolderSingleton INSTANCE = new HolderSingleton();
        }
        static HolderSingleton getInstance() { return Holder.INSTANCE; }
    }

    /** Enum: thread-safe, chống reflection và serialization (Effective Java khuyên dùng). */
    enum EnumSingleton { INSTANCE }

    static void demo5Singleton() {
        System.out.println("  DCL    : " + (DclSingleton.getInstance() == DclSingleton.getInstance()));
        System.out.println("  Holder : " + (HolderSingleton.getInstance() == HolderSingleton.getInstance()));
        System.out.println("  Enum   : " + (EnumSingleton.INSTANCE == EnumSingleton.valueOf("INSTANCE")));
    }

    // ============================================
    // DEMO 6: FINAL FIELD - an toàn khi publish
    // ============================================
    static class FinalHolder {
        private final int a;
        private final int[] arr;

        FinalHolder() {
            this.a = 1;
            this.arr = new int[]{1, 2, 3};
            // KHÔNG được để 'this' thoát ra ngoài trong constructor
            // (vd: đăng ký listener, start thread) → phá vỡ đảm bảo của final
        }
        int sum() { return a + arr[0] + arr[1] + arr[2]; }
    }

    static FinalHolder racyPublished; // publish KHÔNG đồng bộ (data race)

    static void demo6Final() throws InterruptedException {
        Thread reader = new Thread(() -> {
            FinalHolder h;
            while ((h = racyPublished) == null) { Thread.onSpinWait(); }
            // Dù publish có data race, final field ĐƯỢC ĐẢM BẢO thấy giá trị đã khởi tạo
            System.out.println("  [Reader] sum = " + h.sum() + "  ✓ luôn 7 nhờ final field semantics");
        });
        reader.setDaemon(true);
        reader.start();
        Thread.sleep(50);
        racyPublished = new FinalHolder();
        reader.join(1000);
        System.out.println("  (Lưu ý: vòng spin đọc biến thường 'racyPublished' vẫn có thể không thoát trên JIT");
        System.out.println("   mạnh tay → trong code thật phải publish qua volatile/final/lock.)");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== JAVA MEMORY MODEL DEMO ===\n");

        System.out.println("--- Demo 1: Visibility (không có happens-before) ---");
        demo1Visibility();

        System.out.println("\n--- Demo 2: Safe publication bằng volatile ---");
        demo2VolatilePublication();

        System.out.println("\n--- Demo 3: Thread.start() / join() ---");
        demo3StartJoin();

        System.out.println("\n--- Demo 4: Reordering (có thể mất vài giây) ---");
        demo4Reordering();

        System.out.println("\n--- Demo 5: Singleton ---");
        demo5Singleton();

        System.out.println("\n--- Demo 6: final field ---");
        demo6Final();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Happens-before là gì?");
        System.out.println("A: Quan hệ đảm bảo hành động A thấy được/được xếp trước hành động B.");
        System.out.println("   Không liên quan thời gian thực, chỉ liên quan quy tắc của JMM.");

        System.out.println("\nQ: Liệt kê các quy tắc happens-before?");
        System.out.println("A: Program order, monitor unlock→lock, volatile write→read,");
        System.out.println("   Thread.start, Thread.join, transitivity (+ final field, static init).");

        System.out.println("\nQ: Vì sao DCL cần volatile?");
        System.out.println("A: Cấm reorder giữa 'chạy constructor' và 'gán reference', tránh thread khác");
        System.out.println("   thấy object khởi tạo dở. Từ Java 5 (JSR-133) volatile mới đảm bảo điều này.");

        System.out.println("\nQ: volatile có thay thế synchronized được không?");
        System.out.println("A: Chỉ khi thao tác là đọc/ghi ĐƠN và không phụ thuộc giá trị cũ.");

        System.out.println("\nQ: Data race khác race condition?");
        System.out.println("A: Data race: truy cập biến chung không đồng bộ (ít nhất 1 ghi) - khái niệm của JMM.");
        System.out.println("   Race condition: kết quả phụ thuộc thứ tự thread - lỗi logic, có thể xảy ra");
        System.out.println("   cả khi KHÔNG có data race (vd: dùng ConcurrentHashMap nhưng check-then-put).");

        System.out.println("\nQ: 'volatile ép đọc/ghi từ main memory' có đúng không?");
        System.out.println("A: Đó là cách nói đơn giản hoá. Chính xác: volatile tạo happens-before và memory barrier,");
        System.out.println("   phần cứng vẫn dùng cache nhưng được đồng bộ bằng cache coherence.");
    }
}
