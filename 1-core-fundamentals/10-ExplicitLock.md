# 10 — Explicit Locks: ReentrantLock, Condition, ReadWriteLock, StampedLock

**Source:** `ExplicitLockDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.

---

## 1. Technical Knowledge

### 1.1 Bảng so sánh

| Tính năng | synchronized | ReentrantLock | ReadWriteLock | StampedLock |
|---|:-:|:-:|:-:|:-:|
| Tự động unlock | ✓ | ✗ (finally) | ✗ (finally) | ✗ (finally) |
| tryLock / timeout | ✗ | ✓ | ✓ | ✓ |
| Interruptible | ✗ | ✓ | ✓ | ✓ |
| Fair mode | ✗ | ✓ | ✓ | ✗ |
| Nhiều Condition | ✗ | ✓ | (write lock) | ✗ |
| Nhiều reader cùng lúc | ✗ | ✗ | ✓ | ✓ |
| Optimistic read | ✗ | ✗ | ✗ | ✓ |
| Reentrant | ✓ | ✓ | ✓ | **✗** |

### 1.2 ReentrantLock

```java
lock.lock();          // ngay TRƯỚC try
try {
    // critical section
} finally {
    lock.unlock();    // BẮT BUỘC trong finally
}
```

- Quên `finally` + exception → lock bị giữ mãi, mọi thread khác treo.
- `tryLock()`: thử ngay, không chờ. `tryLock(200, ms)`: chờ tối đa 200ms.
- `getHoldCount()`: số lần thread hiện tại đang giữ (reentrant).
- **Fair**: thread chờ lâu nhất được ưu tiên, tránh starvation, throughput thấp hơn. **Unfair** (mặc định): cho "chen ngang" (barging), nhanh hơn.

### 1.3 Condition

- wait/notify chỉ có **1 hàng chờ** → phải `notifyAll()` đánh thức cả producer lẫn consumer.
- Một Lock có **nhiều Condition** (`notFull`, `notEmpty`) → `signal()` đúng nhóm.
- Vẫn dùng `while` khi `await()`, giống `wait()`.

### 1.4 ReadWriteLock

- Nhiều reader **cùng lúc**, writer **độc quyền**. Demo 3: 3 reader × 300ms ≈ 300ms (synchronized ≈ 900ms).
- Dùng khi: **đọc nhiều, ghi ít**, critical section đọc đủ lâu.
- Nhược: writer có thể bị đói; overhead cao hơn khi section ngắn; **không nâng cấp read → write** (sẽ deadlock), chỉ hạ cấp write → read.

### 1.5 StampedLock

- **Optimistic read:** không lock, lấy "tem" → đọc → `validate(stamp)`. Có writer chen vào → fallback đọc bằng read lock thật.
- Rất nhanh khi ít ghi. Nhưng **không reentrant** (lock 2 lần = tự deadlock), không có Condition, dễ dùng sai.

### 1.6 Virtual thread (Java 21)

Code blocking trong `synchronized` có thể **pin** virtual thread vào carrier thread (Java 21–23) → dùng `ReentrantLock`. Từ Java 24 `synchronized` đã bớt pin.

---

## 2. Interview English

### Q1. When would you use ReentrantLock instead of synchronized?

**Key sentence:** *When I need tryLock, timeouts, interruptible locking, fairness, or several conditions.*

**Suggested Answer:**
I use `ReentrantLock` when I need features that `synchronized` doesn't have: `tryLock` with a timeout, `lockInterruptibly`, a **fair** lock, or **several `Condition`s**. Otherwise, I prefer `synchronized`, because it's simpler and **can't forget to unlock**.

### Q2. Why must `unlock()` be in a `finally` block?

**Key sentence:** *Otherwise an exception leaves the lock held forever.*

**Suggested Answer:**
Unlike `synchronized`, a `Lock` is not released automatically. If the code between `lock()` and `unlock()` throws an exception and there's no `finally`, the lock is **never released**. Then every other thread that needs it **hangs**. So I always call `lock()` right before `try` and `unlock()` in `finally`.

### Q3. Fair vs unfair lock?

**Key sentence:** *Fair locks prevent starvation; unfair locks give better throughput.*

**Suggested Answer:**
A fair lock gives the lock to the thread that has **waited the longest**, so no thread starves. An unfair lock, which is the default, lets a new thread **jump the queue** if the lock is free. Unfair is faster, but some threads may wait a long time. I use fair only when starvation is a real problem.

### Q4. How is `Condition` different from wait/notify?

**Key sentence:** *One lock can have several conditions, so I can wake the right group of threads.*

**Suggested Answer:**
With wait/notify, there is only **one wait set** per object, so I often need `notifyAll()` and wake everyone. With a `Lock`, I can create several `Condition`s, like `notFull` for producers and `notEmpty` for consumers. Then I `signal()` only the threads that need to wake up. I still use a `while` loop around `await()`.

### Q5. When would you use ReadWriteLock?

**Key sentence:** *When reads are frequent, writes are rare, and reads take some time.*

**Suggested Answer:**
`ReadWriteLock` lets **many readers** hold the lock at the same time, while a writer gets **exclusive** access. It's useful for a cache with many reads and few writes. The downsides: writers can starve, it's slower than `synchronized` for very short sections, and you **can't upgrade** a read lock to a write lock — that causes a deadlock.

### Q6. What is special about StampedLock?

**Key sentence:** *It supports optimistic reads without locking.*

**Suggested Answer:**
`StampedLock` has an **optimistic read** mode. I get a stamp, read the data without locking, and then call `validate()`. If a writer changed the data, I read again with a real read lock. It's very fast when writes are rare. But it's **not reentrant**, has no conditions, and is easy to misuse, so I use it only when performance really matters.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Use ReentrantLock for tryLock, timeout, interruptible, fair, or multiple conditions. |
| 2 | Always call `unlock()` in `finally`. |
| 3 | Condition lets me signal the right group of threads. |
| 4 | ReadWriteLock: many readers or one writer; no read-to-write upgrade. |
| 5 | StampedLock gives optimistic reads but is not reentrant. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| explicit lock | lock tường minh (gọi lock/unlock) |
| try to acquire with a timeout | thử lấy lock có giới hạn thời gian |
| fair / unfair | công bằng / không công bằng |
| jump the queue / barging | chen ngang |
| exclusive access | truy cập độc quyền |
| upgrade / downgrade a lock | nâng / hạ cấp lock |
| optimistic read | đọc lạc quan (không lock) |
| pinning | virtual thread bị dính vào carrier thread |

## 5. Self-test

1. Kể 5 tính năng ReentrantLock có mà synchronized không có.
2. Viết pattern lock/try/finally. Vì sao `lock()` đặt trước `try`?
3. `tryLock()` vs `tryLock(timeout)`?
4. Fair vs unfair: ưu nhược điểm?
5. Condition khác wait/notify thế nào? Vẫn cần `while` không?
6. Khi nào dùng ReadWriteLock? 3 nhược điểm?
7. StampedLock optimistic read hoạt động thế nào? Nhược điểm?
8. Vì sao với virtual thread (Java 21) nên dùng ReentrantLock?
