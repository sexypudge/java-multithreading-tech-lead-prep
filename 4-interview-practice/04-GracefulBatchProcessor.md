# 04 — GracefulBatchProcessor: back-pressure, retry, idempotency, graceful shutdown

**Source:** `GracefulBatchProcessor.java` · **Priority:** P0/P1
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Đề bài: batch gửi hoá đơn

1. Concurrency có giới hạn (4 worker) + **back-pressure** — không nạp cả triệu task vào RAM.
2. **Retry** có giới hạn, exponential backoff + **jitter** cho lỗi tạm thời.
3. **Idempotency**: bản ghi nộp trùng không xử lý 2 lần.
4. **Graceful shutdown**: ngừng nhận việc → ân hạn → interrupt → báo việc chưa xong.
5. Số liệu: processed / failed / skipped / interrupted / neverStarted.

### 1.2 Pool

```java
new ThreadPoolExecutor(4, 4, 0, SECONDS,
        new ArrayBlockingQueue<>(20),                 // queue NHỎ, có giới hạn
        r -> new Thread(r, "batch-worker-" + n),      // tên thread
        new ThreadPoolExecutor.CallerRunsPolicy());   // đầy → thread nộp việc tự chạy → tự chậm lại
```

Lần chạy 2: submitter nộp 10.000 task, sau 300ms queue chỉ có **~20** (không phải 10.000).

### 1.3 Retry + backoff + jitter

- Chỉ retry **lỗi tạm thời** (timeout, 503). Lỗi nghiệp vụ (400) retry vô ích.
- Backoff: 10, 20, 40ms… (`10 × 2^(attempt-1)`).
- **Full jitter**: sleep ngẫu nhiên trong `[0, backoff]` → tránh nhiều worker **retry đồng loạt** (thundering herd).
- Hết lượt → `failed` → production: **dead-letter queue + alert**.

### 1.4 Idempotency

- `done.add(recordId)` trên `ConcurrentHashMap.newKeySet()` là **atomic** → chỉ 1 thread "giành" được bản ghi.
- Production: trạng thái `PROCESSING/DONE` trong DB với **unique constraint**, hoặc outbox → crash giữa chừng không mất / không trùng.
- Bắt buộc khi có retry hoặc **at-least-once delivery** (Kafka/SQS).

### 1.5 Xử lý interrupt trong task

- Bị interrupt (đang shutdown) → **không retry**, `done.remove(recordId)` (lần sau làm lại), `Thread.currentThread().interrupt()`, thoát.
- Áp dụng cả khi đang `sleep` backoff.

### 1.6 Graceful shutdown (mẫu Javadoc)

```java
pool.shutdown();                                         // 1. ngừng nhận việc mới
if (pool.awaitTermination(grace, MS)) return 0;          // 2. ân hạn
List<Runnable> neverStarted = pool.shutdownNow();        // 3. interrupt + lấy lại việc chưa chạy
if (!pool.awaitTermination(2, SECONDS)) log("task không phản hồi interrupt!");   // 4. chờ lần cuối
return neverStarted.size();                              // production: ghi lại để chạy lần sau
```

⚠ **Bẫy CallerRunsPolicy sau shutdown:** handler **lặng lẽ bỏ task** (không chạy, không ném lỗi) → submitter phải tự kiểm tra `pool.isShutdown()`.

Kích hoạt: `Runtime.addShutdownHook(...)` / `@PreDestroy` / `SmartLifecycle` khi nhận **SIGTERM**; Kubernetes `terminationGracePeriodSeconds` (mặc định 30s).

---

## 2. Interview English

### Q1. Design a batch job that processes a million records safely.

**Importance:** 🔴 Important

**Key sentence:** *Bounded pool and queue for back-pressure, bounded retries with jitter, idempotent processing, graceful shutdown.*

**Suggested Answer:**
I use a fixed pool of workers with a **small bounded queue** and `CallerRunsPolicy`, so the submitter slows down instead of loading a million tasks into memory. Each record is processed **idempotently**, so duplicates or retries don't cause double work. Transient errors get a few **retries with exponential backoff and jitter**; permanent failures go to a **dead-letter queue**. On shutdown, I stop taking work, give running tasks a grace period, then interrupt and record what didn't finish.

