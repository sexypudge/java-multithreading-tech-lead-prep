# 07 — Tech Lead Scenarios: luyện nói nhanh

**Source:** [`TECH_LEAD_SCENARIOS.md`](TECH_LEAD_SCENARIOS.md) (bản đầy đủ, có bảng và sample answer dài 2–3 phút)
**Goal:** file này là bản **ngắn** để luyện: mỗi scenario trả lời trong **45–60 giây**. Khi có thời gian, mở rộng theo bản đầy đủ.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Khung trả lời (dùng cho mọi scenario)

1. **Clarify** — triệu chứng gì, cái gì vừa thay đổi, metric nói gì?
2. **Hypothesize** — 2–4 nguyên nhân, khả năng cao nhất trước.
3. **Verify** — dữ liệu nào (metric, thread dump, JFR, log) xác nhận / loại bỏ?
4. **Fix** — ngắn hạn vs dài hạn, trade-off.
5. **Prevent** — monitoring, alert, test, checklist review.

### 1.2 Bảng tra nhanh

| # | Scenario | Ý chính |
|---|---|---|
| S1 | Latency cao, CPU thấp | Thread đang **chờ** — chờ cái gì? Thread dump ×3 |
| S2 | Queue của pool cứ tăng | Arrival > service rate; queue có giới hạn + rejection; tìm phía nào thay đổi |
| S3 | Gọi downstream chậm | Timeout mọi tầng, bulkhead, circuit breaker, retry + jitter, fallback, cancel |
| S4 | 200 worker vs 10 DB connection | DB pool là giới hạn thật; khớp executor với connection pool |
| S5 | Blocking / CF / virtual thread / reactive | Theo workload và team |
| S6 | CPU 100% | `top -H` → hex → `nid` trong thread dump; GC thì heap dump |
| S7 | Job chạy 3 lần khi scale | ShedLock + idempotent; scheduler 1 thread |
| S8 | `@Async` mất traceId / user null | ThreadLocal; TaskDecorator; transaction sau commit |
| S9 | Monitoring thread pool | active/max, queue, rejected, p99; tên thread; runbook |
| S10 | Phòng bug concurrency cho team | Design, document, review checklist, test, runtime |

---

## 2. Interview English

### Q1 (S1). High latency, but CPU is low. How do you investigate?

**Importance:** 🔴 Important

**Key sentence:** *Low CPU with high latency means threads are waiting — I find out what they wait for.*

**Suggested Answer:**
Low CPU means threads are **waiting, not working**. I take **three thread dumps** a few seconds apart and see where most threads are parked: in `getConnection` means the DB pool is exhausted, in socket reads means a slow downstream without a timeout, BLOCKED on one lock means contention, and waiting in `Future.get` means pool starvation. I also check executor active versus max and queue depth. The usual fix is **timeouts at every layer** and a **bulkhead** for the slow dependency.

### Q2 (S2). A thread pool's queue keeps growing. What do you do?

**Importance:** 🔴 Important

**Key sentence:** *Work arrives faster than it completes — find which side changed, and bound the queue.*

**Suggested Answer:**
A growing queue means the **arrival rate is higher than the completion rate**. So I check which side changed: a traffic spike or retry storm, or **slower tasks** because a dependency slowed down. An unbounded queue just hides the problem until OOM, so I **bound it** and pick a rejection policy — CallerRuns for internal producers, fail fast with 503 for user traffic. Then I fix the real cause. I don't just increase the pool size without knowing the bottleneck.

### Q3 (S3). Your service calls a slow downstream API. Design the interaction.

**Importance:** 🔴 Important

**Key sentence:** *Timeouts, bounded concurrency, circuit breaker, careful retries, fallback and cancellation.*

**Suggested Answer:**
I set **timeouts at every layer**: connect, read, per call and an overall deadline. I **limit concurrency** to that dependency with a bulkhead, to protect both sides. A **circuit breaker** stops calls when the error rate is high. Retries only for transient errors on idempotent operations, bounded, with **backoff and jitter**. A **fallback** returns cached or default data, marked as partial. When the caller gives up, I cancel the work. And I monitor latency, errors, breaker state and the fallback rate.

### Q4 (S4). The worker pool has 200 threads but the DB pool has 10 connections.

**Importance:** 🔴 Important

**Key sentence:** *The connection pool is the real limit; 190 threads just wait.*

**Suggested Answer:**
Only 10 threads can use the database at once, so **190 threads just wait** in `getConnection`. They add memory, context switches, tail latency and connection timeouts, but **no throughput**. I align the executor size with the connection pool, size the DB pool by what the **database** can handle, keep transactions short with no remote calls inside, and separate DB work from other I/O. With virtual threads this matters even more, because the connection pool becomes the **only limit**.

### Q5 (S5). Blocking, `CompletableFuture`, virtual threads or reactive — which one?

**Importance:** 🔴 Important

**Key sentence:** *It depends on the workload and the team; for typical I/O on Java 21, virtual threads with explicit limits.*

