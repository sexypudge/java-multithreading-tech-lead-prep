package v2;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CONCURRENTHASHMAP & BLOCKINGQUEUE
 *
 * ConcurrentHashMap (CHM):
 *   - Mỗi thao tác đơn (get/put/remove) là thread-safe và atomic.
 *   - Java 8+: không khoá toàn bảng; get() không khoá, put() khoá/CAS ở mức từng bucket → nhiều thread ghi song song.
 *   - KHÔNG cho phép key/value null (vì get() trả null sẽ không biết là "không có" hay "giá trị null").
 *   - ⚠ Chỉ từng thao tác riêng lẻ là atomic. CHUỖI thao tác (check-then-act) thì KHÔNG
 *     → dùng thao tác gộp: putIfAbsent, computeIfAbsent, compute, merge.
 *
 * BlockingQueue:
 *   - Hàng đợi thread-safe, put() chờ khi đầy, take() chờ khi rỗng → Producer-Consumer
 *     KHÔNG cần tự viết wait/notify.
 *   - ArrayBlockingQueue (có giới hạn, 1 lock) | LinkedBlockingQueue (tuỳ chọn giới hạn, 2 lock)
 *     | SynchronousQueue (không chứa, bàn giao trực tiếp) | PriorityBlockingQueue (vô hạn, có thứ tự)
 *
 * Chạy: java ConcurrentCollectionsDemo.java
 */
public class ConcurrentCollectionsDemo {

    static final int THREADS = 4;

    static void runInThreads(Runnable task) throws InterruptedException {
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) (ts[i] = new Thread(task)).start();
        for (Thread t : ts) t.join();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ============================================
    // DEMO 1: HashMap không thread-safe
    // ============================================
    static void demo1HashMapUnsafe() throws InterruptedException {
        Map<Integer, Integer> hashMap = new HashMap<>();
        Map<Integer, Integer> chm = new ConcurrentHashMap<>();
        AtomicInteger id = new AtomicInteger();
        final int perThread = 50_000;

        String hashMapResult;
        try {
            runInThreads(() -> {
                int base = id.getAndIncrement() * perThread;
                for (int i = 0; i < perThread; i++) hashMap.put(base + i, i);
            });
            hashMapResult = hashMap.size() + " / " + (THREADS * perThread);
        } catch (RuntimeException e) {
            hashMapResult = "ném " + e.getClass().getSimpleName();
        }

        id.set(0);
        runInThreads(() -> {
            int base = id.getAndIncrement() * perThread;
            for (int i = 0; i < perThread; i++) chm.put(base + i, i);
        });

        boolean lucky = hashMapResult.equals((THREADS * perThread) + " / " + (THREADS * perThread));
        System.out.println("  HashMap           : size = " + hashMapResult
                + (lucky ? "  (lần này may mắn đủ - máy ít core ít bị xen ngang; KHÔNG có nghĩa là an toàn)"
                         : "  ✗ thiếu = mất dữ liệu do ghi đè khi resize/chèn đồng thời"));
        System.out.println("  ConcurrentHashMap : size = " + chm.size() + " / " + (THREADS * perThread) + "  ✓");
        System.out.println("  (Java 7 trở xuống, HashMap song song còn có thể treo vòng lặp vô hạn khi resize.)");
    }

    // ============================================
    // DEMO 2: CHM vẫn dính check-then-act
    // ============================================
    static void demo2CheckThenAct() throws InterruptedException {
        // Đếm từ: 4 thread, mỗi thread tăng key "java" 25.000 lần
        ConcurrentHashMap<String, Integer> wrong = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Integer> right = new ConcurrentHashMap<>();
        final int n = 25_000;

        runInThreads(() -> {
            for (int i = 0; i < n; i++) {
                // ✗ get + put là HAI thao tác riêng: thread khác chen vào giữa → mất lần cộng
                wrong.put("java", wrong.getOrDefault("java", 0) + 1);
            }
        });
        runInThreads(() -> {
            for (int i = 0; i < n; i++) {
                // ✓ merge là MỘT thao tác atomic
                right.merge("java", 1, Integer::sum);
            }
        });

        System.out.println("  put(getOrDefault+1)  : " + wrong.get("java") + " / " + (THREADS * n)
                + (wrong.get("java") == THREADS * n ? "  (may mắn - vẫn SAI)" : "  ✗ CHM thread-safe nhưng logic vẫn sai"));
        System.out.println("  merge(k, 1, sum)     : " + right.get("java") + " / " + (THREADS * n) + "  ✓");
        System.out.println("  → Thread-safe collection ≠ code của bạn thread-safe. Đây là 'race condition không có data race'.");
    }

    // ============================================
    // DEMO 3: computeIfAbsent làm cache
    // ============================================
    static void demo3ComputeIfAbsent() throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger loadCount = new AtomicInteger();

