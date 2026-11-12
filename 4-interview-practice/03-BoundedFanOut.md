# 03 — BoundedFanOut: gọi 20 downstream, giới hạn đồng thời, timeout, deadline, fallback

**Source:** `BoundedFanOut.java` · **Priority:** P0
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Đề bài

Trang "product detail" cần dữ liệu từ **20 downstream**:
- Tối đa **5 request đồng thời** (bulkhead / concurrency limit).
- Mỗi call timeout **300ms** (read timeout của HTTP client); lỗi/timeout → **fallback** cho phần đó.
- **Deadline tổng 1.5s**; quá hạn → phần chưa xong dùng fallback, không chờ thêm.
- **Không rò** thread / task sau khi đã trả response.

### 1.2 Lời giải A — CompletableFuture + executor có giới hạn

- Pool size = giới hạn đồng thời (5).
- Mỗi call: `supplyAsync(..., pool).exceptionally(ex -> fallback)`.
- Deadline tổng: `allOf(...).get(1500, ms)`; sau đó `getNow("fallback(deadline)")` cho từng future.
- `finally { pool.shutdownNow(); }` → interrupt call còn dở.
- ⚠ **Không dùng `orTimeout(300ms)` cho per-call**: nó tính từ lúc **submit**, gồm cả thời gian **xếp hàng** chờ thread → task cuối hàng timeout oan. Và nó không dừng call.

### 1.3 Lời giải B — Virtual threads + Semaphore

- `Semaphore(5)` = "van" tới downstream.
- `permits.tryAcquire(deadline - now)` → không lấy được permit trước deadline → fallback.
- Code chặn bình thường trong virtual thread; `release()` trong `finally`.
- Lấy kết quả: `future.get(remaining)`; `TimeoutException` → `cancel(true)` (interrupt) + fallback.
- `try-with-resources` → `close()` chờ mọi virtual thread kết thúc.

### 1.4 A vs B

| | A. CompletableFuture | B. Virtual threads |
|---|---|---|
| Ưu | Tiện ghép nối / biến đổi kết quả | Code tuần tự dễ đọc, stack trace rõ, không lo cạn thread |
| Nhược | Callback, chọn executor quan trọng | **Vẫn phải có Semaphore** — virtual thread không giới hạn gì |
| Giới hạn đồng thời bằng | Kích thước pool | Semaphore |

### 1.5 Năm điểm cần nói khi trình bày

1. Giới hạn đồng thời bảo vệ **downstream**, không làm app nhanh hơn. Thiếu nó: 1000 user × 20 call = **20.000 request** đồng thời → **cascading failure**.
2. Timeout ở **mọi tầng**: HTTP client (connect/read), từng call, deadline tổng.
3. Fallback trả **một phần dữ liệu** (degraded) tốt hơn lỗi cả trang; đánh dấu rõ phần fallback.
4. Production: **Resilience4j** (Bulkhead, TimeLimiter, CircuitBreaker, Retry) — nhưng phải giải thích được cơ chế.
5. A vs B (bảng trên).

---

## 2. Interview English

### Q1. Design a page that calls 20 downstream services with a limit of 5 concurrent calls.

**Importance:** 🔴 Important

**Key sentence:** *Bound the concurrency, time out every call, enforce an overall deadline, and fall back per part.*

**Suggested Answer:**
I start all 20 calls but **limit concurrency to 5**, either with a pool of 5 threads or a semaphore with 5 permits. Each call has a **client timeout** of 300 ms, and any error or timeout gives a **fallback** for that part only. The whole page has a **deadline** of 1.5 seconds; whatever isn't done by then uses its fallback. Finally, I **cancel** unfinished work so nothing keeps running after the response is sent.

### Q2. Why limit concurrency to the downstream at all?

**Importance:** 🔴 Important

**Key sentence:** *To protect the downstream and prevent cascading failures, not to go faster.*

