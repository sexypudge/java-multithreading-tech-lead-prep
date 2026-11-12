# 04 — Concurrent Collections: ConcurrentHashMap, BlockingQueue, CopyOnWriteArrayList

**Source:** `ConcurrentCollectionsDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 HashMap dùng chung giữa nhiều thread (Demo 1)

- Kết quả: **mất dữ liệu**, exception, hoặc **treo 100% CPU** (cấu trúc bucket/tree bin hỏng → `put()` quay vô hạn). Quan sát được cả trên **Java 21**, không chỉ Java 7.
- Dấu hiệu production: CPU 100% + thread dump thấy `RUNNABLE` trong `HashMap.put` / `TreeNode`.

### 1.2 ConcurrentHashMap (CHM)

| | `Hashtable` / `synchronizedMap` | `ConcurrentHashMap` (Java 8+) |
|---|---|---|
| Lock | **Toàn bảng** cho mỗi thao tác | `get()` không lock; `put()` dùng CAS (bucket rỗng) + `synchronized` trên **từng bucket** |
| Ghi song song | ✗ | ✓ |
| null key/value | Hashtable ✗ / synchronizedMap ✓ | **✗** |
| Iterator | fail-fast | **weakly consistent** (không ném CME) |

- **Vì sao cấm null:** `get()` trả `null` không phân biệt "không có key" và "value là null"; `containsKey()` rồi `get()` là 2 bước không atomic.
- `size()` khi có ghi đồng thời chỉ là **ước lượng**.

### 1.3 CHM vẫn dính check-then-act (Demo 2)

```java
map.put(k, map.getOrDefault(k, 0) + 1);   // ✗ get + put = 2 thao tác, thread khác chen giữa
map.merge(k, 1, Integer::sum);            // ✓ 1 thao tác atomic
```

→ **Thread-safe collection ≠ code của bạn thread-safe.** Dùng thao tác gộp: `putIfAbsent`, `computeIfAbsent`, `compute`, `merge`.

### 1.4 `computeIfAbsent` làm cache (Demo 3)

- 400 lượt gọi từ 4 thread → hàm tải chỉ chạy **1 lần** cho mỗi key.
- ⚠ Hàm phải **nhanh** và **không sửa chính map đó** (có thể treo / ném `IllegalStateException`). Thread khác cần key **cùng bucket** bị chặn trong lúc hàm chạy.

### 1.5 BlockingQueue (Demo 4)

- Producer-Consumer **không cần tự viết wait/notify**: `put()` chờ khi đầy, `take()` chờ khi rỗng.
- Queue **có giới hạn** = **back-pressure** (producer nhanh phải chờ consumer chậm). Queue vô hạn → OOM.
- Dừng consumer: **poison pill** hoặc `interrupt()` thread đang block ở `take()`.

| Thao tác | Khi đầy/rỗng |
|---|---|
| `put` / `take` | **chặn** |
| `offer` / `poll` | trả ngay `false`/`null` |
| `offer(t)` / `poll(t)` | chờ có hạn |
| `add` / `remove` | ném exception |

| Loại | Đặc điểm |
|---|---|
| `ArrayBlockingQueue` | có giới hạn, 1 lock |
| `LinkedBlockingQueue` | tuỳ chọn giới hạn (mặc định vô hạn!), 2 lock (put/take) |
| `SynchronousQueue` | không chứa, bàn giao trực tiếp |
| `PriorityBlockingQueue` | vô hạn, có thứ tự |

`workQueue` của `ThreadPoolExecutor` chính là một `BlockingQueue`.

### 1.6 CopyOnWriteArrayList (Demo 5)

- `ArrayList` vừa duyệt vừa sửa → `ConcurrentModificationException` (fail-fast).
- COW: mỗi lần ghi **copy toàn bộ mảng**; iterator duyệt trên **ảnh chụp cũ** → không lỗi.
- Chỉ hợp khi **đọc rất nhiều, ghi rất ít** (danh sách listener, config).

### 1.7 Map/Set có thứ tự, thread-safe

`ConcurrentSkipListMap` / `ConcurrentSkipListSet`: lock-free, O(log n), có `firstKey`, `headMap`... Không có `ConcurrentTreeMap`.

---

## 2. Interview English

### Q1. What happens if several threads share a `HashMap`?

**Importance:** 🔴 Important

**Key sentence:** *You can lose data, get exceptions, or even hang at 100% CPU.*

**Suggested Answer:**
`HashMap` is not thread-safe. Concurrent writes, especially during a resize, can **lose entries**, throw exceptions, or **corrupt the internal structure** so that `put()` loops forever at 100% CPU. It's not only a Java 7 problem — I reproduced the hang on Java 21. In production, the typical sign is high CPU and a thread dump with threads `RUNNABLE` inside `HashMap.put`.

### Q2. How is `ConcurrentHashMap` thread-safe? How is it different from `Hashtable`?

**Importance:** 🔴 Important

**Key sentence:** *Hashtable locks the whole table; ConcurrentHashMap locks only one bucket and reads without locking.*

**Suggested Answer:**
`Hashtable` and `Collections.synchronizedMap` lock the **whole map** for every operation, so only one thread works at a time. Since Java 8, `ConcurrentHashMap` reads **without locking**, and writes use **CAS** for an empty bucket or lock **only that bucket**. So many threads can write in parallel. Its iterators are **weakly consistent** and don't throw `ConcurrentModificationException`.

### Q3. If `ConcurrentHashMap` is thread-safe, is my code automatically correct?

**Importance:** 🔴 Important

**Key sentence:** *Each operation is atomic, but a sequence of operations is not.*

**Suggested Answer:**
No. Each single call like `get` or `put` is atomic, but **check-then-act** — for example `get` and then `put` — is two calls, and another thread can run in between. That's a race condition **without a data race**. In my demo, counting with `put(getOrDefault + 1)` lost updates. The fix is to use **atomic compound methods**: `merge`, `compute`, `computeIfAbsent`, or `putIfAbsent`.

### Q4. How would you build a simple cache with `ConcurrentHashMap`?

**Importance:** 🟡 Medium

**Key sentence:** *computeIfAbsent loads each key once — but keep the function fast and don't touch the map inside it.*

**Suggested Answer:**
I use `computeIfAbsent(key, k -> load(k))`. It runs the loader **only once per key**, even when many threads ask at the same time, unlike `if (!containsKey) put`, which can load several times. But the function runs **while holding the bucket lock**, so it must be fast, and it must **not modify the same map**, or it can hang or throw.

### Q5. Why doesn't `ConcurrentHashMap` allow null?

**Importance:** 🟡 Medium

**Key sentence:** *A null result would be ambiguous: missing key or null value?*

**Suggested Answer:**
If `get()` returns `null`, we can't tell whether the key is **missing** or the value **is null**. In a single-threaded map we could call `containsKey`, but in a concurrent map, `containsKey` and then `get` are two separate steps, and the map can change in between. So `ConcurrentHashMap` simply **forbids null** keys and values to avoid this ambiguity.

### Q6. What is a `BlockingQueue` used for? How is it better than wait/notify?

**Importance:** 🔴 Important

**Key sentence:** *It's a ready-made producer-consumer buffer; a bounded one gives back-pressure.*

**Suggested Answer:**
It's for **producer-consumer**. `put()` waits when the queue is full and `take()` waits when it's empty. It already contains the **lock and conditions**, so it's much less error-prone than writing wait/notify myself. I always use a **bounded** queue: when consumers are slow, producers have to wait, which is **back-pressure**. An unbounded queue just grows until OOM. To stop a consumer, I send a **poison pill** or interrupt it.

### Q7. When would you use `CopyOnWriteArrayList`?

**Importance:** 🟡 Medium

**Key sentence:** *When reads vastly outnumber writes, like a list of listeners.*

**Suggested Answer:**
Every write **copies the whole array**, and iterators work on a **snapshot**, so you can iterate while other threads modify the list, with no `ConcurrentModificationException` and no locking for reads. But writes are expensive, so it only fits when reads are **very frequent and writes are rare** — for example, a list of event listeners or configuration.

### Q8. Are `size()` and iterators of `ConcurrentHashMap` accurate? What if I need a sorted concurrent map?

**Importance:** 🟢 Low

**Key sentence:** *They are weakly consistent; for sorting use ConcurrentSkipListMap.*

**Suggested Answer:**
They are **weakly consistent**. An iterator may or may not see changes made during iteration, and `size()` is only an **estimate** while other threads are writing. That's fine for monitoring, not for strict logic. If I need a **sorted** thread-safe map, I use `ConcurrentSkipListMap`, which is lock-free and O(log n). There's no `ConcurrentTreeMap`.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | A shared HashMap can lose data or hang at 100% CPU. |
| 2 | ConcurrentHashMap locks per bucket and reads without locking. |
| 3 | Each operation is atomic; check-then-act is not — use merge or compute. |
| 4 | A bounded BlockingQueue gives back-pressure. |
| 5 | CopyOnWriteArrayList is for many reads and rare writes. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| check-then-act | kiểm tra rồi hành động (2 bước) |
| compound operation | thao tác gộp |
| bucket / bin | ô trong bảng băm |
| weakly consistent | nhất quán yếu |
| fail-fast iterator | iterator ném lỗi khi bị sửa |
| poison pill | giá trị đặc biệt báo dừng |
| snapshot | ảnh chụp tại một thời điểm |

## 5. Self-test

1. `HashMap` dùng chung có thể gây ra những lỗi gì? Dấu hiệu trong thread dump?
2. CHM khác `Hashtable` / `synchronizedMap` thế nào?
3. Vì sao `put(getOrDefault + 1)` trên CHM vẫn sai? Sửa bằng gì?
4. `computeIfAbsent` có lưu ý gì?
5. Vì sao CHM cấm null?
6. `put/take` vs `offer/poll` vs `add/remove`? Vì sao phải dùng queue có giới hạn?
7. Dừng consumer thế nào?
8. `CopyOnWriteArrayList` hợp khi nào? Không hợp khi nào?
