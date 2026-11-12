# 03 — Callable, Future, CompletableFuture

**Source:** `CompletableFutureDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Runnable → Callable → Future → CompletableFuture

| | Trả giá trị | Checked exception | Ghi chú |
|---|:-:|:-:|---|
| `Runnable` | ✗ | ✗ | `void run()` |
| `Callable<V>` | ✓ | ✓ | `V call() throws Exception` |
| `Future<V>` | | | "phiếu nhận hàng": `get()` **chặn**; exception bọc trong `ExecutionException` |
| `CompletableFuture<V>` | | | Future + **pipeline bất đồng bộ**, callback, ghép nối |

Hạn chế của `Future`: không callback, không ghép nối, chờ nhiều Future phải `get()` lần lượt. `get()` nên **luôn có timeout**.

### 1.2 Ghép nối (Demo 3)

| Method | Tương đương | Dùng khi |
|---|---|---|
| `thenApply` | `map` (T → U) | biến đổi kết quả |
| `thenCompose` | `flatMap` (T → CF<U>) | bước tiếp theo **cũng async** → tránh `CF<CF<U>>` |
| `thenCombine` | zip | ghép 2 CF **chạy song song** |
| `thenAccept` / `thenRun` | | tiêu thụ kết quả / chạy tiếp, không trả gì |

Demo: user → orders (tuần tự, `thenCompose`) + loyalty (song song, `thenCombine`) → ~800ms thay vì ~1200ms.

### 1.3 allOf / anyOf (Demo 4)

- `allOf` trả `CF<Void>` → phải `join()` từng CF để lấy kết quả. 1 CF lỗi → `allOf` lỗi, nhưng các CF khác **vẫn chạy** (không tự cancel).
- `anyOf` hoàn thành khi CF **đầu tiên** xong (thành công **hoặc lỗi**), kiểu trả về `Object`.

### 1.4 Exception (Demo 5)

- Fire-and-forget không ai `join/get/handle` → lỗi **biến mất**, không log.
- `join()` ném `CompletionException` (unchecked); `get()` ném `ExecutionException` (checked).
- `exceptionally(ex -> fallback)`: chỉ chạy khi lỗi, trả giá trị thay thế.
- `handle((val, ex) -> ...)`: **luôn** chạy, đổi được kết quả.
- `whenComplete((val, ex) -> ...)`: quan sát (log), **không** đổi kết quả.
- Quy tắc: kết thúc pipeline bằng `handle` / `exceptionally` / `whenComplete` để **log lỗi**.

### 1.5 Timeout (Demo 6, Java 9+)

- `orTimeout(t)`: quá hạn → hoàn thành với `TimeoutException`.
- `completeOnTimeout(v, t)`: quá hạn → hoàn thành với giá trị mặc định.
- ⚠ **Không huỷ** task đang chạy. `cancel(true)` trên CF cũng **không interrupt**. → Đặt timeout ở client (HTTP/JDBC).

### 1.6 Bốn cái bẫy

| # | Bẫy | Hậu quả | Cách sửa |
|---|---|---|---|
| 1 | `supplyAsync()` không truyền executor | Chạy trên `ForkJoinPool.commonPool()` — dùng chung cả JVM, ~`N_cpu - 1` thread, daemon | Luôn truyền **executor riêng** cho I/O |
| 2 | Nuốt exception | Lỗi mất dấu | Kết thúc chuỗi bằng `whenComplete`/`handle` + log |
| 3 | Mất context | `ThreadLocal` (MDC, SecurityContext, transaction) không sang thread khác | Chụp ở thread gọi, gắn lại ở thread chạy, `remove()` trong `finally` (TaskDecorator) |
| 4 | `join()` trong task của chính pool đó | **Thread pool starvation deadlock** — treo mãi | `thenCompose` thay vì `join()` lồng; tách pool 2 tầng |

Thêm về bẫy 4: `join()` **không phản hồi interrupt** (`get()` thì có) → `shutdownNow()` không cứu được thread kẹt ở `join()`. Đường chờ nên dùng `get(timeout)` / `orTimeout`.

---

## 2. Interview English

### Q1. Runnable vs Callable? What are the limits of `Future`?

**Importance:** 🟡 Medium

**Key sentence:** *Callable returns a value and can throw; Future can only block.*

**Suggested Answer:**
`Runnable` returns nothing and can't throw checked exceptions. `Callable` **returns a value** and can throw. When I submit a `Callable`, I get a `Future`. Its problem is that `get()` **blocks** the caller, there are **no callbacks**, and I can't easily **chain** or combine several futures. That's why Java 8 added `CompletableFuture`.

### Q2. `thenApply` vs `thenCompose`?

**Importance:** 🔴 Important

**Key sentence:** *thenApply is map; thenCompose is flatMap for a function that returns another future.*

**Suggested Answer:**
`thenApply` takes a function that returns a **normal value**, like `map`. `thenCompose` takes a function that returns **another CompletableFuture**, like `flatMap`. If I used `thenApply` with an async call, I'd get a `CompletableFuture<CompletableFuture<T>>`. So for "get the user, then load the user's orders asynchronously", I use `thenCompose`.

### Q3. How do you run several calls in parallel and combine the results?

**Importance:** 🔴 Important

**Key sentence:** *thenCombine for two, allOf for many — and allOf doesn't cancel the others on failure.*

**Suggested Answer:**
For two independent calls, I start both and use `thenCombine` to merge the results. For many calls, I use `allOf`, then `join` each future to collect the results. `allOf` itself returns `Void`. Also, if one future fails, `allOf` fails, but the **other tasks keep running** — they are not cancelled automatically. `anyOf` completes with the first one that finishes, success or failure.

### Q4. Where does `supplyAsync()` run if you don't pass an executor? Why is that a problem?

**Importance:** 🔴 Important

**Key sentence:** *It runs on the shared common pool, so slow I/O there can stall the whole app.*

**Suggested Answer:**
It runs on `ForkJoinPool.commonPool()`. That pool is **shared by the whole JVM**, including parallel streams, and it only has about **cores minus one** threads. If I put slow HTTP or DB calls there, they block those few threads, and every other async task and parallel stream in the app has to wait. So for I/O, I **always pass my own executor**.

### Q5. What happens to an exception inside a `CompletableFuture`?

**Importance:** 🔴 Important

**Key sentence:** *It stays inside the future; if nobody handles it, it's lost.*

**Suggested Answer:**
The exception is stored in the future, wrapped in a `CompletionException`. If nobody calls `join`, `get`, or a handler, it's **lost — no log at all**. `join()` throws `CompletionException`, `get()` throws `ExecutionException`. I use `exceptionally` for a fallback value, `handle` to transform both success and failure, and `whenComplete` to **log** without changing the result. Every pipeline should end with one of them.

### Q6. Does `orTimeout()` stop the task?

**Importance:** 🔴 Important

**Key sentence:** *No — it only stops waiting; the task keeps running.*

**Suggested Answer:**
No. `orTimeout` completes the future with a `TimeoutException`, and `completeOnTimeout` completes it with a default value, but the **underlying task keeps running** and keeps its thread. Even `cancel(true)` on a CompletableFuture doesn't interrupt it. So the real timeout must be set where resources are used — on the **HTTP client** or the **JDBC query**.

### Q7. Why do MDC or SecurityContext disappear in async code?

**Importance:** 🔴 Important

**Key sentence:** *They live in ThreadLocals, and the task runs on a different thread.*

**Suggested Answer:**
MDC, Spring's `SecurityContext` and transactions are stored in **ThreadLocals**, which belong to the calling thread. The async task runs on a pool thread, so it **doesn't see them**. The fix is to **capture** the context in the calling thread, **set** it in the worker thread, and **clean it up** in `finally`, because pool threads are reused. In Spring I do this with a `TaskDecorator`.

### Q8. Can calling `join()` inside a pool task cause a deadlock?

**Importance:** 🔴 Important

**Key sentence:** *Yes — the outer task holds the thread the inner task needs.*

**Suggested Answer:**
Yes. If a task running on a pool submits another task to the **same pool** and then calls `join()`, it holds a thread while waiting. When all threads do this, the inner tasks sit in the queue with **no thread to run them**, and everything hangs. It's called **thread pool starvation deadlock**. I avoid it by using `thenCompose` instead of blocking, or by using **separate pools**. And `join()` doesn't respond to interrupts, so `shutdownNow()` can't save it.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | thenApply is map; thenCompose is flatMap. |
| 2 | Always pass your own executor for I/O work. |
| 3 | End every pipeline with a handler that logs errors. |
| 4 | A timeout on a future doesn't stop the task. |
| 5 | Never block on a task from the same pool. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| chain / compose | nối các bước |
| callback | hàm được gọi khi có kết quả |
| fallback value | giá trị thay thế khi lỗi |
| fire-and-forget | gửi đi, không chờ kết quả |
| common pool | ForkJoinPool dùng chung toàn JVM |
| context propagation | truyền context sang thread khác |
| starvation deadlock | treo vì task chờ task trong cùng pool |

## 5. Self-test

1. Runnable vs Callable? Hạn chế của `Future`?
2. `thenApply` / `thenCompose` / `thenCombine` khác nhau thế nào?
3. `allOf` trả gì? 1 CF lỗi thì các CF khác có bị cancel không? `anyOf` trả khi nào?
4. `supplyAsync()` không có executor chạy ở đâu? Hậu quả?
5. `join()` vs `get()` ném exception gì? `exceptionally` / `handle` / `whenComplete`?
6. `orTimeout` có dừng task không? Timeout nên đặt ở đâu?
7. Vì sao MDC mất khi async? Sửa thế nào?
8. Vì sao `join()` lồng trong cùng pool gây treo? Vì sao `shutdownNow()` không cứu được?
