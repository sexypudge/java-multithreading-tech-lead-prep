# 06 — volatile

**Source:** `VolatileDemo.java` (học trước `JavaMemoryModelDemo.java`)
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.

---

## 1. Technical Knowledge

### 1.1 Vấn đề

JIT/CPU được phép giữ giá trị trong thanh ghi, **đưa việc đọc ra ngoài vòng lặp**, đổi thứ tự lệnh → thread này có thể **không bao giờ thấy** thay đổi của thread khác.

```java
private boolean running = true;   // KHÔNG volatile
while (running) { count++; }      // JIT có thể biến thành: if (running) while (true) count++;
```

Demo 1: worker **không dừng** dù `running = false`. Nếu lần chạy nào worker dừng được → JIT chưa tối ưu, **code vẫn sai**.

### 1.2 volatile đảm bảo gì?

| Đảm bảo | Không đảm bảo |
|---|---|
| ✓ **Visibility** — mọi thread thấy giá trị mới nhất | ✗ **Atomicity** cho thao tác ghép (`count++` vẫn sai) |
| ✓ **Ordering** — cấm reorder qua volatile read/write | ✗ Mutual exclusion |
| ✓ Đọc/ghi `long`/`double` là atomic | |

Ghi volatile V **happens-before** mọi lần đọc V sau đó. ("Đọc/ghi thẳng main memory" chỉ là cách nói đơn giản hoá — xem `07-JavaMemoryModel.md`.)

### 1.3 Khi nào dùng?

Điều kiện: (1) giá trị ghi **không phụ thuộc** giá trị cũ (hoặc chỉ 1 thread ghi), (2) biến **không tham gia bất biến** cùng biến khác.

| ✓ Dùng | ✗ Không dùng |
|---|---|
| Cờ trạng thái (`shutdownRequested`) | `count++` → `AtomicInteger` / `LongAdder` |
| Một writer, nhiều reader | Check-then-act |
| Publish **immutable snapshot** (thay cả object `Config`) | Bất biến giữa nhiều biến → `synchronized` / `Lock` |
| Field `instance` trong DCL | |

### 1.4 volatile vs synchronized

| | volatile | synchronized |
|---|---|---|
| Visibility | ✓ | ✓ |
| Atomicity (thao tác ghép) | ✗ | ✓ |
| Mutual exclusion / lock | ✗ | ✓ |
| Dùng cho | Cờ, giá trị độc lập | Shared mutable state, nhiều bước |

**Hiệu năng:** đọc volatile gần như rẻ như đọc thường (x86); ghi volatile tốn memory barrier. Đắt hơn biến thường, thường rẻ hơn lock có tranh chấp.

### 1.5 Decision tree

```
Cờ / giá trị ghi độc lập        → volatile
Một biến đếm / CAS đơn giản      → AtomicInteger / LongAdder
Map / Queue dùng chung           → ConcurrentHashMap / BlockingQueue
Bất biến giữa nhiều biến         → synchronized / Lock
Tốt nhất: không chia sẻ state    → immutable / thread confinement
```

---

## 2. Interview English

### Q1. What problem does `volatile` solve?

**Key sentence:** *`volatile` makes a change by one thread visible to other threads.*

**Suggested Answer:**
Without `volatile`, the JIT can keep a value in a register or move a read out of a loop. So a thread might **never see** a change made by another thread. For example, a worker loop with `while (running)` may never stop. When the field is `volatile`, every read sees the **latest write**, and the compiler can't reorder instructions around it.

### Q2. Does `volatile` make `count++` thread-safe?

**Key sentence:** *No — volatile gives visibility, not atomicity.*

**Suggested Answer:**
No. `count++` is three steps: read, add, and write. `volatile` makes each read and write visible, but it doesn't make the three steps **one atomic action**. Two threads can still read the same value and lose an update. For a counter, I use `AtomicInteger`, `LongAdder`, or `synchronized`.

### Q3. When should you use `volatile`?

**Key sentence:** *For simple flags and values that don't depend on the old value.*

**Suggested Answer:**
I use `volatile` when the new value **doesn't depend on the old value**, like a shutdown flag, or when only one thread writes. Another good case is publishing an **immutable object**, like a new `Config`, by replacing the whole reference. I don't use it for counters or for rules that involve several variables.

### Q4. What is the difference between `volatile` and `synchronized`?

**Key sentence:** *volatile gives visibility; synchronized gives visibility plus mutual exclusion.*

**Suggested Answer:**
Both guarantee visibility. But `volatile` has **no lock** and no mutual exclusion, so it can't protect compound operations. `synchronized` lets only one thread run the critical section at a time, so it can protect several steps together. I use `volatile` for simple state like a flag, and `synchronized` when several operations must happen **atomically**.

### Q5. Is `volatile` expensive?

**Key sentence:** *Reads are cheap; writes cost a memory barrier.*

**Suggested Answer:**
On x86, a volatile read is almost as cheap as a normal read. A volatile write is more expensive because it needs a **memory barrier**. So `volatile` is slower than a normal field, but usually **cheaper than a lock** under contention.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | volatile guarantees visibility and ordering. |
| 2 | volatile does not guarantee atomicity — `count++` is still unsafe. |
| 3 | Use volatile for flags and values that don't depend on the old value. |
| 4 | Use AtomicInteger or synchronized for counters. |
| 5 | synchronized = visibility + mutual exclusion; volatile = visibility only. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| visibility | thread khác thấy được thay đổi |
| latest value | giá trị mới nhất |
| status flag | biến cờ trạng thái |
| atomicity | tính nguyên tử (không bị chia cắt) |
| compound operation | thao tác nhiều bước |
| memory barrier | rào chắn bộ nhớ (cấm reorder) |
| move a read out of a loop | đưa việc đọc ra ngoài vòng lặp |

## 5. Self-test

1. Vì sao worker `while (running)` có thể không bao giờ dừng?
2. volatile đảm bảo 3 thứ gì? Không đảm bảo gì?
3. 2 điều kiện để dùng volatile an toàn?
4. Kể 4 use case đúng và 3 use case sai.
5. volatile vs synchronized — bảng so sánh.
6. Chi phí của volatile read vs write?
7. Vì sao publish immutable `Config` qua volatile an toàn, còn 2 field volatile riêng thì không?
