package v2;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CHẨN ĐOÁN SỰ CỐ: thread dump, CPU cao, pool đầy
 *
 * Quy trình khi production có sự cố (nhớ theo thứ tự):
 *   1. Quan sát triệu chứng : CPU cao? latency tăng? request treo? hết connection?
 *   2. Lấy thread dump      : jcmd <pid> Thread.print   (hoặc jstack <pid>, hoặc kill -3 <pid>)
 *                             → lấy 3 lần cách nhau ~5-10 giây để so sánh (thread nào đứng yên một chỗ?)
 *   3. Đọc trạng thái       : RUNNABLE | BLOCKED | WAITING | TIMED_WAITING
 *   4. Tìm thủ phạm theo triệu chứng (bảng bên dưới)
 *
 *   Triệu chứng                    Thread dump thường thấy                         Nguyên nhân hay gặp
 *   -----------------------------  ----------------------------------------------  ----------------------------------
 *   CPU 100%                       vài thread RUNNABLE cùng một chỗ                 vòng lặp vô hạn, regex xấu, GC
 *   CPU thấp nhưng request treo    nhiều thread BLOCKED/WAITING                     lock contention, deadlock, pool đầy
 *   Pool đầy / queue phình         worker WAITING ở Future.get/join/take            task chờ task cùng pool (starvation),
 *                                                                                  downstream chậm (DB/HTTP không timeout)
 *   Hết connection                 nhiều thread chờ ở getConnection()               giữ connection quá lâu, leak
 *
 *   Tìm thread "ngốn CPU" trên Linux:
 *       top -H -p <pid>              → lấy TID (số thập phân) của thread nóng
 *       printf '%x\n' <TID>          → đổi sang hex
 *       jstack <pid> | grep -A 20 nid=0x<hex>   → thấy stack của chính thread đó
 *
 * File này mô phỏng 4 sự cố và dùng ThreadMXBean (cùng nguồn dữ liệu với jstack) để "chẩn đoán" ngay trong code.
 *
 * Chạy: java ThreadDiagnosisDemo.java
 */
public class ThreadDiagnosisDemo {

    static final ThreadMXBean MX = ManagementFactory.getThreadMXBean();

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static ThreadFactory namedFactory(String prefix) {
        AtomicInteger n = new AtomicInteger(1);
        return r -> { Thread t = new Thread(r, prefix + "-" + n.getAndIncrement()); t.setDaemon(true); return t; };
    }

    /** In dạng rút gọn của thread dump cho các thread có tên bắt đầu bằng prefix. */
    static void printThreads(String prefix, int frames) {
        for (ThreadInfo ti : MX.dumpAllThreads(true, true)) {
            if (!ti.getThreadName().startsWith(prefix)) continue;
            System.out.println("    \"" + ti.getThreadName() + "\" state=" + ti.getThreadState()
                    + (ti.getLockName() != null ? "  chờ lock=" + ti.getLockName() : "")
                    + (ti.getLockOwnerName() != null ? "  (đang bị giữ bởi \"" + ti.getLockOwnerName() + "\")" : ""));
            StackTraceElement[] st = ti.getStackTrace();
            for (int i = 0; i < Math.min(frames, st.length); i++) System.out.println("        at " + st[i]);
        }
    }

    // ============================================
    // SỰ CỐ 1: CPU cao - tìm thread nóng
    // ============================================
    static volatile boolean stopBusy = false;