### Q2. Walk me through graceful shutdown of an executor.

**Importance:** 🔴 Important

**Key sentence:** *shutdown, awaitTermination, shutdownNow, awaitTermination again.*

**Suggested Answer:**
First `shutdown()`, so no new tasks are accepted but queued and running ones continue. Then `awaitTermination` with a **grace period**. If it's not done, `shutdownNow()` **interrupts** running tasks and returns the tasks that never started — I save those to run later. Then I wait once more, and if it still doesn't stop, I log that some task **ignores interrupts**. I trigger this from a shutdown hook or `@PreDestroy` when the pod gets SIGTERM.

### Q3. Why do you need idempotency here?

**Importance:** 🔴 Important

**Key sentence:** *Retries, duplicates and at-least-once delivery mean the same record can arrive twice.*

**Suggested Answer:**
The same record can arrive **more than once**: we retry after a timeout even though the first attempt succeeded, a message broker delivers **at least once**, or a job restarts after a crash. Without idempotency we'd send the same invoice twice. In the demo, `add` on a concurrent set is atomic, so only one thread claims a record. In production, I use a **unique constraint** or a status column in the database, so it survives restarts.

### Q4. Why exponential backoff with jitter?

**Importance:** 🟡 Medium

**Key sentence:** *Backoff gives the dependency time to recover; jitter stops everyone retrying at the same moment.*

**Suggested Answer:**
If a dependency is overloaded and every worker retries immediately, we make the overload **worse** — a retry storm. Exponential backoff waits longer after each failure, giving it **time to recover**. But if all workers failed at the same time, they'd also retry at the same time, so I add **random jitter** to spread the retries out. And I only retry **transient** errors like timeouts or 503, never business errors like a 400.

### Q5. What does your task do when it's interrupted?

**Importance:** 🔴 Important

**Key sentence:** *Stop immediately, don't retry, undo the claim, and restore the interrupt flag.*

**Suggested Answer:**
An interrupt means **we're shutting down**, so the task must not retry. It removes the record from the "done" set so the **next run processes it again**, increments an interrupted counter, **restores the interrupt flag** with `Thread.currentThread().interrupt()`, and returns. It does this both during the work and during the backoff sleep. Swallowing the interrupt would make `shutdownNow()` useless.

### Q6. What's the trap with `CallerRunsPolicy` after shutdown?

**Importance:** 🟡 Medium

**Key sentence:** *After shutdown, it silently discards the task instead of running it.*

**Suggested Answer:**
`CallerRunsPolicy` only runs the task on the caller if the pool is **not shut down**. After shutdown, it just **drops the task silently** — no exception, no log. So a submitter that keeps submitting during shutdown would lose work without noticing. In my code the submitter checks **`isShutdown()`** and stops, and in production I'd use a custom handler that logs and counts rejections.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | A small bounded queue plus CallerRuns gives back-pressure. |
| 2 | Retry only transient errors, with backoff and jitter. |
| 3 | Idempotency is mandatory with retries or at-least-once delivery. |
| 4 | shutdown, awaitTermination, shutdownNow, awaitTermination. |
| 5 | On interrupt: don't retry, restore the flag, exit. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| back-pressure | áp lực ngược |
| exponential backoff | chờ tăng theo cấp số nhân |
| jitter | độ ngẫu nhiên thêm vào |
| retry storm / thundering herd | bão retry đồng loạt |
| idempotent | xử lý nhiều lần cùng kết quả |
| at-least-once delivery | giao ít nhất một lần |
| grace period | thời gian ân hạn |
| dead-letter queue | hàng đợi việc lỗi |

## 5. Self-test

1. 5 yêu cầu của đề bài?
2. Vì sao queue nhỏ + CallerRuns? Lần chạy 2 queue có bao nhiêu task?
3. Công thức backoff? Full jitter là gì? Retry lỗi nào?
4. Idempotency trong demo làm bằng gì? Production làm bằng gì?
5. Task bị interrupt làm 4 việc gì?
6. Viết mẫu graceful shutdown 4 bước.
7. Bẫy CallerRunsPolicy sau shutdown? Sửa thế nào?
8. Kích hoạt shutdown khi nhận SIGTERM bằng gì?
