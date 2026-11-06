import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * FORK/JOIN FRAMEWORK & PARALLEL STREAMS - DIVIDE AND CONQUER
 *
 * ForkJoinPool khác ThreadPoolExecutor ở chỗ:
 *   - Mỗi worker có DEQUE riêng. Task con được fork() vào đầu deque của chính worker đó.
 *   - WORK STEALING: worker rảnh "ăn trộm" task từ ĐUÔI deque của worker khác → cân bằng tải tự động.
 *   - join() không chặn ngây ngô: worker đang chờ sẽ đi chạy task khác trong lúc đó.
 *
 * Dùng khi: bài toán CPU-bound chia nhỏ đệ quy được (sum, sort, duyệt cây, xử lý ảnh/ma trận).
 * KHÔNG dùng khi: task I/O-bound / blocking (dùng executor riêng hoặc virtual thread).
 *
 * parallelStream() và CompletableFuture.supplyAsync() (không truyền executor) đều chạy trên
 * ForkJoinPool.commonPool() - DÙNG CHUNG cả JVM, kích thước ≈ số core - 1.
 *
 * Interview Question: "Fork/Join vs ExecutorService? Khi nào dùng parallel stream?"
 * Answer: Fork/Join cho task đệ quy CPU-bound, có work stealing. ExecutorService cho các task độc lập.
 *         Parallel stream chỉ khi dữ liệu LỚN, thao tác CPU-bound, không side effect, nguồn dễ chia
 *         (array/ArrayList/range) - và đã ĐO thấy nhanh hơn.
 *
 * Chạy: java ForkJoinDemo.java
 */
public class ForkJoinDemo {

    // ============================================
    // RECURSIVETASK - có kết quả
    // ============================================
    static class SumTask extends RecursiveTask<Long> {
        private final long[] array;
        private final int start, end, threshold;

        SumTask(long[] array, int start, int end, int threshold) {
            this.array = array;
            this.start = start;
            this.end = end;
            this.threshold = threshold;
        }

        @Override
        protected Long compute() {
            if (end - start <= threshold) {             // đủ nhỏ → tính tuần tự
                long sum = 0;
                for (int i = start; i < end; i++) sum += array[i];
                return sum;
            }
            int mid = (start + end) >>> 1;
            SumTask left = new SumTask(array, start, mid, threshold);
            SumTask right = new SumTask(array, mid, end, threshold);

            left.fork();                                // đẩy nửa trái vào deque (worker khác có thể trộm)
            long rightResult = right.compute();         // tự làm nửa phải trên thread hiện tại
            long leftResult = left.join();              // rồi mới join nửa trái
            return leftResult + rightResult;
            // ✗ Sai thường gặp: left.fork(); right.fork(); left.join(); right.join();
            //   → thread hiện tại ngồi không; hoặc join() TRƯỚC compute() → mất song song.
        }
    }

    // ============================================
    // RECURSIVEACTION - không kết quả
    // ============================================
    static class SquareAction extends RecursiveAction {
        private final long[] array;
        private final int start, end;

        SquareAction(long[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            if (end - start <= 10_000) {
                for (int i = start; i < end; i++) array[i] = array[i] * array[i];
                return;
            }
            int mid = (start + end) >>> 1;
            invokeAll(new SquareAction(array, start, mid), new SquareAction(array, mid, end));
        }
    }

    static long timeMs(Runnable r) {
        long t = System.nanoTime();
        r.run();
        return (System.nanoTime() - t) / 1_000_000;
    }