    static void case1HighCpu() {
        // Thủ phạm: thread lặp vô hạn (giả lập regex catastrophic backtracking / vòng lặp lỗi)
        Thread busy = new Thread(() -> {
            long x = 0;
            while (!stopBusy) x += System.nanoTime() % 7;
            if (x == 42) System.out.println();
        }, "report-generator-7");
        busy.setDaemon(true);
        busy.start();
        sleep(100);

        // Giống 'top -H': đo CPU time của MỌI thread trong khoảng 600ms, xếp hạng
        Map<Long, Long> before = new HashMap<>();
        for (long id : MX.getAllThreadIds()) before.put(id, MX.getThreadCpuTime(id));
        sleep(600);

        List<long[]> deltas = new ArrayList<>();   // {threadId, deltaNanos}
        for (long id : MX.getAllThreadIds()) {
            long b = before.getOrDefault(id, 0L), a = MX.getThreadCpuTime(id);
            if (a >= 0 && b >= 0) deltas.add(new long[]{id, a - b});
        }
        deltas.sort(Comparator.comparingLong((long[] d) -> d[1]).reversed());

        System.out.println("  Top thread theo CPU trong 600ms vừa rồi (giống top -H):");
        for (int i = 0; i < Math.min(3, deltas.size()); i++) {
            ThreadInfo ti = MX.getThreadInfo(deltas.get(i)[0], 4);
            if (ti == null) continue;
            System.out.printf("    #%d \"%s\" dùng %d ms CPU, state=%s%n", i + 1, ti.getThreadName(),
                    deltas.get(i)[1] / 1_000_000, ti.getThreadState());
            if (i == 0) for (StackTraceElement e : ti.getStackTrace()) System.out.println("        at " + e);
        }
        System.out.println("  → Thread nóng nhất + stack của nó chỉ thẳng vào dòng code gây ra. Tên thread có ý nghĩa giúp ích rất nhiều!");
        stopBusy = true;
    }

