# 03 — AsyncController: fan-out, timeout + fallback, back-pressure, pool metrics

**Source:** `src/main/java/com/example/async/AsyncController.java` (lệnh curl trong `README.md`)
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Mỗi endpoint = một câu hỏi phỏng vấn

| Endpoint | Minh hoạ | Kết quả quan sát |
|---|---|---|
| `/api/sync` | Gọi tuần tự | ~1800ms (600 + 800 + 400) |
| `/api/parallel` | Fan-out / fan-in bằng CF | ~800ms (= lời gọi chậm nhất) |
| `/api/timeout` | `completeOnTimeout` + fallback | Trả sau 1s, nhưng thread `io-*` **vẫn bận thêm 2s** |
| `/api/error` | `exceptionally` | `handled: flaky-service returned 503` |
| `/api/notify` | Fire-and-forget `void` | Trả ngay; lỗi `bob-fail` chỉ có trong **log** |
| `/api/self-invocation` | Bẫy proxy | `viaProxy` = `io-*`, `selfInvocation` = `http-nio-*` |
| `/api/saturate` | `CallerRunsPolicy` | Một phần task chạy trên thread Tomcat |
| `/api/pools` | Chỉ số pool | poolSize, active, queue, completed |

### 1.2 Trả `CompletableFuture` từ controller

```java
return CompletableFuture.allOf(user, order, payment)
        .thenApply(v -> timed(start, List.of(user.join(), order.join(), payment.join())));
```

- Spring MVC xử lý **async request**: thread Tomcat được **nhả ngay**, response gửi khi future xong.
- `join()` bên trong `thenApply` sau `allOf` là an toàn: mọi future **đã xong**, không chặn.

### 1.3 Timeout không huỷ task

- `completeOnTimeout(fallback, 1s)` → caller nhận fallback sau 1s.
- Thread `io-*` **vẫn chạy** lời gọi 3s tới hết → timeout liên tục = pool đầy dần bởi task "zombie".
- → Luôn đặt timeout ở **chính HTTP / DB client**.

### 1.4 Back-pressure với CallerRunsPolicy (`/api/saturate`)

- `backgroundExecutor`: core 2, max 4, queue 10 → sức chứa 14 task.
- 30 task × 500ms → task thứ 15+ chạy **trên thread Tomcat** → request này **chậm lại** thay vì OOM.
- Thử `setQueueCapacity(Integer.MAX_VALUE)` → không còn CallerRuns, `maxPoolSize` không bao giờ dùng, queue phình.

### 1.5 Chỉ số pool (`/api/pools`)

`poolSize`, `activeCount`, `corePoolSize`, `maxPoolSize`, `queueSize`, `queueRemainingCapacity`, `completedTaskCount` — đúng những gì cần lên dashboard / alert. Dấu hiệu quá tải: `active == max`, queue gần đầy, `completed` đứng yên.

---

## 2. Interview English

### Q1. How do you call three independent services in parallel in a Spring controller?

**Importance:** 🔴 Important

**Key sentence:** *Start all three async calls, combine them with allOf, and return the CompletableFuture.*

**Suggested Answer:**
I call three `@Async` methods that each return a `CompletableFuture`, so they **start at the same time** on the I/O pool. Then I use `allOf` and `thenApply` to combine the results. Total latency becomes the **slowest call** — about 800 ms instead of 1800 ms. I return the `CompletableFuture` directly from the controller, so Spring MVC **releases the Tomcat thread** and sends the response when the future completes.

### Q2. What happens when you return a `CompletableFuture` from a Spring MVC controller?

**Importance:** 🟡 Medium

**Key sentence:** *The request thread is released, and the response is written when the future completes.*

**Suggested Answer:**
Spring MVC switches to **async request processing**. The servlet container thread is **released** back to the pool right away, so it can serve other requests while we wait for downstream calls. When the future completes, Spring writes the response on another thread. If the future fails, the normal exception handling applies. It improves thread usage, but the downstream calls still use threads from my executor.

### Q3. You added a timeout with `completeOnTimeout`. Is the slow call cancelled?

**Importance:** 🔴 Important

**Key sentence:** *No — the user gets a fallback, but the pool thread stays busy until the real call ends.*

**Suggested Answer:**
No. The caller gets the fallback after one second, but the `io-` thread keeps running the slow call for the full three seconds — I can see it in the logs. If the downstream is slow all the time, these **zombie tasks** fill the pool. So the timeout on the future is only for the user; the **real timeout** must be on the HTTP client or the JDBC query, where the resource is actually freed.

### Q4. How does `CallerRunsPolicy` give back-pressure? What's the downside?

**Importance:** 🔴 Important

**Key sentence:** *When the pool is full, the submitting thread runs the task itself, so it slows down.*

**Suggested Answer:**
When the pool and queue are full, the task runs **on the thread that submitted it** — here, the Tomcat request thread. That thread is busy, so it can't submit more work, and the producer **naturally slows down** instead of filling memory. The downside is that the **caller's latency increases**, and in a web app it uses up request threads. So I use it for background or batch work, not for latency-sensitive user requests.

### Q5. Fire-and-forget: the endpoint returns 200 but some notifications fail. How would you know?

**Importance:** 🟡 Medium

**Key sentence:** *Only through the uncaught handler — so you need logs, metrics and alerts there.*

**Suggested Answer:**
With a `void @Async` method, the endpoint returns immediately, so the client **never sees** the failure. The only place it appears is the `AsyncUncaughtExceptionHandler`. That's why I log with context there, **count failures as a metric** and alert on them. For important work like payments or emails, I'd rather put the job on a **durable queue** with retries and a dead-letter queue, so nothing is lost if the app restarts.

### Q6. Which executor metrics do you watch, and what signals overload?

**Importance:** 🟡 Medium

**Key sentence:** *Active versus max, queue size versus capacity, completed rate and rejections.*

**Suggested Answer:**
I watch **active threads versus max pool size**, **queue size versus remaining capacity**, the **completed task rate**, and the number of **rejections**. Overload looks like active equal to max, the queue almost full, and completed tasks not increasing. In Spring Boot these come for free as `executor.*` metrics in Actuator. I put them next to downstream latency, because a slow dependency is usually the cause.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Parallel latency equals the slowest call. |
| 2 | Returning a CompletableFuture releases the request thread. |
| 3 | A timeout on a future doesn't free the pool thread. |
| 4 | CallerRuns slows the producer down instead of filling memory. |
| 5 | Watch active, queued, completed and rejected. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| fan-out / fan-in | chia ra gọi song song / gom kết quả |
| async request processing | xử lý request bất đồng bộ |
| fallback | giá trị dự phòng |
| zombie task | task vẫn chạy dù không ai chờ |
| saturate | làm đầy, quá tải |
| durable queue | hàng đợi bền vững (Kafka, SQS) |
| tail latency | độ trễ đuôi (p99) |

## 5. Self-test

1. `/api/sync` vs `/api/parallel` mất bao lâu? Vì sao?
2. Trả CF từ controller thì thread Tomcat thế nào?
3. Vì sao `join()` sau `allOf` không chặn?
4. `/api/timeout`: user nhận gì sau 1s? Thread `io-*` làm gì 2s sau đó?
5. `/api/saturate` với 30 task: task nào chạy trên thread Tomcat? Đổi queue vô hạn thì sao?
6. Lỗi trong `/api/notify` hiện ở đâu?
7. Dấu hiệu quá tải trong `/api/pools`?
