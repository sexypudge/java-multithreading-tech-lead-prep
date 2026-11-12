# 03 — Thread Basics: join, daemon, exceptions, yield

**Source:** `ThreadBasicsDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Các API nhỏ hay bị hỏi

| Method | Ý nghĩa | Giữ lock? | State thread gọi |
|---|---|---|---|
| `sleep(ms)` | Ngủ ít nhất ms | **Giữ** | TIMED_WAITING |
| `join()` | Chờ thread khác kết thúc | — | WAITING |
| `join(ms)` | Như trên, có timeout | — | TIMED_WAITING |
| `yield()` | **Gợi ý** nhường CPU, scheduler có thể bỏ qua | Giữ | RUNNABLE |

### 1.2 join()

- `join()` làm thread **gọi** (vd `main`) chờ thread kia xong.
- `join(timeout)` **không báo lỗi** khi hết hạn → phải tự kiểm tra `isAlive()`.
- Bên trong: `wait()` trên chính Thread object trong vòng `while (isAlive())`; khi thread kết thúc JVM gọi `notifyAll()` → **đừng dùng Thread object làm lock**.
- `join()` trả về tạo **happens-before**: thread gọi thấy mọi thứ thread kia đã ghi.
- `t1.start(); t2.start(); t1.join(); t2.join();` → chạy **song song**. `t1.start(); t1.join(); t2.start(); ...` → chạy **lần lượt**.

### 1.3 Daemon thread

- JVM thoát khi **không còn thread non-daemon** nào chạy. Daemon bị dừng đột ngột, **không chạy `finally`**.
- Dùng cho việc phụ: GC, metrics, cache eviction. **Không** dùng để ghi file/DB.
- Phải `setDaemon(true)` **trước** `start()`, nếu không → `IllegalThreadStateException`.
- Daemon được **kế thừa** từ thread tạo ra nó.

### 1.4 Exception trong thread con

- Exception **không lan lên** thread cha; `try/catch` ở `main` không bắt được.
- Bắt bằng `setUncaughtExceptionHandler` (từng thread) hoặc `Thread.setDefaultUncaughtExceptionHandler`.
- `ExecutorService.submit()`: exception bị **giấu trong `Future`** → chỉ thấy khi `get()` (`ExecutionException`). `execute()`: đi vào uncaught handler.

### 1.5 Khác

- Priority 1..10 (mặc định 5): chỉ là **gợi ý** cho OS, không đáng tin.
- `yield()` không dùng để đồng bộ. Spin-wait hiện đại dùng `Thread.onSpinWait()`.

---

## 2. Interview English

### Q1. What does `join()` do?

**Importance:** 🟡 Medium

**Key sentence:** *`join()` makes the current thread wait until another thread finishes.*

**Suggested Answer:**
When I call `t.join()`, the current thread waits until thread `t` terminates. With `join(timeout)`, it waits at most that long, but it **doesn't throw an error** on timeout, so I must check `isAlive()`. Also, `join()` creates a **happens-before** relationship, so the caller sees all changes made by the finished thread.

### Q2. What is a daemon thread?

**Importance:** 🟡 Medium

**Key sentence:** *The JVM doesn't wait for daemon threads when it shuts down.*

**Suggested Answer:**
A daemon thread is a background thread. The JVM exits when **all non-daemon threads** have finished, and it stops daemon threads suddenly, without running `finally` blocks. So I use daemon threads only for helper work, like metrics or cache cleanup, **never for writing data**. I must call `setDaemon(true)` before `start()`.

### Q3. What happens to an exception thrown in a child thread?

**Importance:** 🟡 Medium

**Key sentence:** *It doesn't reach the parent thread — use an UncaughtExceptionHandler.*

**Suggested Answer:**
An exception in a thread **does not go to the thread that started it**, so a `try/catch` in `main` can't catch it. The thread just dies, and by default the JVM prints the stack trace. To handle it, I set an `UncaughtExceptionHandler`. With an executor, `submit()` stores the exception in the `Future`, and I only see it when I call `get()`.

### Q4. Does `sleep()` release the lock? What about `yield()`?

**Importance:** 🔴 Important

**Key sentence:** *Neither releases the lock — only `wait()` does.*

**Suggested Answer:**
No. `sleep()` pauses the thread but **keeps every lock** it holds. `yield()` is only a **hint** to the scheduler to give the CPU to another thread, and the scheduler can ignore it. Only `wait()` releases the monitor. I never use `yield()` or priorities for synchronization.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | `join()` waits for a thread to finish and creates happens-before. |
| 2 | `join(timeout)` doesn't fail on timeout — check `isAlive()`. |
| 3 | The JVM exits when all non-daemon threads finish; daemons don't run `finally`. |
| 4 | A child thread's exception doesn't reach the parent — use an UncaughtExceptionHandler. |
| 5 | `submit()` hides exceptions in the Future; `get()` shows them. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| wait for a thread to finish | chờ thread kết thúc |
| background thread | thread chạy nền |
| shut down | tắt |
| uncaught exception | exception không ai bắt |
| a hint to the scheduler | gợi ý cho bộ lập lịch |
| inherit | kế thừa |

## 5. Self-test

1. `join(500)` hết hạn thì có exception không? Làm sao biết thread còn chạy?
2. `join()` hoạt động bên trong thế nào? Vì sao không lock trên Thread object?
3. Hai cách viết `start/join` khác nhau thế nào về thời gian chạy?
4. JVM thoát khi nào? Daemon có chạy `finally` không?
5. `setDaemon()` sau `start()` thì sao?
6. Exception trong thread con đi đâu? `submit()` vs `execute()`?
7. `sleep()` và `yield()` có nhả lock không?
