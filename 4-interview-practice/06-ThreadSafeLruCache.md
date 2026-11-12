# 06 — ThreadSafeLruCache: LRU O(1) và bẫy ReadWriteLock

**Source:** `ThreadSafeLruCache.java` · **Priority:** P2
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

> File **P2** — học sau cùng. Riêng **bẫy ReadWriteLock** (Q2) rất đáng nhớ vì áp dụng được cho nhiều chỗ khác.

---

## 1. Technical Knowledge

### 1.1 Lời giải chuẩn

```java
map = new LinkedHashMap<>(16, 0.75f, true) {              // true = thứ tự theo lần TRUY CẬP
    protected boolean removeEldestEntry(Map.Entry<K, V> e) {
        return size() > capacity;                          // tự đẩy entry ít dùng nhất
    }
};
public V get(K key) { lock.lock(); try { return map.get(key); } finally { lock.unlock(); } }
```

- `LinkedHashMap` = hash map + **danh sách liên kết đôi** → get/put O(1).
- `accessOrder = true` → mỗi lần `get` đưa entry lên **cuối** danh sách.
- Một `ReentrantLock` bảo vệ **mọi** thao tác, kể cả `get`, `size`, `toString`.

Kiểm tra: put a, b, c → get a → put d ⇒ **b** bị đẩy ra.

### 1.2 Bẫy ReadWriteLock

- Suy nghĩ "đọc nhiều → `get()` lấy read lock" là **SAI**.
- Với `accessOrder = true`, `get()` **sửa** danh sách liên kết. Nhiều thread cùng `get()` dưới read lock = nhiều thread cùng **ghi** cấu trúc → hỏng danh sách, NPE, **vòng lặp vô hạn**.
- Demo `BrokenRwLruCache`: 8 thread, 90% get → lỗi / size sai / cấu trúc không nhất quán / treo.
- Bài học: "thao tác nào thật sự là đọc?" phải **xem implementation**, không đoán theo tên method.

### 1.3 Follow-up

| Vấn đề | Hướng giải |
|---|---|
| Lock toàn cục thành nút thắt | **Shard** theo `hash(key)`: mỗi shard 1 LRU + 1 lock, chấp nhận LRU gần đúng |
| Cần TTL, hit rate | **Caffeine**: `expireAfterWrite`, `recordStats` |
| Cache stampede (nhiều thread cùng load 1 key) | `ConcurrentHashMap.computeIfAbsent` / `Caffeine.get(key, loader)` |

Production: dùng **Caffeine** (W-TinyLFU, phần lớn lock-free, có TTL, metrics) thay vì tự viết.

---

## 2. Interview English

### Q1. How do you implement a thread-safe LRU cache with O(1) operations?

**Importance:** 🟡 Medium

**Key sentence:** *LinkedHashMap in access order with removeEldestEntry, guarded by one lock.*

**Suggested Answer:**
I use a `LinkedHashMap` with **access order** set to true, which keeps a doubly linked list ordered by last access, so get and put are O(1). I override **`removeEldestEntry`** to return true when the size exceeds the capacity, so the least recently used entry is evicted automatically. Then I protect **every method**, including `get`, with one `ReentrantLock`, because `LinkedHashMap` itself isn't thread-safe.

### Q2. Reads are much more frequent. Why not use a `ReadWriteLock` with a read lock for `get()`?

**Importance:** 🔴 Important

**Key sentence:** *Because in an LRU cache `get()` is a write — it reorders the list.*

**Suggested Answer:**
Because in access-order mode, `get()` **modifies** the internal linked list — it moves the entry to the end. A read lock lets **many threads in at the same time**, so they'd all modify the list concurrently and **corrupt** it: wrong size, exceptions, even infinite loops. My demo shows exactly that. The lesson is to check what an operation **really does** in the implementation, not to trust that `get` means read-only.

### Q3. The single lock becomes a bottleneck. What can you do?

**Importance:** 🟡 Medium

**Key sentence:** *Shard the cache by key hash, or use Caffeine.*

**Suggested Answer:**
I can **shard** it: split the cache into N segments by key hash, each with its own LRU and its own lock, so threads working on different keys don't block each other. The trade-off is that eviction becomes **approximately LRU** per shard, not globally. In practice, I'd use **Caffeine**, which is mostly lock-free, uses a better eviction policy, and supports TTL and statistics.

### Q4. How do you avoid many threads loading the same missing key at once?

**Importance:** 🟡 Medium

**Key sentence:** *Use computeIfAbsent or a loading cache so only one thread loads each key.*

**Suggested Answer:**
That's a **cache stampede**: when a popular key expires, many threads miss at the same time and all hit the database. I use `ConcurrentHashMap.computeIfAbsent` or Caffeine's **`get(key, loader)`**, which make sure only **one thread loads** the value while the others wait for that result. For very hot keys, I can also refresh in the background before expiry.

### Q5. Would you write your own cache in production?

**Importance:** 🟢 Low

**Key sentence:** *No — Caffeine is faster, safer and has TTL and metrics.*

**Suggested Answer:**
No. Writing it is a good interview exercise to show I understand locking, but in production I use **Caffeine**. It has better hit rates thanks to W-TinyLFU, it's mostly lock-free, and it supports size limits, **expiry**, refresh, loading and **hit-rate metrics** out of the box. Writing my own means owning all the edge cases.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | LinkedHashMap with access order plus removeEldestEntry gives an O(1) LRU. |
| 2 | In an LRU cache, `get()` is a write. |
| 3 | Check what an operation really does before choosing a read lock. |
| 4 | Shard the cache to reduce lock contention. |
| 5 | Use Caffeine in production. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| least recently used (LRU) | ít được dùng gần đây nhất |
| access order | thứ tự theo lần truy cập |
| evict | đẩy ra khỏi cache |
| doubly linked list | danh sách liên kết đôi |
| shard / segment | chia mảnh |
| cache stampede | nhiều thread cùng load một key |
| hit rate | tỷ lệ trúng cache |

## 5. Self-test

1. Viết LRU cache với `LinkedHashMap` (3 tham số constructor nghĩa là gì?).
2. put a, b, c → get a → put d: entry nào bị đẩy? Vì sao?
3. Vì sao không dùng read lock cho `get()`? Hậu quả cụ thể?
4. Lock toàn cục thành nút thắt thì làm gì? Trade-off?
5. Cache stampede là gì? Tránh thế nào?
6. Vì sao production dùng Caffeine?
