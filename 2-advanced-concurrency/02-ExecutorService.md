# 02 — ExecutorService & ThreadPoolExecutor

**Source:** `ExecutorServiceDemo.java`
**Đọc thêm:** [`02b-ExecutorService-Production.md`](02b-ExecutorService-Production.md) — 10 điểm khó trong production + checklist review code.
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Vì sao cần thread pool?

- Tạo thread đắt (stack ~1MB, syscall OS) → **tái sử dụng**.
- Không giới hạn thread → hết RAM / CPU thrash. Pool = cách **giới hạn tài nguyên**.
- Tách **định nghĩa task** (`Runnable`/`Callable`) khỏi **cách chạy** (executor).

### 1.2 Bảy tham số của `ThreadPoolExecutor`

| Tham số | Ý nghĩa |
|---|---|
| `corePoolSize` | số thread thường trực |
| `maximumPoolSize` | số thread tối đa |
| `keepAliveTime` + `unit` | thread dư (core..max) rảnh bao lâu thì bị thu hồi |
| `workQueue` | hàng đợi khi core đã bận (một `BlockingQueue`) |
| `threadFactory` | **đặt tên thread** (bắt buộc để đọc thread dump) |
| `handler` | rejection policy khi pool + queue đều đầy |

### 1.3 Thứ tự nhận task: core → queue → max → reject (câu kinh điển)

```
thread < core        → tạo thread mới
core đủ, queue còn   → vào queue
queue đầy, < max     → tạo thêm thread tới max
queue đầy, = max     → REJECT
```

Demo 1 (core=2, max=4, queue=2): task 1–2 → thread mới · 3–4 → queue · 5–6 → thread mới · 7 → reject.

⚠ Queue **không giới hạn** → bước 3 không bao giờ xảy ra → `maxPoolSize` **vô nghĩa**, backlog phình tới OOM.

### 1.4 Bẫy `Executors.newXxx()` (Demo 3)

| Factory | Bên trong | Vấn đề |
|---|---|---|
| `newFixedThreadPool(n)` | `LinkedBlockingQueue` vô hạn | 10.000 task → queue 10.000, không reject, phình RAM |
| `newCachedThreadPool()` | `SynchronousQueue`, max = `Integer.MAX_VALUE` | 200 task chậm → 200 thread |

→ Production: tự tạo `ThreadPoolExecutor` với **queue có giới hạn + tên thread + rejection policy rõ ràng**.

### 1.5 Rejection policy (Demo 2)

| Policy | Hành vi | Dùng khi |
|---|---|---|
| `AbortPolicy` (mặc định) | ném `RejectedExecutionException` | request online: fail fast, trả 503/429 |
| `CallerRunsPolicy` | thread gọi tự chạy task → **back-pressure** | batch/background |
| `DiscardPolicy` | bỏ im lặng | gần như không bao giờ |
| `DiscardOldestPolicy` | bỏ task cũ nhất, thử lại | chỉ cần dữ liệu mới nhất |

### 1.6 Kích thước pool (Demo 4)

- CPU-bound: ≈ `N_cpu` (hoặc `N_cpu + 1`).
- I/O-bound: ≈ `N_cpu × (1 + W/C)` (W = chờ, C = tính). Vd 8 core, chờ 90ms, tính 10ms → 80 thread.
- Chỉ là điểm xuất phát → **đo bằng load test**. Giới hạn thật thường là **DB pool / downstream**.

### 1.7 `execute()` vs `submit()` (Demo 5)

| | `execute()` | `submit()` |
|---|---|---|
| Trả về | `void` | `Future` |
| Exception | ra `UncaughtExceptionHandler` (in stack trace), worker chết, pool tạo thread mới | **bị nuốt vào `Future`**, chỉ thấy khi gọi `get()` (`ExecutionException`) |

### 1.8 Shutdown (Demo 6)

