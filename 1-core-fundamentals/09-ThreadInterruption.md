# 09 — Thread Interruption

**Source:** `ThreadInterruptionDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Cơ chế hợp tác (cooperative)

Java **không có cách kill thread an toàn** (`Thread.stop()` deprecated vì nhả lock đột ngột, để object ở trạng thái không nhất quán).

- `t.interrupt()` chỉ **set cờ** interrupted = true. Không dừng thread ngay.
- Thread phải **tự kiểm tra cờ**, hoặc bị đánh thức bằng `InterruptedException` nếu đang ở method blocking (`sleep`, `wait`, `join`, `BlockingQueue.take`, `lockInterruptibly`...).

### 1.2 Hai loại thread

| Thread đang... | Cách phản ứng |
|---|---|
| **Blocking** (sleep/wait/join...) | Ném `InterruptedException` — **cờ bị CLEAR** |
| **CPU-bound** (vòng lặp tính toán) | Không có exception → phải tự check `while (!Thread.currentThread().isInterrupted())` |
| **BLOCKED** chờ `synchronized` | **Không phản ứng** với interrupt (vẫn chờ). `lockInterruptibly()` thì có |

### 1.3 Quy tắc vàng

Bắt `InterruptedException` thì **một trong hai**:
1. **Ném tiếp** (`throws InterruptedException`), hoặc
2. **Khôi phục cờ**: `Thread.currentThread().interrupt()` rồi thoát.

**Không bao giờ để `catch` rỗng.** Nuốt interrupt → cờ mất → không ai biết thread được yêu cầu dừng (Demo 4: BadWorker chạy mãi).

### 1.4 isInterrupted() vs interrupted()

| | `isInterrupted()` | `Thread.interrupted()` |
|---|---|---|
| Loại | Instance method | **Static** method |
| Clear cờ? | Không, chỉ đọc | **Có**, đọc và clear |

### 1.5 Liên quan ExecutorService

- `shutdown()`: không nhận task mới, chạy nốt task đang có.
- `shutdownNow()`: **interrupt** các worker, trả về task chưa chạy.
→ Task phải xử lý interrupt đúng thì `shutdownNow()` mới có tác dụng.

---

## 2. Interview English

### Q1. How do you stop a thread correctly?

**Importance:** 🔴 Important

**Key sentence:** *I call `interrupt()`, and the thread checks the flag and stops itself.*

**Suggested Answer:**
Java has no safe way to kill a thread. `Thread.stop()` is deprecated because it releases locks suddenly and can leave objects **broken**. Instead, I call `interrupt()`, which only **sets a flag**. The thread checks the flag in its loop, or gets an `InterruptedException` if it's sleeping or waiting, and then it cleans up and exits. It's a **cooperative** mechanism.

### Q2. Does `interrupt()` stop a thread immediately?

**Importance:** 🔴 Important

**Key sentence:** *No — it only sets a flag.*

**Suggested Answer:**
No. `interrupt()` just sets the interrupted flag. If the thread is in a blocking method like `sleep()`, `wait()`, or `take()`, it wakes up with an `InterruptedException`. If it's running a CPU loop, nothing happens unless the code **checks the flag** itself. And a thread blocked on `synchronized` **ignores** the interrupt completely.

### Q3. What should you do when you catch `InterruptedException`?

**Importance:** 🔴 Important

**Key sentence:** *Rethrow it, or restore the flag — never swallow it.*

**Suggested Answer:**
When `InterruptedException` is thrown, the flag is **cleared**. So I have two options: declare `throws InterruptedException` and let the caller handle it, or call `Thread.currentThread().interrupt()` to **restore the flag** and then exit. An empty catch block is a bug, because the stop request is lost and the thread may run forever.

### Q4. `isInterrupted()` vs `Thread.interrupted()`?

**Importance:** 🟡 Medium

**Key sentence:** *`isInterrupted()` only reads the flag; `interrupted()` reads and clears it.*

**Suggested Answer:**
`isInterrupted()` is an instance method that **only reads** the flag. `Thread.interrupted()` is a static method that checks the current thread and **clears** the flag. So if I call `interrupted()` twice, the second call returns `false`. In a loop, I normally use `isInterrupted()`.

### Q5. Can you interrupt a thread waiting for a lock?

**Importance:** 🟡 Medium

**Key sentence:** *Not with synchronized — but yes with `lockInterruptibly()`.*

**Suggested Answer:**
A thread waiting to enter a `synchronized` block is `BLOCKED`, and it **can't be interrupted**. It keeps waiting until it gets the lock. With `ReentrantLock`, I can use `lockInterruptibly()`, so the thread stops waiting and gets an `InterruptedException`. This is one reason to choose `ReentrantLock`.

### Q6. `shutdown()` vs `shutdownNow()`?

**Importance:** 🔴 Important

**Key sentence:** *`shutdown()` finishes current tasks; `shutdownNow()` interrupts them.*

**Suggested Answer:**
`shutdown()` stops accepting new tasks but lets running and queued tasks finish. `shutdownNow()` **interrupts** the running tasks and returns the tasks that never started. But `shutdownNow()` only works if the tasks **handle interruption correctly**. If a task swallows the exception, it keeps running.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | `interrupt()` only sets a flag; stopping is cooperative. |
| 2 | Blocking methods throw InterruptedException and clear the flag. |
| 3 | Rethrow InterruptedException or restore the flag — never swallow it. |
| 4 | `isInterrupted()` reads the flag; `Thread.interrupted()` reads and clears it. |
| 5 | A thread BLOCKED on synchronized can't be interrupted; use `lockInterruptibly()`. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| interrupted flag | cờ interrupt |
| cooperative mechanism | cơ chế hợp tác |
| swallow an exception | nuốt exception (catch rỗng) |
| restore the flag | khôi phục cờ |
| rethrow | ném tiếp |
| blocking method | method làm thread chờ |
| deprecated | không còn khuyến khích dùng |

## 5. Self-test

1. Vì sao `Thread.stop()` bị deprecated?
2. `interrupt()` làm gì? Có dừng thread ngay không?
3. Thread CPU-bound phản ứng với interrupt thế nào? Thread đang sleep?
4. Khi `InterruptedException` được ném, cờ còn không?
5. 2 cách xử lý đúng khi bắt `InterruptedException`?
6. `isInterrupted()` vs `Thread.interrupted()`?
7. Thread BLOCKED ở `synchronized` có bị interrupt được không? Giải pháp?
8. `shutdown()` vs `shutdownNow()`? Khi nào `shutdownNow()` vô tác dụng?
