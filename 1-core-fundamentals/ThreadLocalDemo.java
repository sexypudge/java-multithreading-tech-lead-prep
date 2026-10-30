import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * THREADLOCAL - MỖI THREAD MỘT BẢN SAO RIÊNG
 *
 * ThreadLocal<T> cho mỗi thread một giá trị độc lập → không cần lock vì không chia sẻ
 * (kỹ thuật "thread confinement").
 *
 * Use cases thực tế:
 *   - Object không thread-safe nhưng tốn kém khi tạo (SimpleDateFormat, Random, buffer)
 *   - Context theo request: userId, traceId (MDC của SLF4J), SecurityContext (Spring Security),
 *     transaction (Spring @Transactional dùng ThreadLocal để giữ connection)
 *
 * BẪY LỚN NHẤT: dùng với THREAD POOL.
 *   Thread trong pool sống rất lâu và được TÁI SỬ DỤNG. Nếu không remove(), giá trị của
 *   request trước "rò" sang request sau (sai dữ liệu / lộ thông tin) và có thể gây memory leak
 *   (ví dụ giữ reference tới ClassLoader khi redeploy).
 *
 * Interview Question: "ThreadLocal gây memory leak thế nào?"
 * Answer: ThreadLocalMap của thread dùng WeakReference cho KEY (ThreadLocal) nhưng giữ
 *         STRONG reference cho VALUE. Khi ThreadLocal bị GC mà thread còn sống (thread pool)
 *         và không gọi remove() thì value vẫn bị giữ.
 */
public class ThreadLocalDemo {

    // ============================================
    // DEMO 1: SimpleDateFormat - không thread-safe
    // ============================================
    static final SimpleDateFormat SHARED_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    static final ThreadLocal<SimpleDateFormat> LOCAL_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

    interface Parser {
        long parse(String s) throws Exception;
    }

    static int countErrors(Parser parser, long expected) throws InterruptedException {
        final int THREADS = 8, LOOPS = 5000;
        AtomicInteger errors = new AtomicInteger();
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < LOOPS; j++) {
                    try {
                        if (parser.parse("2024-01-15") != expected) errors.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet(); // NumberFormatException, ArrayIndexOutOfBounds...
                    }
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        return errors.get();
    }

    static void demo1SimpleDateFormat() throws Exception {
        long expected = new SimpleDateFormat("yyyy-MM-dd").parse("2024-01-15").getTime();

        int shared = countErrors(s -> SHARED_FORMAT.parse(s).getTime(), expected);
        System.out.println("  Dùng chung 1 SimpleDateFormat : " + shared + " lỗi"
                + (shared > 0 ? "  ✗ không thread-safe" : "  (may mắn lần này, vẫn SAI về nguyên tắc)"));

        int local = countErrors(s -> LOCAL_FORMAT.get().parse(s).getTime(), expected);
        System.out.println("  Dùng ThreadLocal              : " + local + " lỗi  ✓");
        System.out.println("  (Thực tế: dùng java.time.DateTimeFormatter - immutable, thread-safe, khỏi cần ThreadLocal)");
    }

    // ============================================
    // DEMO 2: Context theo request
    // ============================================
    static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    static void serviceLayer() {
        // Không cần truyền userId qua mọi tham số
        System.out.println("  [" + Thread.currentThread().getName() + "] service xử lý cho user = " + CURRENT_USER.get());
    }

    static void demo2RequestContext() throws InterruptedException {
        Runnable req1 = () -> {
            CURRENT_USER.set("alice");
            try {
                serviceLayer();
            } finally {
                CURRENT_USER.remove(); // luôn dọn dẹp
            }
        };
        Runnable req2 = () -> {
            CURRENT_USER.set("bob");
            try {
                serviceLayer();
            } finally {
                CURRENT_USER.remove();
            }
        };
        Thread a = new Thread(req1, "Request-1");
        Thread b = new Thread(req2, "Request-2");
        a.start();
        b.start();
        a.join();
        b.join();
    }