    // ============================================
    // SỰ CỐ 2: Pool đầy - starvation (task chờ task cùng pool)
    // ============================================
    static void case2PoolStarvation() throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10), namedFactory("order-pool"));

        // 2 task "ngoài" chiếm hết 2 thread, mỗi task submit 1 task "trong" vào CHÍNH pool đó rồi chờ kết quả.
        for (int i = 0; i < 2; i++) {
            pool.execute(() -> {
                Future<String> inner = pool.submit(() -> "enrich-data");
                try { inner.get(); } catch (InterruptedException | ExecutionException ignored) { }
            });
        }
        sleep(500);

        System.out.printf("  Số liệu pool: poolSize=%d, active=%d, queue=%d, completed=%d%n",
                pool.getPoolSize(), pool.getActiveCount(), pool.getQueue().size(), pool.getCompletedTaskCount());
        System.out.println("  → active = poolSize (kẹt hết), queue > 0 mà completed không tăng = dấu hiệu STARVATION.");
        System.out.println("  Thread dump của các worker:");
        printThreads("order-pool", 4);
        System.out.println("  → Cả 2 worker ở WAITING trong Future.get(); task 'trong' nằm ở queue chờ thread rảnh → không bao giờ có.");
        System.out.println("  Cách xử lý: tách pool cho 2 tầng việc / dùng CompletableFuture.thenCompose / đừng get() trong pool.");
        pool.shutdownNow();
    }

    // ============================================
    // SỰ CỐ 3: Lock contention - ai đang giữ khoá?
    // ============================================
    static void case3LockContention() throws InterruptedException {
        Object lock = new Object();
        Thread holder = new Thread(() -> {
            synchronized (lock) { sleep(1500); }       // giữ khoá và làm việc chậm (vd: gọi HTTP bên trong synchronized)
        }, "slow-holder");
        holder.setDaemon(true);
        holder.start();
        sleep(100);

        for (int i = 1; i <= 3; i++) {
            Thread w = new Thread(() -> { synchronized (lock) { /* vào được thì làm gì đó */ } }, "request-" + i);
            w.setDaemon(true);
            w.start();
        }
        sleep(300);

        System.out.println("  Thread dump:");
        printThreads("slow-holder", 3);
        printThreads("request-", 2);
        System.out.println("  → 3 request BLOCKED cùng chờ một lock, cùng 'owner' là slow-holder.");
        System.out.println("    Bản thân slow-holder lại TIMED_WAITING (đang sleep/IO) trong khi VẪN GIỮ lock → đó là lỗi thiết kế:");
        System.out.println("    không thực hiện IO/chờ lâu bên trong khối synchronized.");
    }

    // ============================================
    // SỰ CỐ 4: Deadlock - phát hiện tự động
    // ============================================
    static void case4Deadlock() throws InterruptedException {
        Object a = new Object(), b = new Object();
        Thread t1 = new Thread(() -> { synchronized (a) { sleep(150); synchronized (b) { } } }, "transfer-A-to-B");
        Thread t2 = new Thread(() -> { synchronized (b) { sleep(150); synchronized (a) { } } }, "transfer-B-to-A");
        t1.setDaemon(true); t2.setDaemon(true);
        t1.start(); t2.start();
        sleep(500);

        long[] ids = MX.findDeadlockedThreads();    // jstack cũng in "Found one Java-level deadlock" ở cuối dump
        System.out.println("  findDeadlockedThreads() → " + (ids == null ? "không có" : ids.length + " thread bị deadlock"));
        if (ids != null) {
            for (ThreadInfo ti : MX.getThreadInfo(ids, true, true)) {
                System.out.println("    \"" + ti.getThreadName() + "\" đang giữ " + ti.getLockedMonitors().length
                        + " monitor, chờ " + ti.getLockName() + " do \"" + ti.getLockOwnerName() + "\" giữ");
            }
        }
        System.out.println("  → Vòng chờ A→B→A. Sửa: luôn lấy khoá theo MỘT thứ tự cố định, hoặc dùng tryLock có timeout.");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== CHẨN ĐOÁN SỰ CỐ DEMO ===");
        System.out.println("(pid của JVM này: " + ProcessHandle.current().pid()
                + " — trong lúc chạy, thử: jcmd " + ProcessHandle.current().pid() + " Thread.print)\n");

        System.out.println("--- Sự cố 1: CPU cao ---");
        case1HighCpu();

        System.out.println("\n--- Sự cố 2: Pool đầy / starvation ---");
        case2PoolStarvation();

        System.out.println("\n--- Sự cố 3: Lock contention ---");
        case3LockContention();

        System.out.println("\n--- Sự cố 4: Deadlock ---");
        case4Deadlock();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Production CPU 100%, bạn làm gì?");
        System.out.println("A: top -H -p <pid> tìm TID nóng nhất → đổi hex → jstack/jcmd Thread.print, tìm nid=0x... xem stack.");
        System.out.println("   Lấy vài lần cách nhau vài giây. Nếu là các thread GC thì xem log/metric GC thay vì code.");

        System.out.println("\nQ: Request treo nhưng CPU thấp?");
        System.out.println("A: Lấy thread dump, xem nhiều thread BLOCKED/WAITING ở đâu: lock contention (BLOCKED, xem owner),");
        System.out.println("   deadlock (jstack báo 'Found one Java-level deadlock'), pool đầy (worker WAITING ở get/join),");
        System.out.println("   chờ DB/HTTP (thread ở socketRead/getConnection → thiếu timeout hoặc connection pool cạn).");

        System.out.println("\nQ: Các trạng thái thread? BLOCKED khác WAITING?");
        System.out.println("A: BLOCKED = chờ vào khối synchronized (monitor). WAITING = chờ vô hạn (wait/join/park/Future.get).");
        System.out.println("   TIMED_WAITING = chờ có hạn (sleep/wait(t)). RUNNABLE = đang chạy HOẶC đang chờ IO ở mức OS.");

        System.out.println("\nQ: Dấu hiệu thread pool bị đầy/starvation?");
        System.out.println("A: active == maximumPoolSize, queue tăng, completedTaskCount đứng yên, worker WAITING ở Future.get.");
        System.out.println("   Phòng: queue có giới hạn + rejection policy, đặt tên thread, expose metric (active/queue/rejected), tách pool.");

        System.out.println("\nQ: Vì sao cần đặt tên thread?");
        System.out.println("A: Thread dump liệt kê theo tên. 'pool-3-thread-12' không cho biết gì; 'order-pool-12' chỉ thẳng module nào lỗi.");
    }
}