**Suggested Answer:**
The limit **protects the downstream**, and through that, us. Without it, 1000 users times 20 calls means **20,000 concurrent requests** hitting another service. It slows down or falls over, our threads pile up waiting for it, and the failure **cascades** to our service and its callers. A bulkhead keeps the load within what the dependency can handle, and makes us fail fast when it's saturated.

### Q3. Why not use `orTimeout(300ms)` for the per-call timeout?

**Importance:** 🔴 Important

**Key sentence:** *It counts queueing time and it doesn't stop the call.*

**Suggested Answer:**
Two reasons. First, `orTimeout` starts counting when the future is **created**, so with 20 tasks and 5 threads, the last tasks spend time **waiting in the queue** and time out without ever being tried. Second, it only completes the future — the **real call keeps running** and keeps its thread. The correct per-call timeout is the **HTTP client's read timeout**, which actually stops the I/O.

### Q4. CompletableFuture or virtual threads for this problem?

**Importance:** 🔴 Important

**Key sentence:** *CompletableFuture for composition; virtual threads for simple blocking code — both need an explicit limit.*

**Suggested Answer:**
With `CompletableFuture`, the limit comes from the **pool size**, and it's convenient to combine and transform results. With virtual threads, I write **simple sequential code** with real stack traces and no risk of running out of threads — but virtual threads don't limit anything, so I **must add a semaphore**. On Java 21 for I/O-heavy code, I'd lean towards virtual threads for readability, and use CompletableFuture when there's a lot of composition.

### Q5. How do you avoid leaking work after the response is sent?

**Importance:** 🟡 Medium

**Key sentence:** *Cancel unfinished tasks with interrupts, and make sure the calls respond to them.*

**Suggested Answer:**
In version A, I call `shutdownNow()` on the pool in `finally`, which **interrupts** calls still running. In version B, when a future misses the deadline I call `cancel(true)`, which interrupts the virtual thread, and the try-with-resources waits for everything to finish. This only works if the calls **respond to interrupts** — that's another reason to rely on client timeouts, because some I/O ignores interrupts.

### Q6. What would you use in production instead of writing this yourself?

**Importance:** 🟡 Medium

**Key sentence:** *Resilience4j: Bulkhead, TimeLimiter, CircuitBreaker and Retry.*

**Suggested Answer:**
I'd use **Resilience4j**: a `Bulkhead` for the concurrency limit, `TimeLimiter` together with client timeouts, a **`CircuitBreaker`** to stop calling a dependency that's failing, and `Retry` with backoff for transient errors only. I'd also monitor the **fallback rate**, because fallbacks can hide an outage. But in the interview I explain the mechanism underneath, like in this exercise.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | The concurrency limit protects the downstream. |
| 2 | Time out at every layer: client, call and request. |
| 3 | `orTimeout` counts queueing time and doesn't stop the call. |
| 4 | Partial data with fallbacks beats a failed page. |
| 5 | Virtual threads still need a semaphore. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| fan-out | gọi song song nhiều nơi |
| concurrency limit / bulkhead | giới hạn đồng thời |
| deadline | hạn chót tổng |
| degraded response | response thiếu một phần |
| cascading failure | lỗi lan dây chuyền |
| circuit breaker | cầu dao ngắt khi lỗi nhiều |
| orphan work | việc mồ côi, không ai chờ |

## 5. Self-test

1. 4 yêu cầu của đề bài?
2. Lời giải A giới hạn đồng thời bằng gì? Deadline tổng bằng gì?
3. Vì sao không dùng `orTimeout` cho per-call timeout (2 lý do)?
4. Lời giải B: `tryAcquire` với thời gian còn lại để làm gì? Quá deadline thì làm gì?
5. A vs B: ưu nhược? Vì sao B vẫn cần Semaphore?
6. Làm sao không rò việc sau khi trả response?
7. Production dùng gì? Cần alert gì?