    public static void main(String[] args) {
        System.out.println("=== FORK/JOIN & PARALLEL STREAMS DEMO ===\n");
        System.out.println("availableProcessors = " + Runtime.getRuntime().availableProcessors()
                + ", commonPool parallelism = " + ForkJoinPool.getCommonPoolParallelism() + "\n");

        long[] data = new long[20_000_000];
        Arrays.fill(data, 1);

        // ============================================
        // DEMO 1: RecursiveTask + ảnh hưởng của threshold
        // ============================================
        System.out.println("--- Demo 1: RecursiveTask sum + threshold ---");
        try (ForkJoinPool pool = new ForkJoinPool()) {   // ForkJoinPool là AutoCloseable từ Java 19
            // Warm-up: để JIT biên dịch trước khi đo (đo micro-benchmark nghiêm túc → dùng JMH)
            for (int i = 0; i < 3; i++) pool.invoke(new SumTask(data, 0, data.length, 10_000));

            long[] seq = new long[1];
            System.out.printf("  Tuần tự             : %4d ms%n", timeMs(() -> {
                long s = 0;
                for (long v : data) s += v;
                seq[0] = s;
            }));
            for (int threshold : new int[]{100, 10_000, 1_000_000}) {
                long[] result = new long[1];
                long ms = timeMs(() -> result[0] = pool.invoke(new SumTask(data, 0, data.length, threshold)));
                System.out.printf("  Fork/Join thr=%-9s: %4d ms (sum=%d)%n", String.format("%,d", threshold), ms, result[0]);
            }
            System.out.println("  → Threshold quá nhỏ: chi phí tạo task lấn át; quá lớn: không đủ song song.");
            System.out.println("    Với phép cộng đơn giản, băng thông bộ nhớ là nút thắt → speedup thường khiêm tốn.");

            // ============================================
            // DEMO 2: RecursiveAction
            // ============================================
            System.out.println("\n--- Demo 2: RecursiveAction ---");
            long[] arr = LongStream.rangeClosed(1, 1_000_000).toArray();
            pool.invoke(new SquareAction(arr, 0, arr.length));
            System.out.println("  arr[0]=" + arr[0] + ", arr[9]=" + arr[9] + ", arr[last]=" + arr[arr.length - 1]);
        }

        // ============================================
        // DEMO 3: Parallel stream - khi nào có lợi
        // ============================================
        System.out.println("\n--- Demo 3: Parallel stream ---");
        // CPU-bound, dữ liệu lớn, nguồn dễ chia → có lợi
        long seqMs = timeMs(() -> LongStream.range(0, 30_000_000).map(x -> x * x % 7).sum());
        long parMs = timeMs(() -> LongStream.range(0, 30_000_000).parallel().map(x -> x * x % 7).sum());
        System.out.println("  CPU-bound 30M phần tử: sequential " + seqMs + " ms | parallel " + parMs + " ms");

        // Dữ liệu nhỏ → chi phí chia + gộp lớn hơn lợi ích
        List<Integer> small = IntStream.range(0, 100).boxed().toList();
        long smallSeq = timeMs(() -> { for (int i = 0; i < 2000; i++) small.stream().mapToInt(x -> x).sum(); });
        long smallPar = timeMs(() -> { for (int i = 0; i < 2000; i++) small.parallelStream().mapToInt(x -> x).sum(); });
        System.out.println("  100 phần tử × 2000 lần: sequential " + smallSeq + " ms | parallel " + smallPar + " ms  ← parallel thường CHẬM hơn");

        // Side effect trong parallel stream → race condition
        List<Integer> unsafe = new java.util.ArrayList<>();
        try {
            IntStream.range(0, 100_000).parallel().forEach(unsafe::add);
            System.out.println("  forEach(list::add) song song → size = " + unsafe.size() + " / 100000  ✗ (ArrayList không thread-safe)");
        } catch (RuntimeException e) {
            System.out.println("  forEach(list::add) song song → " + e.getClass().getSimpleName() + "  ✗");
        }
        List<Integer> safe = IntStream.range(0, 100_000).parallel().boxed().toList();
        System.out.println("  Dùng collect/toList()          → size = " + safe.size() + "  ✓");

        // ============================================
        // DEMO 4: Bẫy commonPool - blocking I/O trong parallel stream
        // ============================================
        System.out.println("\n--- Demo 4: Blocking trong commonPool ---");
        long ioMs = timeMs(() -> IntStream.range(0, 16).parallel().forEach(i -> sleep(200)));
        System.out.println("  16 task 'I/O' 200ms trên parallel stream: " + ioMs + " ms");
        System.out.println("  → Song song bị giới hạn bởi số thread commonPool (+ thread gọi). Tệ hơn: trong lúc đó MỌI");
        System.out.println("    parallelStream / CompletableFuture mặc định khác của cả ứng dụng cũng phải xếp hàng.");
        System.out.println("    I/O → executor riêng có giới hạn, hoặc virtual thread.");

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Fork/Join vs ExecutorService?");
        System.out.println("A: Fork/Join: task đệ quy chia nhỏ, CPU-bound, work stealing, join() không lãng phí thread.");
        System.out.println("   ExecutorService (ThreadPoolExecutor): task độc lập, 1 queue chung, phù hợp I/O với pool riêng.");

        System.out.println("\nQ: Work stealing là gì?");
        System.out.println("A: Mỗi worker có deque riêng, tự lấy task ở đầu; worker rảnh trộm ở đuôi deque của worker khác");
        System.out.println("   → ít tranh chấp, cân bằng tải khi các nhánh đệ quy lớn nhỏ khác nhau.");

        System.out.println("\nQ: Khi nào KHÔNG dùng parallel stream?");
        System.out.println("A: Dữ liệu nhỏ; thao tác I/O/blocking; có side effect/shared state; nguồn khó chia (LinkedList, iterate);");
        System.out.println("   cần thứ tự (forEachOrdered làm mất lợi ích); trong web server bận (đã đủ song song theo request).");

        System.out.println("\nQ: Muốn parallel stream chạy trên pool riêng?");
        System.out.println("A: pool.submit(() -> list.parallelStream()...).join() - chạy trong ForkJoinPool tự tạo.");
        System.out.println("   (hành vi dựa trên implementation; với I/O tốt hơn là dùng executor/virtual thread thay vì stream).");
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