    // ============================================
    // DEMO 3: RÒ RỈ DỮ LIỆU TRONG THREAD POOL
    // ============================================
    static void demo3PoolLeak() throws Exception {
        // 1 thread duy nhất → chắc chắn 2 task chạy trên CÙNG một thread
        ExecutorService pool = Executors.newSingleThreadExecutor();

        System.out.println("  -- Không remove() --");
        pool.submit(() -> CURRENT_USER.set("alice")).get(); // request 1: quên remove()
        pool.submit(() -> System.out.println(
                "  Request 2 (chưa set gì) đọc được user = " + CURRENT_USER.get()
                        + "  ✗ DỮ LIỆU CỦA REQUEST TRƯỚC!")).get();

        pool.submit(CURRENT_USER::remove).get(); // dọn tạm để demo tiếp

        System.out.println("  -- Có try/finally remove() --");
        pool.submit(() -> {
            CURRENT_USER.set("alice");
            try {
                // xử lý...
            } finally {
                CURRENT_USER.remove();
            }
        }).get();
        pool.submit(() -> System.out.println(
                "  Request 2 đọc được user = " + CURRENT_USER.get() + "  ✓ sạch")).get();

        pool.shutdown();
    }

    // ============================================
    // DEMO 4: InheritableThreadLocal
    // ============================================
    static final InheritableThreadLocal<String> INHERITED = new InheritableThreadLocal<>();

    static void demo4Inheritable() throws Exception {
        INHERITED.set("v1");
        Thread child = new Thread(() ->
                System.out.println("  Thread con tạo SAU khi set thấy: " + INHERITED.get() + "  (được COPY lúc tạo thread)"));
        child.start();
        child.join();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {}).get();   // worker được tạo ở đây, copy "v1"
        INHERITED.set("v2");           // đổi giá trị ở main
        pool.submit(() -> System.out.println(
                "  Worker của pool thấy: " + INHERITED.get() + "  ✗ vẫn là bản cũ → KHÔNG dùng được để truyền context qua pool")).get();
        pool.shutdown();
        INHERITED.remove();
        System.out.println("  → Muốn truyền context sang pool: bọc Runnable (copy MDC/SecurityContext thủ công),");
        System.out.println("    Spring TaskDecorator, hoặc thư viện như TransmittableThreadLocal.");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== THREADLOCAL DEMO ===\n");

        System.out.println("--- Demo 1: SimpleDateFormat shared vs ThreadLocal ---");
        demo1SimpleDateFormat();

        System.out.println("\n--- Demo 2: Context theo request ---");
        demo2RequestContext();

        System.out.println("\n--- Demo 3: Rò rỉ dữ liệu trong thread pool ---");
        demo3PoolLeak();

        System.out.println("\n--- Demo 4: InheritableThreadLocal ---");
        demo4Inheritable();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: ThreadLocal lưu dữ liệu ở đâu?");
        System.out.println("A: Trong field ThreadLocalMap của chính Thread. key = ThreadLocal (weak), value = strong.");
        System.out.println("   Không phải map toàn cục chia sẻ giữa các thread.");

        System.out.println("\nQ: Khi nào dùng, khi nào tránh?");
        System.out.println("A: Dùng cho context theo request và object đắt, không thread-safe.");
        System.out.println("   Tránh như 'biến global ẩn': khó test, khó theo dõi luồng dữ liệu.");

        System.out.println("\nQ: Quy tắc an toàn?");
        System.out.println("A: Luôn set() trong try và remove() trong finally; khai báo static final.");

        System.out.println("\nQ: Với @Async / thread pool của Spring thì sao?");
        System.out.println("A: SecurityContext, MDC (traceId), RequestContext KHÔNG tự sang thread khác.");
        System.out.println("   Dùng TaskDecorator hoặc DelegatingSecurityContextExecutor để propagate.");

        System.out.println("\nQ: Virtual threads thì ThreadLocal còn dùng được không?");
        System.out.println("A: Dùng được, nhưng với hàng triệu virtual thread, mỗi thread một bản sao rất tốn RAM.");
        System.out.println("   Java 21+ đưa ra ScopedValue (preview) để thay thế cho nhu cầu truyền context.");
    }
}