- `shutdown()`: không nhận task mới, chạy nốt task đang chạy **và trong queue**.
- `shutdownNow()`: interrupt thread đang chạy, trả `List<Runnable>` task chưa chạy.
- Mẫu chuẩn: `shutdown()` → `awaitTermination(t)` → `shutdownNow()` → `awaitTermination` lần cuối.
- Quên shutdown → thread non-daemon giữ **JVM không thoát**. Java 19+: `ExecutorService` là `AutoCloseable`.

### 1.9 `invokeAll` / `invokeAny` (Demo 7)

- `invokeAll`: chờ **tất cả**, kết quả theo **thứ tự danh sách**; có timeout → task chưa xong bị cancel.
- `invokeAny`: trả kết quả **thành công đầu tiên**, cancel phần còn lại (hedged request).

### 1.10 `ScheduledExecutorService` (Demo 8)

- `scheduleAtFixedRate`: chu kỳ tính từ lúc **bắt đầu**. Task chậm hơn chu kỳ → lần sau chạy **ngay khi lần trước xong**, **không chạy chồng**.
- `scheduleWithFixedDelay`: chờ chu kỳ **sau khi kết thúc**.
- ⚠ Task ném exception → **lịch bị huỷ vĩnh viễn, không log**. Luôn bọc `try/catch` + log.

---

## 2. Interview English

### Q1. How does `ThreadPoolExecutor` handle a new task?

**Importance:** 🔴 Important

**Key sentence:** *Core threads first, then the queue, then extra threads up to max, then reject.*

**Suggested Answer:**
If fewer than **core** threads are running, the pool creates a new thread. Otherwise it puts the task in the **queue**. Only when the queue is **full** does it create extra threads up to **max**. If max is reached too, the task is **rejected**. So with an **unbounded queue**, the queue is never full, max is never used, and the backlog grows until we run out of memory.

### Q2. Why shouldn't you use `Executors.newFixedThreadPool` or `newCachedThreadPool` in production?

**Importance:** 🔴 Important

**Key sentence:** *They hide unbounded queues or unbounded threads.*

**Suggested Answer:**
`newFixedThreadPool` uses an **unbounded queue**, so under load tasks pile up silently until **OOM**, and latency grows without any error. `newCachedThreadPool` allows **unlimited threads**, so a traffic spike can create thousands of threads. In production, I create a `ThreadPoolExecutor` myself with a **bounded queue**, **named threads**, and a rejection policy I chose on purpose.

### Q3. Which rejection policy would you choose?

**Importance:** 🔴 Important

**Key sentence:** *Abort to fail fast online; CallerRuns for back-pressure in background jobs.*

**Suggested Answer:**
For online requests I use `AbortPolicy`: fail fast and return 503 or 429, so users don't wait forever. For background or batch work I use `CallerRunsPolicy`: the submitting thread runs the task itself, which **slows the producer down** — that's natural back-pressure. I almost never use the Discard policies, because they **lose work silently**. I also log and count rejections as a metric.

### Q4. How do you size a thread pool?

**Importance:** 🔴 Important

**Key sentence:** *Start from the formula, then measure — the real limit is usually downstream.*

**Suggested Answer:**
For CPU-bound work, about the **number of cores**. For I/O-bound work, cores times one plus wait time over compute time. But that's only a **starting point**. Often the real limit is the **DB connection pool** or the downstream service: 200 threads sharing 10 connections just wait in line. So I size it with **load tests** and watch active threads, queue depth and p99 latency.

### Q5. What's the difference between `execute()` and `submit()`?

**Importance:** 🔴 Important

**Key sentence:** *`submit()` swallows exceptions into the Future; `execute()` lets them reach the uncaught handler.*

**Suggested Answer:**
`execute()` takes a `Runnable` and returns nothing. If the task throws, the exception goes to the **UncaughtExceptionHandler**, it's printed, and the worker thread dies and is replaced. `submit()` returns a `Future`, and any exception is **stored inside the Future**. If nobody calls `get()`, the error just **disappears**. So I always check the Future, or catch and log inside the task.

