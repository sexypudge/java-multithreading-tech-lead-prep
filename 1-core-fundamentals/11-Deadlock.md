# 11 — Deadlock

**Source:** `DeadlockDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Deadlock là gì?

Hai hoặc nhiều thread **chờ lock của nhau**, không ai tiến lên được. JVM **không tự gỡ** — thread kẹt mãi tới khi restart.

```
T1: lock(A) → chờ B        T2: lock(B) → chờ A      → vòng chờ
```

### 1.2 Bốn điều kiện Coffman (đủ cả 4 mới deadlock → phá 1 là hết)

| Điều kiện | Ý nghĩa | Cách phá |
|---|---|---|
| **Mutual exclusion** | Tài nguyên chỉ 1 thread giữ | (thường không phá được) |
| **Hold and wait** | Giữ cái này, chờ cái khác | `tryLock(timeout)`: không lấy đủ thì **nhả** cái đang giữ |
| **No preemption** | Không cưỡng chế lấy lại lock được; thread phải tự nhả | — |
| **Circular wait** | T1 chờ T2, T2 chờ T1 | **Lock ordering** (cách chuẩn, đơn giản nhất) |

### 1.3 Cách sửa

- **Lock ordering:** mọi thread lock theo cùng thứ tự (vd account có `id` nhỏ trước). Không có id duy nhất → `System.identityHashCode` + tie-breaker lock.
- **tryLock + timeout + backoff ngẫu nhiên:** không lấy đủ 2 lock → nhả, ngủ ngẫu nhiên, thử lại. Backoff **phải ngẫu nhiên**, nếu không → livelock. Caller phải xử lý trường hợp trả `false`.
- **Không gọi code lạ** (callback, listener, remote call) khi đang giữ lock.
- Giữ ít lock, phạm vi nhỏ; dùng công cụ cấp cao hơn (concurrent collections, message passing).

### 1.4 Phát hiện

- `jcmd <pid> Thread.print` / `jstack -l <pid>` → cuối dump có **"Found one Java-level deadlock"** (cho cả monitor và `ReentrantLock`).
- JConsole / VisualVM tab Threads.
- `ThreadMXBean.findDeadlockedThreads()` → dùng để alert tự động.
- **BLOCKED ≠ deadlock**: phải xem ai giữ lock nào, ai chờ lock nào, có vòng không.
- `interrupt()` **không gỡ** được thread BLOCKED ở `synchronized`.

### 1.5 Mở rộng

- **DB deadlock:** DB tự phát hiện, rollback một transaction (victim) → app phải **retry**.
- **Thread-pool starvation deadlock:** task trong pool chờ (`get`/`join`) task khác **cùng pool**, pool hết thread → không có lock nào nhưng vẫn kẹt.

---

## 2. Interview English

### Q1. What is a deadlock?

**Importance:** 🔴 Important

**Key sentence:** *Two or more threads wait for each other's locks, so none of them can continue.*

**Suggested Answer:**
A deadlock happens when two or more threads are **waiting for each other's locks**. For example, thread 1 holds lock A and waits for lock B, while thread 2 holds lock B and waits for lock A. Neither can continue. The JVM **doesn't fix this** automatically, so the threads are stuck until we restart the service.

### Q2. What are the four conditions for a deadlock?

**Importance:** 🔴 Important

**Key sentence:** *Mutual exclusion, hold and wait, no preemption, and circular wait.*

**Suggested Answer:**
These are the **Coffman conditions**. Mutual exclusion: only one thread can hold a lock. Hold and wait: a thread holds one lock while waiting for another. No preemption: a lock **can't be taken away**; the thread must release it. Circular wait: threads wait for each other in a circle. All four must be true, so if we **break one**, we prevent deadlock.

### Q3. How do you detect a deadlock in production?

**Importance:** 🔴 Important

**Key sentence:** *I take a thread dump with jstack or jcmd and look for a circular wait.*

**Suggested Answer:**
I take a thread dump with `jstack` or `jcmd Thread.print`. If there's a deadlock, the dump says **"Found one Java-level deadlock"** and shows which thread holds which lock. I can also call `ThreadMXBean.findDeadlockedThreads()` to send an alert automatically. Note that two `BLOCKED` threads **don't always mean** deadlock — I need to check lock ownership.

### Q4. How do you prevent deadlocks?

**Importance:** 🔴 Important

**Key sentence:** *I enforce a consistent lock ordering.*

**Suggested Answer:**
The simplest way is **lock ordering**: all threads take locks in the same order, for example by account ID. That breaks circular wait. Another way is `tryLock` with a timeout: if I can't get both locks, I release the one I hold, wait a **random** time, and retry. I also avoid calling unknown code while holding a lock, and keep lock scope small.

### Q5. How is a database deadlock different?

**Importance:** 🟡 Medium

**Key sentence:** *The database detects it and rolls back one transaction; the JVM doesn't.*

**Suggested Answer:**
A database **detects** deadlocks itself and rolls back one transaction, called the victim. So the application must **retry** that transaction. The JVM doesn't do this. Java threads stay stuck until we restart. Restarting is only a quick fix — the real fix is lock ordering or `tryLock`.

### Q6. Can a deadlock happen without locks?

**Importance:** 🟡 Medium

**Key sentence:** *Yes — thread-pool starvation deadlock.*

**Suggested Answer:**
Yes. In a **thread-pool starvation deadlock**, tasks in a pool wait for other tasks in the **same pool**, for example by calling `future.get()`. If all threads are busy waiting, the tasks they wait for never get a thread to run. To avoid it, I use separate pools or non-blocking composition like `CompletableFuture`.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | A deadlock means threads wait for each other's locks forever. |
| 2 | The four Coffman conditions: mutual exclusion, hold and wait, no preemption, circular wait. |
| 3 | I detect deadlocks with a thread dump from jstack or jcmd. |
| 4 | I prevent deadlocks by enforcing a consistent lock ordering. |
| 5 | BLOCKED threads don't automatically mean deadlock. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| wait for each other's locks | chờ lock của nhau |
| circular wait | chờ vòng tròn |
| no preemption | không cưỡng chế lấy lại lock |
| lock ordering | thứ tự lấy lock |
| lock ownership | thread nào đang giữ lock |
| thread dump | ảnh chụp trạng thái thread |
| back off and retry | lùi lại rồi thử lại |
| deadlock victim | transaction bị DB chọn để rollback |

**Lỗi tiếng Anh hay gặp:**

| ✗ | ✓ |
|---|---|
| Can force to release the lock | No preemption: a lock can't be taken away. |
| Waiting to get the lock from each other threads | Waiting for each other's locks |
| I will using JS stack | I would use jstack |
| I would like to prevent the deadlock by using lock ordering | I would prevent deadlocks by enforcing a consistent lock ordering |

## 5. Self-test

1. Deadlock là gì? Vẽ ví dụ 2 thread, 2 lock.
2. Kể 4 điều kiện Coffman. Phá điều kiện nào bằng cách nào?
3. Detect deadlock trong production bằng 3 công cụ nào?
4. 2 thread BLOCKED có phải deadlock không?
5. Lock ordering khi không có id duy nhất thì làm sao?
6. Vì sao backoff phải ngẫu nhiên?
7. Deadlock trong DB khác JVM thế nào?
8. Thread-pool starvation deadlock là gì?
