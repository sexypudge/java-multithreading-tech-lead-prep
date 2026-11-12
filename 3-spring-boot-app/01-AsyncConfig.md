# 01 — AsyncConfig: executor, bulkhead, TaskDecorator, exception handler

**Source:** `src/main/java/com/example/async/AsyncConfig.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Bảy nguyên tắc production (mỗi dòng là một câu hỏi phỏng vấn)

1. Tự khai báo executor **có tên** — không dựa vào mặc định.
2. Queue **có giới hạn** + rejection policy rõ ràng → back-pressure thay vì OOM âm thầm.
3. Tách pool theo loại việc (**bulkhead**).
4. `ThreadNamePrefix` có nghĩa → đọc thread dump / log biết ngay pool nào.
5. `TaskDecorator` copy MDC sang thread của pool và **dọn trong `finally`**.
6. `AsyncUncaughtExceptionHandler` cho method `@Async` trả `void`.
7. Shutdown êm: `setWaitForTasksToCompleteOnShutdown(true)` + `setAwaitTerminationSeconds(30)` (kết hợp `server.shutdown=graceful`).

### 1.2 Hai executor = bulkhead

| | `ioExecutor` | `backgroundExecutor` |
|---|---|---|
| Việc | Gọi downstream (HTTP/DB) | Việc nền: notification, audit |
| core / max / queue | 8 / 16 / 100 | 2 / 4 / 10 |
| Prefix | `io-` | `bg-` |
| Rejection | `AbortPolicy` → **fail fast**, caller trả lỗi / fallback | `CallerRunsPolicy` → **back-pressure** |

- Vẫn là `ThreadPoolExecutor`: **core → queue → max → reject**.
- `ioExecutor` phải khớp với khả năng downstream (DB pool 10 → hơn 10 thread gọi DB chỉ xếp hàng).
- Việc nền chậm **không được ăn hết thread** của lời gọi downstream → tách pool.

### 1.3 Pool mặc định của `@Async` (vì sao phải tự khai báo)

| Môi trường | Mặc định | Vấn đề |
|---|---|---|
| Spring thuần | `SimpleAsyncTaskExecutor` | **Thread mới mỗi task**, không giới hạn, không tái sử dụng |
| Spring Boot | `applicationTaskExecutor` (`ThreadPoolTaskExecutor`) | **Queue không giới hạn** → `max-size` gần như không dùng |
| Boot 3.2+ + `spring.threads.virtual.enabled=true` | Virtual thread | Không giới hạn thread → phải giới hạn downstream |

`getAsyncExecutor()` trả `ioExecutor()` → executor cho `@Async` **không chỉ định tên**. Gọi method `@Bean` trong class `@Configuration` trả về **đúng singleton** (class bị CGLIB proxy), không tạo pool mới.

### 1.4 MdcTaskDecorator

```java
Map<String, String> captured = MDC.getCopyOfContextMap();   // chạy trên thread GỌI (lúc submit)
return () -> {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    try {
        if (captured != null) MDC.setContextMap(captured); else MDC.clear();
        task.run();
    } finally {
        if (previous != null) MDC.setContextMap(previous); else MDC.clear();
    }
};
```

- MDC dựa trên `ThreadLocal` → **không tự đi theo** sang thread khác.
- Chụp → gắn lại → **dọn** (thread pool tái sử dụng thread, không dọn = rò sang task sau).
- Vì sao lưu `previous`? Với `CallerRunsPolicy`, task có thể chạy **ngay trên thread gọi** → phải trả lại MDC cũ của nó, không được `clear()` mất context của request.
- Cùng kỹ thuật cho `SecurityContext` (hoặc `DelegatingSecurityContextAsyncTaskExecutor`).

### 1.5 AsyncUncaughtExceptionHandler

- **Chỉ** áp dụng cho method `@Async` trả `void`. Trả `CompletableFuture` thì lỗi nằm trong future.
- Mặc định chỉ log → việc quan trọng cần **alert / retry / dead-letter**.

### 1.6 Metric

Spring Boot Actuator tự gắn `executor.active`, `executor.queued`, `executor.pool.size`, `executor.completed` cho mọi bean `ThreadPoolTaskExecutor` (tag `name` = tên bean).

---

## 2. Interview English

### Q1. What is the default executor for `@Async`, and why shouldn't you rely on it?

**Importance:** 🔴 Important

**Key sentence:** *Plain Spring creates a new thread per task; Spring Boot uses an unbounded queue — both are unsafe under load.*

**Suggested Answer:**
In plain Spring, it's `SimpleAsyncTaskExecutor`, which creates a **new thread for every task** with no limit. Spring Boot creates a `ThreadPoolTaskExecutor`, but its queue is **unbounded** by default, so max pool size is basically never used and tasks pile up until OOM. So I always declare my **own named executors** with a bounded queue, a clear rejection policy and meaningful thread names.

### Q2. How do you configure a `ThreadPoolTaskExecutor` for production?

**Importance:** 🔴 Important

**Key sentence:** *Bounded queue, named threads, explicit rejection policy, context decorator, graceful shutdown.*

**Suggested Answer:**
I set core and max size based on the **downstream capacity**, a **bounded** queue capacity, and a thread name prefix like `io-`. I choose the rejection policy on purpose: Abort for calls that should fail fast, CallerRuns for background work. I add a `TaskDecorator` to copy MDC, and I enable **wait for tasks on shutdown** with an await timeout. It's still a `ThreadPoolExecutor`, so core, then queue, then max, then reject.

### Q3. Why do you use two executors here?

**Importance:** 🔴 Important

**Key sentence:** *It's a bulkhead: slow background work can't steal the threads we need for user requests.*

**Suggested Answer:**
It's the **bulkhead** pattern. If everything shared one pool, a burst of slow background jobs — like sending notifications — could take all the threads, and calls to downstream services for user requests would wait. With separate pools, a problem in one type of work is **isolated**. Each pool also gets its own policy: the I/O pool fails fast with Abort, and the background pool uses CallerRuns to slow producers down.

### Q4. How do you keep the traceId in logs from `@Async` methods?

**Importance:** 🔴 Important

**Key sentence:** *A TaskDecorator captures MDC on submit, restores it on the worker, and cleans up in finally.*

**Suggested Answer:**
MDC is stored in a **ThreadLocal**, so the pool thread doesn't see the request's traceId. I register a `TaskDecorator` on the executor. It **captures** the MDC map on the calling thread when the task is submitted, **sets** it on the worker thread before running the task, and **restores or clears** it in `finally`, because pool threads are reused. In my version I restore the previous context, because with CallerRuns the task may run on the caller's own thread.

### Q5. Where do exceptions from `@Async void` methods go?

**Importance:** 🔴 Important

**Key sentence:** *The caller never sees them; they go to the AsyncUncaughtExceptionHandler.*

**Suggested Answer:**
A `void` method returns nothing, so the caller has **no way to see** the error. Spring sends it to the `AsyncUncaughtExceptionHandler`, which by default only logs it. I override it in `AsyncConfigurer` to log with the method name and parameters, and for important work I add **alerting, retry or a dead-letter queue**, because silent background failures are the hardest bugs to notice. For `CompletableFuture` methods, the error is inside the future instead.

### Q6. How do you shut down async executors gracefully?

**Importance:** 🟡 Medium

**Key sentence:** *Wait for running tasks with a timeout, together with graceful server shutdown.*

**Suggested Answer:**
On the executor, I set `waitForTasksToCompleteOnShutdown` to true and an **await termination** timeout, for example 30 seconds. In Boot I also set `server.shutdown=graceful`, so the server stops taking new requests and lets in-flight ones finish. In Kubernetes, `terminationGracePeriodSeconds` must be **longer** than these timeouts, or the pod gets killed first. Tasks must also respect interrupts so they can stop when the timeout runs out.

### Q7. How do you monitor these executors?

**Importance:** 🟡 Medium

**Key sentence:** *Actuator exports executor.active, queued and pool size for every ThreadPoolTaskExecutor bean.*

**Suggested Answer:**
Spring Boot Actuator automatically binds **`executor.*` metrics** for every `ThreadPoolTaskExecutor` bean, tagged with the bean name: active threads, queued tasks, pool size and completed tasks. I put them on a dashboard next to downstream latency and alert when active is close to max or the queue is almost full. I also count rejections. Named threads make the thread dump readable when there's a problem.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Never rely on the default `@Async` executor. |
| 2 | Bounded queue, named threads, explicit rejection policy. |
| 3 | Separate pools are bulkheads. |
| 4 | A TaskDecorator copies MDC and cleans up in finally. |
| 5 | `@Async void` errors only reach the AsyncUncaughtExceptionHandler. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| executor bean | bean thực thi task |
| bulkhead | tách pool để cô lập lỗi |
| task decorator | lớp bọc task trước khi chạy |
| capture / restore context | chụp / gắn lại context |
| fail fast | báo lỗi ngay |
| dead-letter queue | hàng đợi chứa việc lỗi để xử lý sau |
| graceful shutdown | tắt êm, chạy nốt việc |

## 5. Self-test

1. Pool mặc định của `@Async` trong Spring thuần / Spring Boot / Boot + virtual threads?
2. 7 nguyên tắc cấu hình executor production?
3. Vì sao `ioExecutor` dùng Abort còn `backgroundExecutor` dùng CallerRuns?
4. `getAsyncExecutor()` gọi `ioExecutor()` có tạo pool mới không? Vì sao?
5. `MdcTaskDecorator` chụp ở đâu, gắn ở đâu? Vì sao lưu `previous` thay vì `clear()`?
6. `AsyncUncaughtExceptionHandler` áp dụng cho kiểu trả về nào?
7. Shutdown êm cần cấu hình gì ở executor, server và Kubernetes?