        runInThreads(() -> {
            for (int i = 0; i < 100; i++) {
                cache.computeIfAbsent("config", k -> {
                    loadCount.incrementAndGet();    // giả lập tải tốn kém (DB/HTTP)
                    sleep(20);
                    return "value-of-" + k;
                });
            }
        });
        System.out.println("  400 lượt gọi từ 4 thread → hàm tải chạy " + loadCount.get() + " lần  ✓ (chỉ 1 lần cho mỗi key)");
        System.out.println("  Cách ngây thơ 'if (!containsKey) put(...)' có thể tải nhiều lần và ghi đè nhau.");
        System.out.println("  ⚠ Trong computeIfAbsent: hàm phải NHANH và KHÔNG sửa chính map đó (có thể treo/ném lỗi);");
        System.out.println("    thread khác cần key CÙNG bucket sẽ bị chặn trong lúc hàm chạy.");
    }

    // ============================================
    // DEMO 4: BlockingQueue - Producer/Consumer + back-pressure
    // ============================================
    static void demo4BlockingQueue() throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(3);   // giới hạn 3 → có back-pressure
        final int POISON = -1;                                         // "thuốc độc": tín hiệu kết thúc

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 8; i++) {
                    long t = System.nanoTime();
                    queue.put(i);                                     // đầy thì CHỜ
                    long waited = (System.nanoTime() - t) / 1_000_000;
                    System.out.printf("  [Producer] put(%d)%s%n", i, waited > 50 ? "  ← phải chờ " + waited + "ms vì queue đầy" : "");
                }
                queue.put(POISON);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Producer");

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    int v = queue.take();                             // rỗng thì CHỜ
                    if (v == POISON) { System.out.println("  [Consumer] nhận POISON → dừng"); return; }
                    sleep(150);                                       // consumer CHẬM hơn producer
                    System.out.println("  [Consumer] xử lý " + v);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        // Các biến thể thao tác (câu hỏi hay gặp):
        BlockingQueue<String> q = new ArrayBlockingQueue<>(1);
        q.offer("a");
        System.out.println("  offer() khi đầy       → " + q.offer("b") + "  (không chờ, trả false)");
        System.out.println("  offer(timeout) khi đầy → " + q.offer("b", 100, TimeUnit.MILLISECONDS) + "  (chờ có hạn)");
        try { q.add("b"); } catch (IllegalStateException e) { System.out.println("  add() khi đầy         → ném IllegalStateException"); }
        System.out.println("  → put/take: chặn | offer/poll: trả ngay hoặc chờ có hạn | add/remove: ném exception");
        System.out.println("  ⚠ Queue VÔ HẠN (new LinkedBlockingQueue<>()) = không có back-pressure → producer nhanh hơn sẽ gây OOM.");
    }

    // ============================================
    // DEMO 5: CopyOnWriteArrayList vs ArrayList khi duyệt
    // ============================================
    static void demo5CopyOnWrite() {
        List<Integer> arr = new ArrayList<>(List.of(1, 2, 3));
        try {
            for (Integer x : arr) if (x == 2) arr.add(99);
        } catch (ConcurrentModificationException e) {
            System.out.println("  ArrayList vừa duyệt vừa sửa → ConcurrentModificationException (fail-fast)");
        }

        List<Integer> cow = new CopyOnWriteArrayList<>(List.of(1, 2, 3));
        for (Integer x : cow) if (x == 2) cow.add(99);
        System.out.println("  CopyOnWriteArrayList → duyệt trên ẢNH CHỤP cũ, không lỗi; sau đó list = " + cow);
        System.out.println("  Mỗi lần ghi = copy toàn bộ mảng → chỉ hợp khi ĐỌC rất nhiều, GHI rất ít (vd: danh sách listener).");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== CONCURRENT COLLECTIONS DEMO ===\n");

        System.out.println("--- Demo 1: HashMap vs ConcurrentHashMap ---");
        demo1HashMapUnsafe();

        System.out.println("\n--- Demo 2: CHM vẫn dính check-then-act ---");
        demo2CheckThenAct();

        System.out.println("\n--- Demo 3: computeIfAbsent ---");
        demo3ComputeIfAbsent();

        System.out.println("\n--- Demo 4: BlockingQueue Producer-Consumer ---");
        demo4BlockingQueue();

        System.out.println("\n--- Demo 5: CopyOnWriteArrayList ---");
        demo5CopyOnWrite();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: ConcurrentHashMap thread-safe thế nào? Khác Hashtable / synchronizedMap?");
        System.out.println("A: Hashtable/synchronizedMap khoá TOÀN BẢNG cho mỗi thao tác. CHM (Java 8+) get không khoá,");
        System.out.println("   put dùng CAS + synchronized trên từng bucket → nhiều thread ghi song song. Không cho null key/value.");

        System.out.println("\nQ: ConcurrentHashMap có đảm bảo code đúng không?");
        System.out.println("A: Chỉ từng thao tác đơn là atomic. check-then-act (containsKey rồi put, get rồi put) vẫn race.");
        System.out.println("   Dùng putIfAbsent / computeIfAbsent / compute / merge.");

        System.out.println("\nQ: Vì sao CHM không cho null?");
        System.out.println("A: Trong môi trường đồng thời, get() trả null không phân biệt được 'không có key' và 'value là null',");
        System.out.println("   mà containsKey() rồi get() lại là hai bước không atomic.");

        System.out.println("\nQ: BlockingQueue dùng để làm gì? Khác wait/notify?");
        System.out.println("A: Producer-Consumer: put chờ khi đầy, take chờ khi rỗng. Đóng gói sẵn lock + condition,");
        System.out.println("   ít lỗi hơn tự viết wait/notify. Dùng queue CÓ GIỚI HẠN để có back-pressure.");

        System.out.println("\nQ: Dừng consumer thế nào?");
        System.out.println("A: Poison pill (gửi giá trị đặc biệt), hoặc interrupt() consumer đang block ở take().");

        System.out.println("\nQ: ThreadPoolExecutor liên quan gì BlockingQueue?");
        System.out.println("A: workQueue của pool chính là một BlockingQueue; worker thread take() task từ đó.");
    }
}