### Q6. `shutdown()` vs `shutdownNow()`? How do you shut down a pool correctly?

**Importance:** 🔴 Important

**Key sentence:** *shutdown, awaitTermination, then shutdownNow.*

**Suggested Answer:**
`shutdown()` stops accepting new tasks but **finishes** running and queued ones. `shutdownNow()` **interrupts** running tasks and returns the tasks that never started. The standard pattern is: call `shutdown()`, wait with `awaitTermination` and a timeout, and if it's not done, call `shutdownNow()`. Tasks must **respect interrupts** for this to work. If we forget to shut down, non-daemon threads keep the JVM alive.

### Q7. `scheduleAtFixedRate` vs `scheduleWithFixedDelay`? What if the task throws?

**Importance:** 🔴 Important

**Key sentence:** *Fixed rate counts from the start, fixed delay from the end — and one exception cancels the schedule.*

**Suggested Answer:**
Fixed rate measures the period from the **start** of each run. If a run takes longer than the period, the next run starts **right after** — runs never overlap. Fixed delay waits the period **after** each run ends. The big trap: if the task throws an exception, the schedule is **cancelled forever** with no log. So I always wrap the body in `try/catch` and log the error.

### Q8. Does a bigger pool always make the system faster?

**Importance:** 🟡 Medium

**Key sentence:** *No — more threads than the bottleneck allows only add waiting and context switches.*

**Suggested Answer:**
No. If the bottleneck is the database with 10 connections, 200 worker threads just **queue for a connection**. They use memory, add **context switches**, and make tail latency worse. The pool must match the **downstream capacity**. I also separate pools by type of work — a **bulkhead** — so one slow dependency can't take all the threads.

### Q9. What do `invokeAll` and `invokeAny` do?

**Importance:** 🟢 Low

**Key sentence:** *invokeAll waits for all; invokeAny returns the first success and cancels the rest.*

**Suggested Answer:**
`invokeAll` runs a list of `Callable`s and waits for **all** of them. The futures come back in the **same order as the list**, and with a timeout, unfinished tasks are cancelled. `invokeAny` returns the result of the **first task that succeeds** and cancels the others. It's useful for **hedged requests**, like asking several replicas and taking the fastest answer.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Core, then queue, then max, then reject. |
| 2 | An unbounded queue makes max pool size useless. |
| 3 | Always use a bounded queue, named threads and an explicit rejection policy. |
| 4 | `submit()` hides exceptions in the Future. |
| 5 | A scheduled task stops forever after one uncaught exception. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| bounded / unbounded queue | hàng đợi có / không giới hạn |
| backlog | lượng việc tồn đọng |
| rejection policy | chính sách từ chối task |
| back-pressure | áp lực ngược, làm producer chậm lại |
| fail fast | báo lỗi ngay thay vì chờ |
| bulkhead | tách pool để cô lập lỗi |
| graceful shutdown | tắt nhẹ nhàng, chạy nốt việc |
| hedged request | gửi nhiều nơi, lấy kết quả nhanh nhất |

## 5. Self-test

1. 7 tham số của `ThreadPoolExecutor`?
2. Thứ tự core → queue → max → reject. Vì sao queue vô hạn làm max vô nghĩa?
3. `newFixedThreadPool` và `newCachedThreadPool` nguy hiểm ở đâu?
4. 4 rejection policy, chọn cái nào cho request online / batch?
5. Công thức chọn pool size CPU-bound / I/O-bound? Giới hạn thật thường ở đâu?
6. `execute()` vs `submit()` khi task ném exception?
7. Mẫu shutdown chuẩn? Quên shutdown thì sao?
8. fixedRate vs fixedDelay khi task chậm hơn chu kỳ? Exception thì sao?
9. `invokeAll` trả kết quả theo thứ tự nào? `invokeAny` làm gì với task còn lại?
