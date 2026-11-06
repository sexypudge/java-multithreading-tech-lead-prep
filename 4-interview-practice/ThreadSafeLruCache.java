import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BÀI TẬP: LRU cache thread-safe, dung lượng cố định, get/put O(1).
 *
 * Lời giải chuẩn: LinkedHashMap(accessOrder = true) + removeEldestEntry, bọc bằng một lock.
 *
 * BẪY HAY GẶP (điểm cộng nếu bạn tự nêu ra): "đọc nhiều → dùng ReadWriteLock, get() lấy read lock".
 *   SAI! Với accessOrder = true, get() SỬA danh sách liên kết (đưa entry lên cuối). Nhiều thread cùng get()
 *   dưới read lock = nhiều thread cùng GHI cấu trúc → hỏng danh sách, NPE, vòng lặp vô hạn.
 *   → "Thao tác nào thực sự là đọc?" phải xem implementation, không đoán theo tên method.
 *
 * Production: dùng Caffeine (gần đúng LRU/W-TinyLFU, lock-free phần lớn, có TTL, metrics) thay vì tự viết.
 *
 * Chạy: java ThreadSafeLruCache.java
 */
public class ThreadSafeLruCache<K, V> {

    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<K, V> map;

    public ThreadSafeLruCache(int capacity) {
        this.map = new LinkedHashMap<>(16, 0.75f, true) {         // true = thứ tự theo lần TRUY CẬP
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;                         // tự đẩy entry ít dùng nhất khi vượt dung lượng
            }
        };
    }

    public V get(K key) {
        lock.lock();                                              // get() cũng là GHI (đổi thứ tự) → exclusive lock
        try {
            return map.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return map.keySet().toString();
        } finally {
            lock.unlock();
        }
    }

    /** ✗ Phiên bản SAI để minh hoạ bẫy ReadWriteLock. */
    static class BrokenRwLruCache<K, V> {
        private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        private final LinkedHashMap<K, V> map;

        BrokenRwLruCache(int capacity) {
            map = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > capacity; }
            };
        }

        V get(K key) {
            rw.readLock().lock();                                 // ✗ nhiều thread cùng "đọc" = cùng sửa linked list
            try { return map.get(key); } finally { rw.readLock().unlock(); }
        }

        void put(K key, V value) {
            rw.writeLock().lock();
            try { map.put(key, value); } finally { rw.writeLock().unlock(); }
        }

        int size() {
            rw.readLock().lock();
            try { return map.size(); } finally { rw.readLock().unlock(); }
        }

        /** Đếm số node khi duyệt danh sách - nếu cấu trúc hỏng sẽ lệch size() hoặc ném exception/treo. */
        boolean looksConsistent() {
            rw.writeLock().lock();
            try {
                int n = 0;
                for (K ignored : map.keySet()) if (++n > map.size() + 1) return false;
                return n == map.size();
            } catch (RuntimeException e) {
                return false;
            } finally {
                rw.writeLock().unlock();
            }
        }
    }

    interface Ops { Object get(int k); void put(int k, int v); }

    static int hammer(Ops cache, int threads) throws InterruptedException {
        AtomicInteger errors = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 200_000; i++) {
                    try {
                        int k = rnd.nextInt(200);
                        if (rnd.nextInt(10) == 0) cache.put(k, k); else cache.get(k);
                    } catch (RuntimeException e) {
                        errors.incrementAndGet();
                    }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            return -1;                                            // treo (vòng lặp vô hạn trong cấu trúc hỏng)
        }
        return errors.get();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== THREAD-SAFE LRU CACHE ===\n");

        System.out.println("--- Hành vi LRU ---");
        ThreadSafeLruCache<String, Integer> lru = new ThreadSafeLruCache<>(3);
        lru.put("a", 1); lru.put("b", 2); lru.put("c", 3);
        lru.get("a");                                             // a vừa được dùng → b thành ít dùng nhất
        lru.put("d", 4);                                          // vượt dung lượng → đẩy b
        System.out.println("  put a,b,c → get a → put d  ⇒ " + lru + (lru.get("b") == null ? "  ✓ b bị đẩy ra" : "  ✗"));

        System.out.println("\n--- Dưới tải đồng thời (8 thread, 90% get) ---");
        ThreadSafeLruCache<Integer, Integer> good = new ThreadSafeLruCache<>(100);
        int goodErrors = hammer(new Ops() {
            public Object get(int k) { return good.get(k); }
            public void put(int k, int v) { good.put(k, v); }
        }, 8);
        System.out.println("  ReentrantLock      : lỗi=" + goodErrors + ", size=" + good.size() + " (≤ 100)  "
                + (goodErrors == 0 && good.size() <= 100 ? "✓" : "✗"));

        BrokenRwLruCache<Integer, Integer> broken = new BrokenRwLruCache<>(100);
        int brokenErrors = hammer(new Ops() {
            public Object get(int k) { return broken.get(k); }
            public void put(int k, int v) { broken.put(k, v); }
        }, 8);
        String verdict = brokenErrors == -1 ? "TREO (cấu trúc hỏng → vòng lặp vô hạn)"
                : "lỗi=" + brokenErrors + ", size=" + broken.size() + ", cấu trúc nhất quán? " + broken.looksConsistent();
        System.out.println("  ReadWriteLock (SAI): " + verdict
                + (brokenErrors != 0 || !broken.looksConsistent() || broken.size() > 100 ? "  ✗" : "  (may mắn lần này - vẫn SAI)"));

        System.out.println("\nCâu hỏi follow-up:");
        System.out.println("  - Lock toàn cục thành nút thắt? → chia shard theo hash(key) (mỗi shard 1 LRU + 1 lock), chấp nhận LRU gần đúng.");
        System.out.println("  - Cần TTL / thống kê hit rate? → Caffeine: expireAfterWrite, recordStats.");
        System.out.println("  - Tránh nhiều thread cùng load 1 key (cache stampede)? → ConcurrentHashMap.computeIfAbsent / Caffeine.get(key, loader).");
    }
}