**Suggested Answer:**
For **CPU-bound** work, a pool near the core count — virtual threads won't help. For typical **I/O-bound** request handling on Java 21, I prefer **virtual threads**: simple blocking code, real stack traces, but with explicit limits like a semaphore in front of each downstream. I use **CompletableFuture** to compose independent calls with timeouts and fallbacks. I choose **reactive** only for streaming or end-to-end back-pressure, and only if the team already knows it, because the complexity cost is high.

### Q6 (S6). Production CPU is at 100%. What do you do?

**Importance:** 🔴 Important

**Key sentence:** *Application threads or GC? Then find the hot thread and its stack.*

**Suggested Answer:**
First I check whether it's **application threads or GC**. For application threads, I use `top -H` to find the hottest thread, convert its ID to hex, and find its `nid` in a **thread dump**. Several dumps showing the same RUNNABLE frame point to the culprit — an infinite loop, a bad regex, a **HashMap corrupted by concurrent writes**, or a busy spin. If it's GC, I look for a memory leak with a **heap dump**. I mitigate first — restart or scale — then fix and add an alert.

### Q7 (S7). A scheduled job runs three times after scaling to three instances.

**Importance:** 🟡 Medium

**Key sentence:** *Each instance runs it — use a distributed lock, and make the job idempotent anyway.*

**Suggested Answer:**
Every instance has its own scheduler, so the job runs on **all three**. I add a **distributed lock** like ShedLock with a database or Redis table, use leader election, or move the job to a dedicated worker. I still make the job **idempotent**, because locks can expire and instances can crash. I also remember that Spring Boot's scheduler has **one thread** by default, and long jobs must handle interrupts for graceful shutdown.

### Q8 (S8). Logs from `@Async` code lose the traceId, and the user is null.

**Importance:** 🔴 Important

**Key sentence:** *MDC and SecurityContext are ThreadLocals — copy them with a TaskDecorator and clean up.*

**Suggested Answer:**
MDC and `SecurityContextHolder` are **ThreadLocal-based**, and pool threads don't inherit them. I add a **`TaskDecorator`** that captures them when the task is submitted, restores them on the worker and **clears them in finally**. For Spring Security there's `DelegatingSecurityContextAsyncTaskExecutor`, and for tracing, Micrometer context propagation. Also, the **transaction** doesn't cross threads, so async work should start **after commit**.

### Q9 (S9). How do you monitor and alert on thread pools?

**Importance:** 🟡 Medium

**Key sentence:** *Active versus max, queue capacity, rejections and task latency — with named threads and a runbook.*

**Suggested Answer:**
Per pool I track **active threads, pool size, queue size and remaining capacity, completed rate, rejected count** and task duration including time waiting in the queue. I alert when active is above 90% of max for a few minutes, when remaining queue capacity drops below 20%, on **any rejection** in a user-facing pool, and when p99 exceeds the SLO. Threads have **meaningful names**, dashboards show pool saturation next to downstream latency, and there's a runbook for taking thread dumps.

### Q10 (S10). How do you prevent concurrency bugs as a team?

**Importance:** 🟡 Medium

**Key sentence:** *Simple designs, documented thread safety, a review checklist, stress tests and runtime monitoring.*

**Suggested Answer:**
In **design**, we prefer immutability, confinement and high-level utilities over hand-written locks and wait/notify. We **document** thread-safety: which lock guards which field. Code review has a **checklist**: shared mutable state, check-then-act, I/O inside locks, unbounded queues, swallowed interrupts, ThreadLocal without remove, blocking inside a pool, missing timeouts. We write **stress tests** with a start gate, use Awaitility for async, and at runtime we rely on metrics, named threads and alerts.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Clarify, hypothesize, verify, fix, prevent. |
| 2 | Low CPU and high latency means threads are waiting. |
| 3 | A growing queue means arrivals exceed completions. |
| 4 | The real limit is usually the downstream, not the thread pool. |
| 5 | Timeouts at every layer, and a bulkhead for every slow dependency. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| symptom / root cause | triệu chứng / nguyên nhân gốc |
| hypothesis | giả thuyết |
| mitigation | biện pháp giảm thiểu tạm thời |
| saturation | bão hoà, quá tải |
| SLO (service level objective) | mục tiêu chất lượng dịch vụ |
| runbook | tài liệu hướng dẫn xử lý sự cố |
| retry storm | bão retry |

## 5. Self-test

1. 5 bước của khung trả lời?
2. S1: 4 vị trí thread hay bị kẹt trong dump và ý nghĩa?
3. S2: queue tăng — 2 phía nào cần kiểm tra? Các lựa chọn và trade-off?
4. S3: kể đủ 7 điểm thiết kế gọi downstream chậm.
5. S4: vì sao 200 worker vs 10 connection không tăng throughput? Virtual thread thay đổi gì?
6. S5: chọn mô hình nào cho CPU-bound, I/O-bound, compose, streaming?
7. S6: quy trình CPU 100%?
8. S9: 4 alert cho thread pool?
9. S10: 8 mục trong checklist review code concurrency?
